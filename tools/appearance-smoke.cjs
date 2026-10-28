const {chromium}=require('playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),out=path.join(root,'build/ui-qa');
const initial={track:{key:'custom',title:'노래가 머무는 순간',artist:'BetterLyrics · 화면 예시',position:24000,duration:80000,playing:false,positionKnown:true,actions:822},lyrics:{lines:Array.from({length:16},(_,i)=>({start:i*5000,end:(i+1)*5000,text:['창문 너머 번지는 빛','잠깐, 이 순간을 들어봐','흘러가는 하루 사이','네가 좋아하는 리듬으로','작은 숨을 고르고','다시 우리 속도로 걸어','오래 남을 이 멜로디','오늘의 색을 기억해'][i%8],words:[]}))},settings:{fontSize:28,offset:750,backgroundMode:'static'},permissions:{notifications:true,connected:true}};
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.BETTERLYRICS_CHROME||undefined,args:['--no-sandbox','--enable-unsafe-swiftshader']});
 const result={passed:false,checks:[],errors:[]};
 try{
  for(const viewport of [{width:360,height:740},{width:412,height:915},{width:812,height:375},{width:1280,height:800}]){
   const page=await browser.newPage({viewport});page.on('pageerror',e=>result.errors.push(e.message));
   await page.addInitScript(s=>{window.commands=[];window.Android={getBootstrap:()=>JSON.stringify(s),command:(action,json)=>commands.push({action,data:JSON.parse(json)})};},initial);
   await page.goto(process.env.BETTERLYRICS_PREVIEW_URL||'file://'+path.join(root,'app/src/main/assets/web/index.html'));await page.waitForTimeout(200);
   for(const layout of ['auto','immersive','reverse']){
    await page.evaluate(layout=>App.receive({settings:{appearance:{version:2,layout,controls:'always'}}}),layout);
    await page.locator('#lyricsButton').click();await page.waitForTimeout(160);
    const bounds=await page.evaluate(()=>{const c=document.querySelector('.cover').getBoundingClientRect(),t=document.querySelector('.track-info').getBoundingClientRect(),p=document.querySelector('.player').getBoundingClientRect();return {cover:c.bottom,title:t.bottom,player:p.top,size:c.width};});
    assert(bounds.cover<=bounds.player+1&&bounds.title<=bounds.player+1&&bounds.size>=90,'all layouts retain usable album view');
    await page.locator('#lyricsButton').click();
   }
   await page.evaluate(()=>App.receive({settings:{appearance:{}}}));
   await page.locator('#settingsButton').click();await page.locator('#appearanceButton').click();
   assert(await page.locator('#appearanceSheet').isVisible());assert(await page.locator('#app').evaluate(e=>e.inert),'background is not keyboard interactive under dialog');
   for(const preset of ['focus','album','original']){
    await page.locator(`[data-preset=${preset}]`).click();assert.equal(await page.locator(`[data-preset=${preset}]`).getAttribute('aria-pressed'),'true');
    const geometry=await page.locator('#appearanceSheet').boundingBox();assert(geometry.x>=0&&geometry.x+geometry.width<=viewport.width+1);assert(geometry.y>=0&&geometry.y+geometry.height<=viewport.height+1);
   }
   await page.locator('#appearance-align').selectOption('right');
   await page.locator('#appearance-blur').fill('4');await page.locator('#appearance-blur').dispatchEvent('input');
   await page.locator('#appearance-showCover').uncheck();
   assert.equal(await page.locator('#app').getAttribute('data-align'),'right');assert.equal(await page.locator('.cover').isVisible(),false);
   assert(await page.evaluate(()=>commands.some(c=>c.action==='setAppearance'&&c.data.appearance.blur===4&&c.data.appearance.showCover===false)));
   assert.equal(Math.round(await page.evaluate(()=>App.position())),24000,'appearance updates preserve clock');
   await page.locator('#appearanceReset').click();assert.equal(await page.locator('#app').getAttribute('data-align'),'left');assert(await page.locator('.cover').isVisible());
   assert.equal(await page.locator('#offset').inputValue(),'750','reset preserves per-song offset');
   await page.locator('[aria-label="꾸미기 완료"]').click();assert.equal(await page.locator('#app').evaluate(e=>e.inert),false);
   await page.evaluate(()=>App.receive({settings:{appearance:{controls:'auto'}},track:{playing:true,position:24000}}));
   await page.waitForTimeout(3800);assert(await page.locator('#app').evaluate(e=>e.classList.contains('controls-hidden')));assert(await page.locator('.player').evaluate(e=>e.inert));
   await page.locator('#controlsToggle').click();assert(await page.locator('.player').isVisible());
   await page.evaluate(()=>App.receive({track:{playing:false,position:24000},settings:{appearance:{version:2,controls:'always'}}}));
   await page.keyboard.press('Tab');assert.equal(await page.locator('.player').evaluate(e=>e.inert),false);
   result.checks.push(viewport.width+': presets, live preferences, modal focus, reset/clock/offset isolation, hide/reveal');
   await page.close();
  }
  // Demo persistence is distinct from Android SharedPreferences; this validates only the web path.
  const page=await browser.newPage({viewport:{width:412,height:915}});page.on('pageerror',e=>result.errors.push(e.message));
  await page.goto(process.env.BETTERLYRICS_PREVIEW_URL||'file://'+path.join(root,'app/src/main/assets/web/index.html'));await page.locator('#settingsButton').click();await page.locator('#appearanceButton').click();await page.locator('[data-preset=focus]').click();await page.reload();
  assert.equal(await page.locator('#app').getAttribute('data-align'),'center');result.checks.push('browser demo preference reload');
  assert.equal(result.errors.length,0);result.passed=true;fs.writeFileSync(path.join(out,'appearance-results.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
