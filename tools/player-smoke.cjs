const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'..'),out=path.join(root,'build','ui-qa');
fs.mkdirSync(out,{recursive:true});
const lines=Array.from({length:20},(_,i)=>({start:i*4000,end:(i+1)*4000,text:'밤의 여백을 따라 흐르는 노래 '+i,realTiming:false,words:[]}));
const state={track:{key:'player-qa',title:'밤의 여백',artist:'화면 미리보기',album:'밤의 기록',position:24500,duration:120000,positionKnown:true,playing:false,speed:1,packageName:'qa',appName:'테스트 음악 앱',actions:822},lyrics:{provider:'Local',lines},settings:{fontSize:31,offset:0,wordMode:'auto',backgroundMode:'static'},volume:{available:true,level:6,max:15},permissions:{notifications:true,connected:true,overlay:true},sources:[{packageName:'qa',name:'테스트 음악 앱',playing:false}]};
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.BETTERLYRICS_CHROME||undefined,args:['--no-sandbox','--enable-unsafe-swiftshader']});
 const result={checks:[],errors:[],viewports:[]};
 try{
  for(const viewport of [{width:360,height:740},{width:412,height:915},{width:812,height:375},{width:1280,height:800},{width:800,height:1280}]){
   const page=await browser.newPage({viewport});page.on('pageerror',e=>result.errors.push(e.message));
   await page.addInitScript(s=>{window.commands=[];window.Android={getBootstrap:()=>JSON.stringify(s),command:(action,json)=>commands.push({action,data:JSON.parse(json)})};},state);
   await page.goto('file://'+path.join(root,'app/src/main/assets/web/index.html'));await page.waitForTimeout(650);
   assert.equal(await page.locator('#durationLabel').textContent(),'−1:35','remaining time uses the playback clock');
   assert(await page.locator('#volume').isEnabled());assert.equal(await page.locator('#volume').inputValue(),'400');
   assert.equal(await page.locator('#searchButton').isVisible(),false,'secondary tools live in the menu');
   for(const mode of ['lyrics','album','lyrics']){
    if(mode!=='lyrics'||await page.locator('#lyricsButton').getAttribute('aria-pressed')==='false')await page.locator('#lyricsButton').click();
    await page.waitForTimeout(180);
    const artwork=await page.locator('#lyricsButton').getAttribute('aria-pressed')==='false';
    const geometry=await page.evaluate(()=>{const b=s=>{const r=document.querySelector(s).getBoundingClientRect();return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,width:r.width,height:r.height};};return {player:b('.player'),track:b('.track-info'),cover:b('.cover'),width:innerWidth,height:innerHeight,scroll:document.documentElement.scrollWidth};});
    assert(geometry.player.bottom<=viewport.height+1&&geometry.scroll<=viewport.width+1,'player fits viewport in both views');
    assert(geometry.track.bottom<=geometry.player.top+1,'album and metadata stay clear of the player '+JSON.stringify({viewport,mode,geometry}));
    assert.equal(await page.locator('.lyrics-shell').isVisible(),!artwork);
    if(viewport.width===412||viewport.width===1280)await page.screenshot({path:path.join(out,`player-${artwork?'album':'lyrics'}-${viewport.width}.png`)});
    result.viewports.push({...viewport,mode:artwork?'album':'lyrics',...geometry});
   }
   await page.locator('#collapseButton').evaluate(e=>e.click());assert.equal(await page.locator('#lyricsButton').getAttribute('aria-pressed'),'false');
   await page.locator('#lyricsButton').click();await page.waitForTimeout(180);assert(await page.locator('.lyric-line.current').count()>0,'lyrics follow resumes after album view');
   if(viewport.width===412){
    await page.locator('#volume').evaluate(e=>{e.value='650';e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));});
    assert(await page.evaluate(()=>commands.some(c=>c.action==='setVolume'&&c.data.value===650)),'volume control reaches native bridge');
    await page.evaluate(()=>App.receive({volume:{available:true,level:10,max:15}}));assert.equal(await page.locator('#volume').inputValue(),'667');assert.equal(Math.round(await page.evaluate(()=>App.position())),24500,'volume-only update preserves playback clock');
    await page.evaluate(()=>App.receive({tick:true,track:{position:24500,playing:false},volume:{available:true,level:3,max:15}}));assert.equal(await page.locator('#volume').inputValue(),'200','hardware-volume update is reflected');
    await page.evaluate(()=>App.receive({volume:{available:false,level:3,max:15}}));assert(await page.locator('#volume').isDisabled(),'fixed-volume devices disable control');
    await page.locator('#settingsButton').click();assert(await page.locator('#lyricsBadge').isVisible());await page.locator('#settingsImport').click();assert(await page.evaluate(()=>commands.some(c=>c.action==='importLyrics')));
    await page.locator('#overlayAccess').click();assert(await page.evaluate(()=>commands.some(c=>c.action==='toggleOverlay')));
    await page.locator('#searchButton').click();assert(await page.locator('#searchSheet').isVisible());await page.evaluate(()=>App.handleBack());assert.equal(await page.evaluate(()=>document.activeElement.id),'settingsButton','nested menu closes with visible focus');
    await page.locator('#sourceButton').click();assert(await page.locator('#sourcesSheet').isVisible());await page.locator('.source-item').first().click();assert(await page.evaluate(()=>commands.some(c=>c.action==='selectSource')));
    for(const id of ['playButton','previousButton','nextButton'])await page.locator('#'+id).click();
    assert(await page.evaluate(()=>['playPause','previous','next'].every(action=>commands.some(c=>c.action==='transport'&&c.data.action===action))));
    await page.locator('#audioQuick').click();assert(await page.evaluate(()=>commands.some(c=>c.action==='audioConnect')));
    await page.locator('#seek').evaluate(e=>{e.value='600';e.dispatchEvent(new Event('input',{bubbles:true}));});await page.waitForTimeout(140);assert.equal(await page.locator('#durationLabel').textContent(),'−0:48','scrub preview updates remaining time');await page.locator('#seek').dispatchEvent('pointercancel');
    result.checks.push('native volume + hardware update + fixed volume','volume packets preserve playback clock','menu search/import/overlay and focus return','source selection and all transport actions','sound connection and scrub remaining time');
   }
   await page.close();
  }
  result.checks.push('lyrics and album views fit five screen sizes','both lyric-view controls work and follow resumes');
  assert.equal(result.errors.length,0);result.passed=true;fs.writeFileSync(path.join(out,'player-results.json'),JSON.stringify(result,null,2));process.stdout.write(JSON.stringify(result,null,2)+'\n');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
