const {chromium}=require('playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),out=path.join(root,'build/ui-qa');
const url=process.env.BETTERLYRICS_PREVIEW_URL||'file://'+path.join(root,'app/src/main/assets/web/index.html');
const track={key:'controls-qa',title:'밤의 여백',artist:'테스트 가수',album:'테스트 앨범',duration:120000,position:24000,positionKnown:true,playing:false,speed:1,packageName:'qa',appName:'음악 앱',actions:822};
const rows=Array.from({length:8},(_,i)=>({start:i*4000,end:(i+1)*4000,text:'함께 걷는 이 밤의 노래 '+i,words:[]}));
const base={track,lyrics:{provider:'Local',lines:rows},settings:{backgroundMode:'static',appearance:{controls:'always'}},permissions:{notifications:true,connected:true}};
(async()=>{
 fs.mkdirSync(out,{recursive:true});const result={passed:false,checks:[],errors:[]};
 const browser=await chromium.launch({headless:true,executablePath:process.env.BETTERLYRICS_CHROME||undefined,args:['--no-sandbox','--enable-unsafe-swiftshader']});
 try{
  for(const viewport of [{width:320,height:640},{width:360,height:740},{width:412,height:915},{width:812,height:375},{width:1280,height:800}]){
   const page=await browser.newPage({viewport});page.on('pageerror',e=>result.errors.push(e.message));
   await page.addInitScript(s=>{window.Android={getBootstrap:()=>JSON.stringify(s),command:()=>{}};},base);
   await page.goto(url);await page.waitForTimeout(200);
   const data=await page.evaluate(()=>{const panel=document.querySelector('.player'),seek=document.querySelector('#seek'),p=panel.getBoundingClientRect(),s=seek.getBoundingClientRect(),style=getComputedStyle(panel);return {width:p.width,height:p.height,bottom:p.bottom,seekWidth:s.width,viewport:innerWidth,screenHeight:innerHeight,backdrop:style.backdropFilter||style.webkitBackdropFilter,overflow:document.documentElement.scrollWidth};});
   assert(data.bottom<=viewport.height+1&&data.overflow<=viewport.width+1,'player stays within the viewport');
   assert(data.height<=(viewport.width<=439?143:100),'player is compact');
   assert(data.seekWidth>data.width-36,'progress rail spans the player');
   if(viewport.width<=439)assert(data.width>=viewport.width-26,'mobile player uses the available width');
   assert(data.backdrop&&data.backdrop!=='none','frosted glass is rendered where supported');
   for(const id of ['previousButton','playButton','nextButton','playlistButton']){const box=await page.locator('#'+id).boundingBox();assert(box&&box.width>=43.5&&box.height>=43.5,id+' remains touchable');}
   if(viewport.width===412)await page.screenshot({path:path.join(out,'glass-player.png')});
   await page.close();
  }
  result.checks.push('wide, compact frosted player and long progress rail fit five viewport sizes');
  const context=await browser.newContext({viewport:{width:412,height:915},hasTouch:true,isMobile:true});const page=await context.newPage();page.on('pageerror',e=>result.errors.push(e.message));
  const empty={...base,track:{...track,key:'',title:'',packageName:'',playing:false},lyrics:{lines:[]},settings:{backgroundMode:'static',appearance:{controls:'auto'}}};
  await page.addInitScript(s=>{window.commands=[];window.Android={getBootstrap:()=>JSON.stringify(s),command:(action,json)=>commands.push({action,data:JSON.parse(json)})};},empty);
  await page.goto(url);
  const hidden=()=>page.waitForFunction(()=>document.getElementById('app').classList.contains('controls-hidden'),null,{timeout:6500});
  const shown=()=>page.waitForFunction(()=>!document.getElementById('app').classList.contains('controls-hidden'),null,{timeout:2000});
  await hidden();await page.waitForTimeout(500);
  assert(await page.locator('#playerControls').evaluate(e=>e.inert));assert.equal(await page.locator('#controlsToggle').getAttribute('aria-expanded'),'false');
  assert(await page.locator('#controlsToggle').isVisible(),'a handle remains available after the player hides');
  const hiddenBox=await page.locator('#playerControls').evaluate(e=>e.getBoundingClientRect().top);assert(hiddenBox>=915,'player slides below the screen');
  await page.touchscreen.tap(10,10);await shown();assert.equal(await page.locator('#playerControls').getAttribute('aria-hidden'),'false');
  await hidden();await page.locator('#controlsToggle').click();await shown();
  result.checks.push('auto-hide works while paused and without lyrics; tapping or the handle reveals it');
  await page.evaluate(({track,rows})=>App.receive({track,lyrics:{provider:'Local',lines:rows},settings:{appearance:{controls:'auto'}}}),{track,rows});
  await page.locator('#lyricsButton').click();assert(await page.locator('#app').evaluate(e=>e.classList.contains('artwork-mode')));
  await hidden();await page.locator('#controlsToggle').click();await shown();
  result.checks.push('album view also hides while paused');
  const seek=await page.locator('#seek').boundingBox();await page.mouse.move(seek.x+seek.width/2,seek.y+seek.height/2);await page.mouse.down();await page.waitForTimeout(3900);
  assert.equal(await page.locator('#app').evaluate(e=>e.classList.contains('controls-hidden')),false,'active scrubbing keeps controls visible');
  await page.mouse.up();await hidden();await page.keyboard.press('Tab');await shown();await page.waitForTimeout(3900);
  assert.equal(await page.locator('#playerControls').evaluate(e=>e.inert),false,'keyboard navigation keeps controls accessible');
  result.checks.push('scrubbing and keyboard navigation keep the controls accessible');
  await page.evaluate(()=>App.receive({settings:{appearance:{controls:'always'}}}));await shown();await page.waitForTimeout(3900);
  assert.equal(await page.locator('#app').evaluate(e=>e.classList.contains('controls-hidden')),false,'explicit always-visible preference is respected');
  assert.equal(await page.evaluate(()=>Appearance.sanitize({preset:'album',controls:'always'}).controls),'auto');
  assert.equal(await page.evaluate(()=>Appearance.sanitize({preset:'custom',controls:'always'}).controls),'always');
  result.checks.push('explicit always-visible choice is preserved while old album presets migrate to auto');
  await page.emulateMedia({reducedMotion:'reduce'});await page.evaluate(()=>App.receive({settings:{appearance:{controls:'auto'}}}));await page.touchscreen.tap(10,10);await hidden();
  const motion=await page.locator('#playerControls').evaluate(e=>({duration:getComputedStyle(e).transitionDuration,filter:getComputedStyle(e).filter}));
  assert(motion.duration.split(',').every(v=>parseFloat(v)===0)&&motion.filter==='none','reduced motion disables slide and blur');
  result.checks.push('reduced motion removes the slide and motion blur');
  await context.close();assert.deepEqual(result.errors,[]);result.passed=true;
 }finally{await browser.close();fs.writeFileSync(path.join(out,'controls-results.json'),JSON.stringify(result,null,2)+'\n');}
 console.log(JSON.stringify(result,null,2));
})().catch(error=>{console.error(error);process.exit(1)});
