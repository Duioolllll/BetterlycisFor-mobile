const {chromium}=require('playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),out=path.join(root,'build/playlist-qa');
const url=process.env.BETTERLYRICS_PREVIEW_URL||'file://'+path.join(root,'app/src/main/assets/web/index.html');
const initial={track:{key:'qa',title:'노래',artist:'가수',duration:60000,position:1000,positionKnown:true,playing:false,actions:822,packageName:'qa'},lyrics:{lines:[]},settings:{backgroundMode:'static',appearance:{controls:'always'}},permissions:{notifications:true,connected:true},sources:[]};
(async()=>{
 fs.mkdirSync(out,{recursive:true});const result={passed:false,checks:[],errors:[]};
 const browser=await chromium.launch({headless:true,executablePath:process.env.BETTERLYRICS_CHROME||undefined,args:['--no-sandbox','--enable-unsafe-swiftshader']});
 try{
  for(const viewport of [{width:320,height:640},{width:360,height:740},{width:412,height:915},{width:812,height:375}]){
   const page=await browser.newPage({viewport});page.on('pageerror',e=>result.errors.push(e.message));
   await page.addInitScript(s=>{window.commands=[];window.Android={getBootstrap:()=>JSON.stringify(s),command:(action,json)=>commands.push({action,data:JSON.parse(json)})};},initial);
   await page.goto(url);await page.locator('#playlistButton').click();
   await page.waitForFunction(()=>document.querySelector('#playlistSheet').getBoundingClientRect().bottom<=innerHeight+0.1);
   assert(await page.locator('#playlistSheet').isVisible());
   const geometry=await page.evaluate(()=>{const r=document.querySelector('#playlistSheet').getBoundingClientRect();return {left:r.left,right:r.right,bottom:r.bottom,width:document.documentElement.scrollWidth,viewport:innerWidth,height:innerHeight}});
   assert(geometry.left>=-0.1&&geometry.right<=viewport.width+0.1&&geometry.bottom<=viewport.height+0.1&&geometry.width<=viewport.width,'playlist sheet fits '+JSON.stringify({viewport,geometry}));
   for(const id of ['playlistButton','localTab','spotifyTab']){const box=await page.locator('#'+id).boundingBox();assert(box&&box.width>=44&&box.height>=44,id+' touch target');}
   await page.close();
  }
  result.checks.push('playlist sheet fits four sizes and maintains accessible controls');
  const page=await browser.newPage({viewport:{width:412,height:915}});page.on('pageerror',e=>result.errors.push(e.message));
  await page.addInitScript(s=>{window.commands=[];window.Android={getBootstrap:()=>JSON.stringify(s),command:(action,json)=>commands.push({action,data:JSON.parse(json)})};},initial);
  await page.goto(url);await page.locator('#playlistButton').click();await page.locator('#importMusic').click();await page.locator('#importPlaylist').click();
  assert(await page.evaluate(()=>['getPlaylists','importMusic','importPlaylist'].every(a=>commands.some(c=>c.action===a))));
  await page.evaluate(()=>App.playlistBusy({busy:true}));assert(await page.locator('#importMusic').isDisabled());assert(await page.locator('#localImportStatus').isVisible());await page.evaluate(()=>App.playlistBusy({busy:false}));assert(await page.locator('#importMusic').isEnabled());
  result.checks.push('music-file and M3U picker actions');
  await page.evaluate(()=>App.playlists({items:[{id:'local-1',name:'<img src=x onerror=alert(1)>',count:2}],selected:{},mode:'all'}));
  assert.equal(await page.locator('#playlistList img').count(),0);assert((await page.locator('#playlistList').textContent()).includes('<img'));
  await page.locator('#playlistList button').click();assert(await page.evaluate(()=>commands.some(c=>c.action==='getPlaylists'&&c.data.id==='local-1')));
  await page.evaluate(()=>App.playlists({items:[{id:'local-1',name:'내 음악',count:2}],selected:{id:'local-1',name:'내 음악',tracks:[{title:'첫 곡',artist:'가수'},{title:'둘째 곡'}]},mode:'all'}));
  await page.locator('#playlistOrder').selectOption('shuffle');assert(await page.evaluate(()=>commands.some(c=>c.action==='localMode'&&c.data.mode==='shuffle')));
  await page.locator('#playlistSearch').fill('둘째');assert.equal(await page.locator('#playlistTracks .playlist-track').count(),1);await page.locator('#playlistTracks .playlist-track').click();assert(await page.evaluate(()=>commands.some(c=>c.action==='playLocal'&&c.data.id==='local-1'&&c.data.index===1)));assert(await page.locator('#playlistSheet').isHidden());
  result.checks.push('playlist text is escaped, selection and shuffle are sent to Android','selected track starts and closes the sheet');
  await page.locator('#playlistButton').click();await page.evaluate(()=>App.playlists({items:[{id:'local-1',name:'내 음악',count:2}],selected:{id:'local-1',name:'내 음악',tracks:[{title:'첫 곡'}]},mode:'all'}));
  await page.locator('#playlistSearch').fill('');await page.locator('#playlistTracks .playlist-track-remove').click();assert(await page.locator('#trackRemoveConfirm').isVisible());await page.locator('#trackRemoveYes').click();assert(await page.evaluate(()=>commands.some(c=>c.action==='removeLocalTrack'&&c.data.index===0)));
  await page.locator('#importMusic').click();assert(await page.evaluate(()=>commands.some(c=>c.action==='importMusic'&&c.data.id==='local-1')));
  await page.locator('#playlistRename').click();await page.locator('#playlistRenameInput').fill('내 즐겨찾기');await page.locator('#playlistRenameForm button[type=submit]').click();assert(await page.evaluate(()=>commands.some(c=>c.action==='renamePlaylist'&&c.data.name==='내 즐겨찾기')));
  await page.locator('#playlistRemove').click();assert(await page.locator('#playlistRemoveConfirm').isVisible());await page.locator('#playlistRemoveNo').click();assert(await page.locator('#playlistRemoveConfirm').isHidden());
  await page.locator('#playlistRemove').click();await page.locator('#playlistRemoveYes').click();assert(await page.evaluate(()=>commands.some(c=>c.action==='removePlaylist'&&c.data.id==='local-1')));
  result.checks.push('playlist and track removal require confirmation; files append to the selected list');
  await page.locator('#spotifyTab').click();assert(await page.evaluate(()=>commands.some(c=>c.action==='spotifyPlaylists')));
  assert(await page.locator('.spotify-brand img').evaluate(el=>el.complete&&el.naturalWidth>0));
  await page.evaluate(()=>App.spotify({view:'disconnected'}));await page.locator('#spotifyContent button').click();assert(await page.evaluate(()=>commands.some(c=>c.action==='spotifyConnect')));
  const id='0123456789ABCDEFGHIJKL';
  await page.evaluate(id=>App.spotify({view:'lists',connected:true,offset:0,nextOffset:50,more:true,items:[{id,name:'Spotify 목록',owner:'가수',count:1}]}),id);
  await page.getByRole('button',{name:'더 보기'}).click();assert(await page.evaluate(()=>commands.some(c=>c.action==='spotifyPlaylists'&&c.data.offset===50)));
  await page.evaluate(id=>App.spotify({view:'lists',connected:true,offset:50,more:false,items:[{id,name:'두 번째 목록',count:1}]}),id);assert.equal(await page.locator('#spotifyContent .playlist-item').count(),2);
  await page.locator('#spotifyContent .playlist-item').first().click();assert(await page.evaluate(id=>commands.some(c=>c.action==='spotifyTracks'&&c.data.id===id),id));
  await page.evaluate(id=>App.spotify({view:'tracks',connected:true,id,name:'Spotify 목록',offset:0,nextOffset:50,more:true,items:[{id,type:'track',title:'노래',artist:'가수'}]}),id);
  await page.getByRole('button',{name:'더 보기'}).click();assert(await page.evaluate(()=>commands.some(c=>c.action==='spotifyTracks'&&c.data.offset===50)));
  await page.evaluate(id=>App.spotify({view:'tracks',connected:true,id,name:'Spotify 목록',offset:50,more:false,items:[{id,type:'track',title:'다음 노래',artist:'가수'}]}),id);assert.equal(await page.locator('#spotifyContent .playlist-track').count(),2);
  await page.locator('#spotifyContent .playlist-track').first().click();assert(await page.evaluate(id=>commands.some(c=>c.action==='spotifyOpen'&&c.data.id===id&&c.data.type==='track'),id));
  await page.locator('#spotifyContent .spotify-header button').last().click();assert(await page.evaluate(()=>commands.some(c=>c.action==='spotifyOpen'&&c.data.type==='playlist')));
  result.checks.push('Spotify connection, list and track links','official Spotify branding is loaded');
  await page.evaluate(id=>App.spotify({view:'tracksError',connected:true,id,name:'Spotify 목록',message:'접근 권한이 없습니다.'}),id);
  assert((await page.locator('#spotifyContent').textContent()).includes('접근 권한이 없습니다.'));await page.getByRole('button',{name:'Spotify 연결 해제'}).click();
  assert(await page.evaluate(()=>commands.some(c=>c.action==='spotifyDisconnect')));result.checks.push('Spotify permission errors and disconnect');
  await page.screenshot({path:path.join(out,'spotify.png')});await page.close();assert.deepEqual(result.errors,[]);result.passed=true;
 }finally{fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(result,null,2)+'\n');await browser.close();}
 console.log(JSON.stringify(result,null,2));
})().catch(error=>{console.error(error);process.exit(1)});
