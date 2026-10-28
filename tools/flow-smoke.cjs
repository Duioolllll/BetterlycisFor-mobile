const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'..'),out=path.join(root,'build','ui-qa');
fs.mkdirSync(out,{recursive:true});
const rows=Array.from({length:25},(_,i)=>({start:i*4000,end:(i+1)*4000,text:'흐르는 가사 '+i,realTiming:true,words:[{start:i*4000,end:i*4000+1600,text:'흐르는 '},{start:i*4000+1600,end:(i+1)*4000,text:'가사 '+i}]}));
function initial(lines=rows){return {track:{key:'flow-qa',title:'흐름 확인',artist:'테스트',position:24500,duration:120000,positionKnown:true,playing:false,speed:1,packageName:'qa',actions:822},lyrics:{provider:'Local',lines},settings:{fontSize:31,offset:0,wordMode:'auto',backgroundMode:'static',appearance:{controls:'always'}},permissions:{notifications:true,connected:true}};}
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.BETTERLYRICS_CHROME||undefined,args:['--no-sandbox','--enable-unsafe-swiftshader']});
 const result={checks:[],errors:[]};
 try{
  for(const viewport of [{width:412,height:915},{width:1280,height:800}]){
   const page=await browser.newPage({viewport});page.on('pageerror',e=>result.errors.push(e.message));
   await page.addInitScript(s=>{window.commands=[];window.Android={getBootstrap:()=>JSON.stringify(s),command:(action,json)=>commands.push({action,data:JSON.parse(json)})};},initial());
   await page.goto(process.env.BETTERLYRICS_PREVIEW_URL||'file://'+path.join(root,'app/src/main/assets/web/index.html'));await page.waitForTimeout(180);
   const scroll=()=>page.locator('#lyricsViewport').evaluate(e=>e.scrollTop);
   const tick=async(position,playing=false,speed=1)=>{await page.evaluate(track=>App.receive({tick:true,track}),{position,playing,speed});await page.waitForTimeout(160);};
   const active=async expected=>{if(expected!==undefined)await page.waitForFunction(index=>document.querySelector('.lyric-line.current')?.dataset.index===index,expected,{timeout:1500});return page.locator('.lyric-line.current').first().getAttribute('data-index');};
   const input=async(value,commit=false)=>{await page.locator('#seek').evaluate((e,{value,commit})=>{e.value=value;e.dispatchEvent(new Event('input',{bubbles:true}));if(commit)e.dispatchEvent(new Event('change',{bubbles:true}));},{value,commit});await page.waitForTimeout(160);};
   let before=await scroll();await tick(26500);assert.equal(await active('6'),'6');assert(await scroll()>before+20,'same verse follows its elapsed fraction');
   await tick(24500,true);before=await scroll();await page.waitForTimeout(420);const moving=await scroll();assert(moving>before+4,'playing moves without a new native tick');
   await tick(await page.evaluate(()=>App.position()),false);const stopped=await scroll();await page.waitForTimeout(350);assert(Math.abs(await scroll()-stopped)<=1,'paused lyrics stop');
   await tick(64000);const later=await scroll();await tick(8000);assert.equal(await active('2'),'2');assert(await scroll()<later-300,'external backward seek follows immediately');
   result.checks.push(viewport.width+': continuous progress, pause and external rewind');
   const commandCount=await page.evaluate(()=>commands.length);
   await input(420);assert.equal(await active('12'),'12');assert.equal(await page.locator('#positionLabel').textContent(),'0:50');
   assert.equal(Math.round(await page.evaluate(()=>App.position())),8000,'preview leaves playback clock unchanged');
   assert.equal(await page.evaluate(()=>commands.length),commandCount,'preview sends no native seeks');
   const fill=await page.locator('.lyric-line.current .lyric-word').last().evaluate(e=>parseFloat(e.style.getPropertyValue('--progress')));assert(fill>30&&fill<37,'real word timing follows scrub preview');
   await tick(8000);assert.equal(await active('12'),'12','native tick does not overwrite preview');
   await page.locator('#seek').dispatchEvent('pointercancel');await page.waitForTimeout(110);assert.equal(await active('2'),'2','cancel returns to actual playback');
   await tick(8000,true);await input(400,true);assert.equal(await active('12'),'12');
   assert(await page.evaluate(()=>commands.some(c=>c.action==='transport'&&c.data.action==='seek'&&c.data.position===48000)));
   await tick(8000);assert.equal(await active('12'),'12','stale player sample cannot undo local seek');
   const pendingPause=await page.evaluate(()=>App.position());await page.waitForTimeout(180);assert.equal(await page.evaluate(()=>App.position()),pendingPause,'pause is honoured while waiting for a seek acknowledgement');
   await tick(48000);await tick(8000);assert.equal(await active('2'),'2','acknowledgement releases stale-sample guard');
   await input(500,true);await tick(8000);assert.equal(await active('15'),'15');await page.waitForTimeout(1450);await tick(8000);assert.equal(await active('2'),'2','rejected native seek recovers to real playback');
   result.checks.push(viewport.width+': scrub preview, word fill, cancel, seek acknowledgement and rejection');
   await tick(28000);await page.evaluate(()=>App.receive({settings:{offset:750}}));await page.waitForTimeout(110);
   await page.locator('.lyric-line.current').click();assert(await page.evaluate(()=>commands.some(c=>c.action==='transport'&&c.data.action==='seek'&&c.data.position===27250)),'lyric tap includes per-song timing offset');
   await tick(27250);await page.locator('#lyricsViewport').dispatchEvent('wheel',{deltaY:100});
   await page.locator('#lyricsViewport').evaluate(e=>e.scrollTop+=100);const manual=await scroll();await tick(40000);assert(Math.abs(await scroll()-manual)<=1,'manual browsing remains in place');
   await page.locator('#lyricsViewport').dispatchEvent('pointerdown',{clientX:120,clientY:200});
   await page.locator('#lyricsViewport').dispatchEvent('pointermove',{clientX:120,clientY:230});
   await page.evaluate(()=>window.dispatchEvent(new PointerEvent('pointerup',{clientX:120,clientY:850})));
   await page.locator('#followButton').click();await page.waitForTimeout(550);assert.equal(await page.locator('#followButton').isVisible(),false);assert(Math.abs(await scroll()-manual)>30,'follow returns to current progress');
   await input(700);await page.evaluate(s=>App.receive({...s,track:{...s.track,key:'new-song',position:0}}),initial());await page.waitForTimeout(110);assert.equal(await active('0'),'0','song change cancels scrub preview');
   result.checks.push(viewport.width+': offset-aware line tap, manual reading, follow and song change');
   const gapRows=[0,4000,8000,12000,16000,40000,44000].map((start,i)=>({start,end:start+2000,text:'간주 확인 '+i,words:[]}));
   await page.evaluate(s=>App.receive(s),initial(gapRows));await tick(20000);const gap=await scroll();await tick(30000);assert(Math.abs(await scroll()-gap)<=1,'instrumental does not scroll early');await tick(39800);assert(await scroll()>gap+20,'next entrance approaches at end of instrumental');
   const untimed=rows.map(r=>({...r,start:-1,end:-1,realTiming:false,words:[]}));
   await page.evaluate(s=>App.receive(s),initial(untimed));await page.waitForTimeout(100);await page.locator('#lyricsViewport').evaluate(e=>e.scrollTop=240);await tick(30000,true);await page.waitForTimeout(200);assert.equal(await scroll(),240,'untimed text does not gain invented timestamps');assert.equal(await page.locator('.lyric-line.current').count(),0);
   const wrapped=[{start:0,end:20000,text:'긴 가사도 한 줄씩 읽을 수 있게 천천히 흐르는 화면입니다. '.repeat(18),realTiming:false,words:[]}];
   await page.evaluate(s=>App.receive({...s,settings:{...s.settings,fontSize:46}}),initial(wrapped));await tick(1000);const head=await scroll();await tick(19000);assert(await scroll()>head+100,'long final verse scrolls through its wrapped rows');
   result.checks.push(viewport.width+': instrumental hold, untimed text and wrapped final verse');
   await page.close();
  }
  const page=await browser.newPage({viewport:{width:412,height:915},reducedMotion:'reduce'});page.on('pageerror',e=>result.errors.push(e.message));
  await page.addInitScript(s=>{window.Android={getBootstrap:()=>JSON.stringify(s),command:()=>{}};},initial());await page.goto(process.env.BETTERLYRICS_PREVIEW_URL||'file://'+path.join(root,'app/src/main/assets/web/index.html'));await page.waitForTimeout(100);
  const start=await page.locator('#lyricsViewport').evaluate(e=>e.scrollTop);await page.evaluate(()=>App.receive({tick:true,track:{position:26500,playing:false}}));await page.waitForTimeout(110);assert.equal(await page.locator('#lyricsViewport').evaluate(e=>e.scrollTop),start,'reduced motion disables continuous scroll');
  await page.evaluate(()=>App.receive({tick:true,track:{position:28000,playing:false}}));await page.waitForTimeout(110);assert(await page.locator('#lyricsViewport').evaluate(e=>e.scrollTop)>start+20,'reduced motion still follows new verse');
  result.checks.push('reduced motion: hold inside verse, follow at verse boundary');await page.close();
  assert.equal(result.errors.length,0);result.passed=true;fs.writeFileSync(path.join(out,'flow-results.json'),JSON.stringify(result,null,2));process.stdout.write(JSON.stringify(result,null,2)+'\n');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
