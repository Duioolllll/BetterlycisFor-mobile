const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const fs=require('node:fs');
const root=path.resolve(__dirname,'..');
const out=path.join(root,'build','ui-qa');fs.mkdirSync(out,{recursive:true});
const track={key:'qa',title:'동기화 확인',artist:'테스트 아티스트',album:'QA',duration:120000,position:2200,playing:false,speed:1,positionKnown:true,packageName:'qa.player',appName:'테스트 플레이어',actions:822};
function state(lines){return {volume:{available:true,level:6,max:15},track:{...track},lyrics:{revision:1,status:'가사 연결됨',loading:false,error:'',provider:'Local',score:100,lines},settings:{offset:0,fontSize:31,wordMode:'auto',provider:'auto',searchMode:'sequential',keepScreen:true,overlay:false},permissions:{notifications:true,overlay:true,connected:true},sources:[{packageName:'qa.player',name:'테스트 플레이어',playing:false}]};}
const lines=[{start:0,end:6000,text:'첫 번째 목소리',agent:'v1',bg:false,realTiming:true,words:[{start:0,end:3000,text:'첫 번째 '},{start:3000,end:6000,text:'목소리'}]},{start:1500,end:4500,text:'겹쳐 부르는 코러스',agent:'v2',bg:true,realTiming:false,translation:'같은 시간에 함께 표시',words:[]}];
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.BETTERLYRICS_CHROME||undefined,args:['--no-sandbox','--enable-unsafe-swiftshader']});
 const result={viewports:[],checks:[],errors:[]};
 try{
  for(const viewport of [{width:360,height:740},{width:412,height:915},{width:812,height:375},{width:1280,height:800},{width:800,height:1280}]){
   const page=await browser.newPage({viewport,deviceScaleFactor:1});page.on('pageerror',e=>result.errors.push(e.message));
   await page.addInitScript(initial=>{window.commands=[];window.Android={getBootstrap:()=>JSON.stringify(initial),command:(action,json)=>window.commands.push({action,data:JSON.parse(json)})};},state(lines));
   await page.goto('file://'+path.join(root,'app/src/main/assets/web/index.html'));await page.waitForTimeout(220);
   assert.equal(await page.locator('#demoBanner').isVisible(),false);
   assert.equal(await page.locator('#trackTitle').textContent(),track.title);
   assert.doesNotMatch(await page.locator('main').innerText(),/BetterLyrics|MOBILE \/ 01/,'header branding removed');
   assert.equal(await page.locator('.lyric-line.current').count(),2);
   assert.equal(await page.locator('.lyric-line.bg').count(),1);
   const geometry=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,bottom:document.querySelector('.transport').getBoundingClientRect().bottom,metadataBottom:document.querySelector('.track-card').getBoundingClientRect().bottom,playerTop:document.querySelector('.player').getBoundingClientRect().top,webgl:document.querySelector('.ambient').classList.contains('webgl')}));
   assert(geometry.scroll<=geometry.width+1,'horizontal page overflow');assert(geometry.bottom<=viewport.height+1,'playback controls off-screen');assert(geometry.webgl,'shader must compile');
   assert(geometry.metadataBottom<geometry.playerTop,'track metadata overlaps playback controls');
   await page.locator('#settingsButton').click();assert(await page.locator('#settingsSheet').isVisible());
   assert.equal(await page.evaluate(()=>App.handleBack()),true);assert.equal(await page.locator('#settingsSheet').isVisible(),false);
   const sample=['잠깐 멈춰 이 순간을 들어','멀리 번지는 도시의 불빛','우리의 밤은 천천히 흘러','지금 이 노래에 머물러','바람 끝에 남겨둔 목소리','조금 더 가까이 들려와','말하지 않아도 알 수 있어','함께 걷는 이 밤의 멜로디'].map((text,i)=>({start:i*4000,end:(i+1)*4000,text,realTiming:false,words:[]}));
   await page.evaluate(initial=>{
    const c=document.createElement('canvas');c.width=c.height=240;const x=c.getContext('2d');const g=x.createLinearGradient(0,0,240,240);g.addColorStop(0,'#d3ad99');g.addColorStop(.48,'#695381');g.addColorStop(1,'#254954');x.fillStyle=g;x.fillRect(0,0,240,240);x.fillStyle='#ffffff25';for(let i=0;i<5;i++){x.beginPath();x.arc(60,200,32+i*25,0,Math.PI*2);x.strokeStyle='#ffffff40';x.stroke();}
    App.receive({...initial,track:{...initial.track,title:'밤의 여백',artist:'화면 미리보기',album:'밤의 기록',position:14000,cover:c.toDataURL()}});
   },state(sample));await page.waitForTimeout(650);
   await page.screenshot({path:path.join(out,`screen-${viewport.width}x${viewport.height}.png`)});
   const layout=await page.evaluate(()=>{const box=s=>{const r=document.querySelector(s).getBoundingClientRect();return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,width:r.width,height:r.height};};return {track:box('.track-card'),player:box('.player'),lyrics:box('.lyrics-shell')};});
   assert(layout.lyrics.height>=150,'usable lyric viewport');
   if(viewport.width>=700&&viewport.width>viewport.height)assert(layout.lyrics.left>=layout.track.right,'wide layout separates player and lyrics');
   result.viewports.push({...viewport,...geometry,layout});
   if(viewport.width===412){
    for(const fontSize of [22,46]){
     await page.evaluate(fontSize=>App.receive({settings:{fontSize}}),fontSize);await page.waitForTimeout(180);
     const content=await page.evaluate(()=>{const v=document.getElementById('lyricsViewport'),r=v.getBoundingClientRect(),l=document.querySelector('.lyric-line.current').getBoundingClientRect();return {overflow:v.scrollWidth-v.clientWidth,top:l.top,bottom:l.bottom,viewTop:r.top,viewBottom:r.bottom};});
     assert(content.overflow<=1&&content.top>=content.viewTop&&content.bottom<=content.viewBottom,'large Korean text stays within lyric viewport');
    }
    await page.evaluate(()=>App.receive({settings:{fontSize:31}}));await page.waitForTimeout(180);
    await page.locator('.lyric-line').nth(3).click();assert(await page.evaluate(()=>commands.some(x=>x.action==='transport'&&x.data.action==='seek'&&x.data.position===12000)),'tap lyric still seeks');
    await page.locator('#lyricsViewport').dispatchEvent('wheel',{deltaY:180});await page.waitForTimeout(550);
    assert(await page.locator('#followButton').isVisible(),'manual browsing exposes follow action');
    assert(await page.locator('.lyric-line').last().evaluate(e=>{const f=getComputedStyle(e).filter;return f==='none'||parseFloat(f.slice(5))<.05;}),'manual browsing sharpens surrounding lines');
    await page.locator('#followButton').click();await page.waitForTimeout(550);assert.equal(await page.locator('#followButton').isVisible(),false);
    await page.evaluate(()=>App.receive({track:{key:'qa',title:'',artist:'',cover:''},lyrics:{lines:[],loading:false,status:''},permissions:{notifications:false}}));await page.waitForTimeout(100);
    const action=await page.locator('#emptyAction').boundingBox(),footer=await page.locator('.player').boundingBox();assert(action&&action.y+action.height<=footer.y,'empty-state connection remains accessible');
    await page.screenshot({path:path.join(out,'empty-state.png')});
    result.checks.push('large Korean text 22–46','lyric tap seeks','manual browse sharpens lyrics + follow','empty-state connection');
   }
   await page.evaluate(s=>App.receive(s),state(lines));await page.waitForTimeout(180);
   if(viewport.width===412){
    const cover=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=32;c.height=32;c.getContext('2d').fillRect(0,0,32,32);return c.toDataURL('image/png');});
    await page.evaluate(value=>App.receive({track:{cover:value}}),cover);
    await page.waitForFunction(()=>!document.getElementById('coverImage').hidden && document.getElementById('coverImage').naturalWidth===32);
    assert(await page.locator('#coverImage').isVisible(),'late cover update on the same song');
    await page.evaluate(()=>App.receive({track:{key:'next-art-test',cover:''}}));assert.equal(await page.locator('#coverImage').isVisible(),false);
    await page.evaluate(()=>{Android.elapsedRealtime=()=>51000;App.receive({track:{key:'delay-test',position:10000,sampledAt:50000,playing:true,speed:1,positionKnown:true}});});
    assert(await page.evaluate(()=>App.position()>=11000 && App.position()<11300),'native delivery latency compensated');
    await page.evaluate(()=>App.receive({tick:true,track:{position:10000,sampledAt:50000,playing:false,speed:1,positionKnown:true}}));
    assert.equal(Math.round(await page.evaluate(()=>App.position())),10000,'paused packet must not extrapolate delivery latency');
    await page.evaluate(()=>App.receive({tick:true,track:{position:10000,sampledAt:50000,playing:true,speed:2,positionKnown:true}}));
    assert(await page.evaluate(()=>App.position()>=12000 && App.position()<12300),'delivery latency respects speed');
    await page.waitForTimeout(180);const before=await page.evaluate(()=>App.position());
    await page.evaluate(value=>App.receive({track:{cover:value},settings:{fontSize:32}}),cover);
    assert(await page.evaluate(()=>App.position())>=before,'artwork/settings updates must not rewind playback');
    await page.evaluate(s=>{delete Android.elapsedRealtime;App.receive(s);},state(lines));
    const untimed=[{start:0,end:5000,text:'줄 단위 가사',realTiming:false,words:[{start:0,end:5000,text:'줄 단위 가사'}]}];
    await page.evaluate(s=>App.receive(s),state(untimed));assert.equal(await page.locator('.lyric-word').count(),0);
    await page.evaluate(()=>App.receive({settings:{wordMode:'always'}}));assert(await page.locator('.lyric-word').count()>0);
    await page.evaluate(()=>App.receive({lyrics:{lines:[],loading:true,status:'가사를 찾고 있어요…'}}));assert.match(await page.locator('#emptyTitle').textContent(),/찾고/);
    await page.evaluate(()=>App.receive({track:{actions:0}}));assert(await page.locator('#playButton').isDisabled());assert(await page.locator('#seek').isDisabled());
    const seq=Array.from({length:10},(_,i)=>({start:i*1000,end:(i+1)*1000,text:'시간 '+i,realTiming:false,words:[]}));
    await page.evaluate(s=>App.receive(s),state(seq));await page.evaluate(()=>{App.receive({settings:{offset:1000}});App.receive({tick:true,track:{key:'qa',position:5000,playing:false,speed:1,positionKnown:true}})});await page.waitForTimeout(180);assert.match(await page.locator('.lyric-line.current').textContent(),/시간 6/);
    await page.evaluate(()=>App.receive({lyrics:{lines:[{start:0,end:10000,text:'<img src=x onerror=alert(1)>',words:[]}]}}));assert.equal(await page.locator('#lyricsList img').count(),0);
    await page.locator('#settingsButton').click();await page.locator('#provider').selectOption('netease');assert(await page.evaluate(()=>commands.some(x=>x.action==='setSetting'&&x.data.key==='provider'&&x.data.value==='netease')));await page.evaluate(()=>App.handleBack());
    await page.locator('#settingsButton').click();await page.locator('#searchButton').click();await page.locator('#searchTrack').fill('직접 검색');await page.locator('#searchForm').evaluate(f=>f.requestSubmit());assert(await page.evaluate(()=>commands.some(x=>x.action==='search'&&x.data.title==='직접 검색')));
    await page.evaluate(()=>App.searchResults({items:[{id:'candidate',title:'검색 결과',artist:'테스트',album:'QA',provider:'LRCLIB',duration:100000,score:95}],error:''}));await page.locator('.search-result').first().click();assert(await page.evaluate(()=>commands.some(x=>x.action==='chooseResult'&&x.data.id==='candidate')));
    await page.evaluate(()=>{App.receive({lyrics:{lines:[{start:0,end:10000,text:'소리에 맞춰 번지는 빛',realTiming:false,words:[]}]},track:{playing:true,position:2000},settings:{backgroundMode:'sound',backgroundIntensity:80,backgroundParticles:true}});App.audio({status:'live',message:'재생 소리에 반응 중',source:'visualizer',connected:true,available:true,level:.8,bass:.9,mid:.5,treble:.7,beat:.8,sequence:1});});
    await page.waitForTimeout(200);let visual=await page.evaluate(()=>ReactiveAmbient.metrics());
    assert(visual.webgl&&visual.frames>0&&visual.energy[1]>.25&&visual.rings>0&&visual.particles>0,'actual signal drives shader, waves and particles');
    assert.match(await page.locator('#audioQuickLabel').textContent(),/반응/);
    await page.screenshot({path:path.join(out,'sound-reactive.png')});
    await page.waitForTimeout(1100);visual=await page.evaluate(()=>ReactiveAmbient.metrics());assert(visual.energy.every(x=>x<.05),'stale signal must fade without invented beats');
    await page.evaluate(()=>{App.receive({tick:true,track:{playing:false,position:2000}});App.audio({status:'paused',connected:true,available:false,sequence:1});});await page.waitForTimeout(300);
    assert((await page.evaluate(()=>ReactiveAmbient.metrics())).energy.every(x=>x<.05),'paused input has no reactive energy');
    await page.evaluate(()=>App.receive({settings:{backgroundMode:'static'}}));await page.waitForTimeout(100);const frozen=await page.evaluate(()=>ReactiveAmbient.metrics().frames);await page.waitForTimeout(180);assert.equal(await page.evaluate(()=>ReactiveAmbient.metrics().frames),frozen,'static mode stops shader redraw');
    await page.locator('#settingsButton').click();await page.locator('#backgroundPreview').click();await page.waitForTimeout(200);
    assert((await page.evaluate(()=>ReactiveAmbient.metrics())).preview,'explicit effect preview enabled');assert.match(await page.locator('#audioStatus').textContent(),/미리/);
    await page.locator('#audioConnect').click();assert(await page.evaluate(()=>commands.some(x=>x.action==='audioConnect')),'explicit audio consent action reaches native');
    await page.locator('#audioCapture').click();assert(await page.evaluate(()=>commands.some(x=>x.action==='audioCapture')),'alternative playback-capture consent reaches native');
    await page.locator('#backgroundMode').selectOption('ambient');assert(await page.evaluate(()=>commands.some(x=>x.action==='setSetting'&&x.data.key==='backgroundMode'&&x.data.value==='ambient')));
    await page.evaluate(()=>App.handleBack());
    const long=Array.from({length:2000},(_,i)=>({start:i*2000,end:(i+1)*2000,text:'긴 가사 '+i,realTiming:false,words:[]}));
    const ms=await page.evaluate(s=>{const t=performance.now();App.receive(s);return performance.now()-t;},state(long));await page.waitForTimeout(150);assert.equal(await page.locator('.lyric-line').count(),2000);result.longLyricsBuildMs=Math.round(ms);
    result.checks.push('native bridge','overlapping vocals','true/estimated timing','word modes','loading state','transport capability bits','paused seek + offset','lyric injection text','provider setting','manual search + result selection','2000-line document','late artwork on same song','clear art on song switch','native delivery latency + speed','paused latency','partial packets preserve clock');
    result.checks.push('signal-driven fluid shader + rings + particles','stale audio decay','pause clears audio energy','static mode render freeze','labelled effect preview','two explicit native audio connection actions','background mode preference');
   }
   await page.close();
  }
  for(const fallback of ['reduced-motion','no-webgl']){
   const page=await browser.newPage({viewport:{width:412,height:915},reducedMotion:fallback==='reduced-motion'?'reduce':'no-preference'});
   page.on('pageerror',e=>result.errors.push(e.message));
   await page.addInitScript(({initial,block})=>{window.Android={getBootstrap:()=>JSON.stringify(initial),command:()=>{}};if(block){const original=HTMLCanvasElement.prototype.getContext;HTMLCanvasElement.prototype.getContext=function(kind,...args){return kind==='webgl'?null:original.call(this,kind,...args);};}},{initial:state(lines),block:fallback==='no-webgl'});
   await page.goto('file://'+path.join(root,'app/src/main/assets/web/index.html'));
   await page.evaluate(()=>{App.receive({track:{playing:true,position:2000}});App.audio({status:'live',available:true,level:1,bass:1,mid:1,treble:1,beat:1,sequence:3});});await page.waitForTimeout(250);
   const info=await page.evaluate(()=>ReactiveAmbient.metrics());
   if(fallback==='reduced-motion')assert(info.reducedMotion&&info.energy.every(x=>x===0)&&info.rings===0,'system reduced motion respected');else assert(!info.webgl&&await page.locator('.ambient-fallback').isVisible(),'CSS gradient fallback survives unavailable WebGL');
   result.checks.push(fallback);await page.close();
  }
  assert.equal(result.errors.length,0,'browser JavaScript errors');result.passed=true;fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(result,null,2));process.stdout.write(JSON.stringify(result,null,2)+'\n');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
