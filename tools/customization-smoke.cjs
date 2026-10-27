const {chromium}=require('playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),out=path.join(root,'build/customization-qa');
const url=process.env.BETTERLYRICS_PREVIEW_URL||'file://'+path.join(root,'app/src/main/assets/web/index.html');
const initial={track:{key:'custom',title:'노래',artist:'가수',duration:60000,position:5000,positionKnown:true,playing:false,actions:822,packageName:'qa'},lyrics:{lines:[{start:0,end:60000,text:'가나다라마바사',words:[]}]},settings:{fontSize:28,backgroundMode:'static'},permissions:{notifications:true,connected:true},sources:[]};
(async()=>{
 fs.mkdirSync(out,{recursive:true});const result={passed:false,checks:[],errors:[]};
 const browser=await chromium.launch({headless:true,executablePath:process.env.BETTERLYRICS_CHROME||undefined,args:['--no-sandbox','--enable-unsafe-swiftshader']});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}});page.on('pageerror',e=>result.errors.push(e.message));
  await page.addInitScript(s=>{window.commands=[];window.Android={getBootstrap:()=>JSON.stringify(s),command:(action,json)=>commands.push({action,data:JSON.parse(json)})};},initial);
  await page.goto(url);await page.locator('#settingsButton').click();
  for(const [id,value] of [['widgetTheme','light'],['widgetSize','large']])await page.locator('#'+id).selectOption(value);
  await page.locator('label').filter({has:page.locator('#widgetCover')}).click();assert.equal(await page.locator('#widgetCover').isChecked(),false);await page.locator('#addWidget').click();
  assert(await page.evaluate(()=>[['widgetTheme','light'],['widgetSize','large'],['widgetCover',false]].every(([key,value])=>commands.some(c=>c.action==='setSetting'&&c.data.key===key&&c.data.value===value))));
  assert(await page.evaluate(()=>commands.some(c=>c.action==='addWidget')));
  assert.equal(await page.locator('#settingsSheet .permission-note, #settingsSheet small').count(),0);
  result.checks.push('widget theme, size, cover and pin commands','concise settings');
  await page.locator('#appearanceButton').click();
  for(const [id,family] of [['nanum-gothic','BL Nanum Gothic'],['nanum-myeongjo','BL Nanum Myeongjo'],['jua','BL Jua'],['nanum-pen','BL Nanum Pen']]){
   await page.locator('.font-choice[data-font="'+id+'"]').click();
   assert.equal(await page.locator('.font-choice[data-font="'+id+'"]').getAttribute('aria-pressed'),'true');
   const loaded=await page.evaluate(async name=>{const faces=await document.fonts.load('400 24px "'+name+'"','가나다라마바사');return faces.length>0&&faces.every(face=>face.status==='loaded')&&document.fonts.check('400 24px "'+name+'"','가나다라마바사');},family);
   assert(loaded,'Korean font loaded: '+family);
   assert((await page.locator('.lyric-line').first().evaluate(el=>getComputedStyle(el).fontFamily)).includes(family));
   assert(await page.evaluate(font=>commands.some(c=>c.action==='setAppearance'&&c.data.appearance.font===font),id));
   if(id==='jua'){
    await page.evaluate(()=>{const sample=document.createElement('div');sample.id='testFontWeight';sample.className='lyric-line current';sample.textContent='가나다라';sample.style.cssText='position:fixed;top:0;left:0;z-index:1000;width:300px;font-size:48px;opacity:1;filter:none;background:white;color:black';document.body.append(sample);});
    const sample=page.locator('#testFontWeight');await sample.evaluate(el=>el.style.fontWeight='400');const normal=await sample.screenshot();
    await sample.evaluate(el=>el.style.fontWeight='900');const bold=await sample.screenshot();
    assert(!normal.equals(bold),'font weight changes the rendered Korean glyphs');await sample.evaluate(el=>el.remove());
    result.checks.push('bundled Korean font responds visibly to weight changes');
   }
  }
  await page.locator('[data-preset=focus]').click();
  assert.equal(await page.evaluate(()=>App.getState().settings.appearance.font),'nanum-pen','changing preset keeps selected font');
  await page.locator('#appearanceReset').click();
  assert.equal(await page.evaluate(()=>App.getState().settings.appearance.font),'system','reset restores default font');
  result.checks.push('four bundled Korean fonts load and apply','font survives preset changes; reset restores default');
  await page.screenshot({path:path.join(out,'fonts.png')});await page.close();
  const demo=await browser.newPage({viewport:{width:412,height:915}});demo.on('pageerror',e=>result.errors.push(e.message));await demo.goto(url);
  await demo.locator('#settingsButton').click();await demo.locator('#appearanceButton').click();await demo.locator('.font-choice[data-font="jua"]').click();await demo.reload();
  assert.equal(await demo.evaluate(()=>App.getState().settings.appearance.font),'jua','demo stores selected font across reload');
  result.checks.push('browser font persistence');await demo.close();assert.deepEqual(result.errors,[]);result.passed=true;
 }finally{fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(result,null,2)+'\n');await browser.close();}
 console.log(JSON.stringify(result,null,2));
})().catch(e=>{console.error(e);process.exit(1)});
