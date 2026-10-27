// Regression coverage for playback state, interrupted search and control accessibility.
const {chromium} = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const out = path.join(root, 'build', 'review-qa');
const url = process.env.BETTERLYRICS_PREVIEW_URL || 'file://' + path.join(root, 'app/src/main/assets/web/index.html');
const initial = {
  track: {key:'review', title:'밤의 여백', artist:'화면 미리보기', album:'밤의 기록', appName:'테스트 음악 앱', packageName:'qa.player', position:24500, duration:120000, playing:false, positionKnown:true, actions:822},
  lyrics: {provider:'Local', lines:Array.from({length:16}, (_, i) => ({start:i*4000, end:(i+1)*4000, text:'함께 걷는 이 밤의 멜로디 ' + i, words:[]}))},
  settings: {fontSize:31, backgroundMode:'static', appearance:{controls:'always'}},
  permissions: {notifications:true, connected:true}, sources:[], volume:{available:true, level:6, max:15}
};

(async () => {
  fs.mkdirSync(out, {recursive:true});
  const result = {passed:false, checks:[], viewports:[], errors:[]};
  const browser = await chromium.launch({headless:true, executablePath:process.env.BETTERLYRICS_CHROME || undefined, args:['--no-sandbox', '--enable-unsafe-swiftshader']});
  try {
    for (const viewport of [{width:320,height:640}, {width:360,height:740}, {width:412,height:915}, {width:812,height:375}, {width:1280,height:800}]) {
      const page = await browser.newPage({viewport});
      page.on('pageerror', error => result.errors.push(error.message));
      await page.addInitScript(state => {
        window.commands = [];
        window.Android = {getBootstrap:() => JSON.stringify(state), command:(action, json) => commands.push({action, data:JSON.parse(json)})};
      }, initial);
      await page.goto(url);
      await page.waitForTimeout(600);
      assert.equal(await page.locator('#sessionLabel').textContent(), '일시정지');
      assert.equal(await page.locator('#sessionSource').textContent(), '테스트 음악 앱');
      const geometry = await page.evaluate(() => {
        const box = selector => {const r=document.querySelector(selector).getBoundingClientRect();return {left:r.left, right:r.right, top:r.top, bottom:r.bottom};};
        return {transport:box('.transport-main'), actions:box('.player-actions'), footer:box('.player'), width:innerWidth, height:innerHeight};
      });
      assert(geometry.transport.right <= geometry.actions.left || geometry.transport.bottom <= geometry.actions.top, 'transport and secondary actions must not overlap: ' + JSON.stringify({viewport, geometry}));
      assert(geometry.footer.left >= 0 && geometry.footer.right <= viewport.width && geometry.footer.bottom <= viewport.height, 'footer fits viewport');
      for (const id of ['playButton','previousButton','nextButton','lyricsButton','audioQuick','sourceButton','settingsButton']) {
        const b = await page.locator('#' + id).boundingBox();
        assert(b && b.width >= 44 && b.height >= 44, id + ' retains a 44px touch target');
      }
      await page.screenshot({path:path.join(out, 'player-' + viewport.width + '.png')});
      result.viewports.push(viewport);
      await page.evaluate(() => App.receive({track:{key:'', title:'', artist:'', album:'', packageName:''}, lyrics:{lines:[]}, permissions:{notifications:false}}));
      assert(await page.locator('#emptySecondary').isHidden(), 'import requires a selected song');
      for (const id of ['emptyAction']) {
        await page.locator('#' + id).scrollIntoViewIfNeeded();
        const action = await page.locator('#' + id).boundingBox();
        const footer = await page.locator('.player').boundingBox();
        assert(action && action.y >= 0 && action.y + action.height <= footer.y, 'empty-state action stays accessible above playback controls');
      }
      await page.evaluate(state => App.receive(state), initial);
      if (viewport.width === 412) {
        await page.evaluate(() => App.receive({tick:true, track:{position:24500, playing:true}}));
        assert.equal(await page.locator('#sessionLabel').textContent(), '지금 재생 중');
        await page.evaluate(() => App.receive({track:{playing:false, appName:'새 음악 앱'}}));
        assert.equal(await page.locator('#sessionSource').textContent(), '새 음악 앱', 'source updates even when artwork is unchanged');
        assert.equal(await page.locator('#seek').getAttribute('aria-valuetext'), '0:24 / 2:00');
        await page.locator('#seek').evaluate(el => {el.value='600';el.dispatchEvent(new Event('input', {bubbles:true}));});
        await page.waitForTimeout(100);
        assert.equal(await page.locator('#seek').getAttribute('aria-valuetext'), '1:12 / 2:00', 'screen reader receives scrub time');
        await page.locator('#seek').dispatchEvent('pointercancel');
        result.checks.push('source and playback state updates', 'accessible elapsed/total time and scrub preview');

        await page.evaluate(() => App.audio({status:'error', source:'visualizer', connected:false, available:false, message:'권한을 확인해 주세요.'}));
        assert.equal(await page.locator('#audioStatus').textContent(), '권한을 확인해 주세요.');
        await page.evaluate(() => App.audio({status:'error', source:'visualizer', connected:false, available:false, message:'연결이 종료됐어요.'}));
        assert.equal(await page.locator('#audioStatus').textContent(), '연결이 종료됐어요.', 'same status can carry a new failure reason');
        await page.evaluate(() => App.audio({status:'live', source:'visualizer', connected:true, available:false}));
        assert.equal(await page.locator('#audioQuick').evaluate(el => el.classList.contains('connected')), false);
        await page.evaluate(() => App.audio({status:'live', source:'visualizer', connected:true, available:true}));
        assert.equal(await page.locator('#audioQuick').evaluate(el => el.classList.contains('connected')), true, 'availability alone updates connection indicator');
        result.checks.push('audio reason and availability updates without a status change');

        await page.locator('#settingsButton').click();
        await page.waitForTimeout(300);
        await page.screenshot({path:path.join(out, 'settings.png')});
        await page.locator('#searchButton').click();
        await page.locator('#searchForm').evaluate(el => el.requestSubmit());
        assert(await page.locator('#submitSearch').isDisabled());
        await page.evaluate(() => App.handleBack());
        await page.locator('#settingsButton').click();
        await page.locator('#searchButton').click();
        assert(await page.locator('#submitSearch').isEnabled(), 'closing an in-flight search must not lock the next search');
        await page.locator('#searchForm').evaluate(el => el.requestSubmit());
        assert.equal(await page.evaluate(() => commands.filter(c => c.action === 'search').length), 2);
        await page.evaluate(() => App.handleBack());
        result.checks.push('interrupted search can be reopened and submitted');
      }
      await page.close();
    }
    result.checks.push('44px control targets and nonoverlapping playback controls on five viewports', 'empty-state action stays accessible above playback controls');
    assert.deepEqual(result.errors, []);
    result.passed = true;
  } finally {
    fs.writeFileSync(path.join(out, 'results.json'), JSON.stringify(result, null, 2) + '\n');
    await browser.close();
  }
  console.log(JSON.stringify(result, null, 2));
})().catch(error => {console.error(error);process.exit(1);});
