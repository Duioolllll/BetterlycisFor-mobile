'use strict';

(() => {
  const $ = id => document.getElementById(id);
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const number = (v, fallback = 0) => Number.isFinite(Number(v)) ? Number(v) : fallback;
  const native = !!(window.Android && typeof window.Android.getBootstrap === 'function');
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  let state = {
    track: {key:'', title:'', artist:'', album:'', duration:0, position:0, speed:1, playing:false, positionKnown:false},
    lyrics: {lines:[], provider:'', status:'waiting'},
    settings: {offset:0, fontSize:28, wordMode:'auto', provider:'auto', searchMode:'best', keepScreen:false, overlay:false},
    permissions: {notifications:false, overlay:false}, audio:{status:'off'}, volume:{available:false,level:0,max:0}, sources:[]
  };
  let clock = {base:0, at:performance.now(), playing:false, speed:1, correction:0};
  let lineModels = [], lyricsSignature = '', currentAnchor = -1, priorActive = '';
  let manualUntil = 0, scrollMotion = null, dragging = false, pointerStart = null, suppressClickUntil = 0;
  let flowGroups = [], flowLayoutDirty = true, snapFollow = true;
  let seekPreview = null, pendingSeek = null;
  let lyricsVisible=true,volumeSeeking=false,lastVolumeSent=0;
  let controlsUntil=performance.now()+3500,keyboardNavigation=false,controlsHidden=false;
  let seeking = false, lastVisual = 0, lastCover = '', lastSheet = null, toastTimer = 0, lastFocus = null;
  let wasPreview=false,audioStatusSignature='';
  let selectedLocal='',playlistTab='local',localTracks=[],pendingTrackRemoval=-1,localVisible=100,spotifyItems=[],spotifyListId='';
  let demoTime = performance.now(), demoPosition = 14500, demoPlaying = true;

  function position(now = performance.now()) {
    const progress = clock.playing ? Math.max(0, now-clock.at)*clock.speed : 0;
    const blend = clock.playing ? Math.max(0, 1-(now-clock.at)/200) : 0;
    const p = clock.base + progress + clock.correction*blend;
    return clamp(p, 0, state.track.duration > 0 ? state.track.duration : 86400000);
  }

  function updateClock(track, changed) {
    const now = performance.now(), old = position(now);
    const nextPlaying = !!track.playing, speed = number(track.speed, 1);
    let age=0;
    if(native && nextPlaying && track.positionKnown!==false && track.sampledAt!==undefined && typeof window.Android.elapsedRealtime==='function') {
      try {age=clamp(number(window.Android.elapsedRealtime())-number(track.sampledAt),0,10000);} catch(_) {}
    }
    const incoming=Math.max(0,number(track.position)+(nextPlaying?age*speed:0));
    // A player may deliver its previous sample briefly after seekTo(). Keep the local
    // seek until an acknowledgement arrives, but recover if the player rejects it.
    if (changed || track.positionKnown===false) {
      pendingSeek=null;seeking=false;seekPreview=null;
    } else if(pendingSeek) {
      const elapsed=now-pendingSeek.at;
      if(elapsed<1400 && Math.abs(incoming-old)>1200){
        if(nextPlaying!==clock.playing || speed!==clock.speed)clock={base:old,at:now,playing:nextPlaying,speed,correction:0};
        return;
      }
      pendingSeek=null;
    }
    const diff = old-incoming;
    clock = {base:incoming, at:now, playing:nextPlaying && track.positionKnown !== false, speed, correction: !changed && nextPlaying && Math.abs(diff)<300 ? diff : 0};
    if(Math.abs(diff)>750)lastVisual=0;
    if (changed || Math.abs(diff)>5300) { currentAnchor=-1; scrollMotion=null;snapFollow=true;lastVisual=0; }
  }

  function receive(input) {
    let incoming;
    try { incoming = typeof input === 'string' ? JSON.parse(input) : input; } catch (_) { return; }
    if (!incoming || typeof incoming !== 'object') return;
    const nextTrack = Object.assign({}, state.track, incoming.track || {});
    const changed = nextTrack.key !== state.track.key;
    // A settings/artwork-only packet must not rewind the clock to an older sample.
    if(changed || incoming.track && Object.prototype.hasOwnProperty.call(incoming.track,'position'))updateClock(nextTrack, changed);
    if(incoming.volume){state.volume=Object.assign({},state.volume,incoming.volume);updateVolume();}
    if (incoming.tick) { state.track=nextTrack; updateTransport(); return; }
    const oldSettings = state.settings;
    state = Object.assign({}, state, incoming, {
      track: nextTrack,
      settings: Object.assign({}, state.settings, incoming.settings || {}),
      permissions: Object.assign({}, state.permissions, incoming.permissions || {}),
      lyrics: Object.assign({}, state.lyrics, incoming.lyrics || {})
    });
    if (!Array.isArray(state.lyrics.lines)) state.lyrics.lines=[];
    if (!Array.isArray(state.sources)) state.sources=[];
    if (changed) { manualUntil=0; currentAnchor=-1; $('lyricsViewport').scrollTop=0; }
    updateMetadata();
    updateSettings();
    if(incoming.audio)audioFrame(incoming.audio);
    const signature = String(state.track.key)+'|'+JSON.stringify(state.lyrics.lines);
    if (signature!==lyricsSignature) { lyricsSignature=signature; buildLyrics(); }
    if (oldSettings.fontSize!==state.settings.fontSize || oldSettings.wordMode!==state.settings.wordMode) {
      if (oldSettings.wordMode!==state.settings.wordMode) buildLyrics();
      currentAnchor=-1;flowLayoutDirty=true;
    }
    updateEmpty(); updateBadge(); updateTransport();updateVolume();
    if(lastSheet==='sourcesSheet') renderSources();
  }

  function updateMetadata() {
    const t=state.track, has=hasSource();
    $('trackTitle').textContent=t.title || '음악이 시작되는 곳';
    $('trackArtist').textContent=t.artist || (has ? '아티스트 정보 없음' : '좋아하는 음악을 재생해 주세요');
    $('trackAlbum').textContent=t.album || '';
    $('sourceName').textContent=t.appName || (has ? '음악 앱 연결됨' : state.permissions.notifications && state.permissions.connected===false ? '음악 연결 중' : '음악 연결 대기');
    $('sessionSource').textContent=$('sourceName').textContent;
    document.querySelector('.session-bar').classList.toggle('is-connected',has);
    $('sourceDot').classList.toggle('connected',has);
    const cover=typeof t.cover==='string' ? t.cover : '';
    if(cover===lastCover) return;
    lastCover=cover;
    const valid=/^data:image\/(png|jpeg|webp);base64,[a-z\d+/=\s]+$/i.test(cover) && cover.length<3000000;
    const img=$('coverImage');
    img.hidden=true;
    if(valid) {
      img.onload=()=>{ if(lastCover===cover && img.getAttribute('src')===cover){img.hidden=false; extractPalette(img);} };
      img.onerror=()=>{img.hidden=true;};
      img.src=cover;
    } else {img.hidden=true;img.removeAttribute('src');}
  }

  function hasSource() { return !!(state.track.key && (state.track.packageName || !native)); }
  function actionAllowed(kind) {
    if(!hasSource())return false;
    if(state.track.actions===undefined || state.track.actions===null)return !native;
    const actions=number(state.track.actions);
    if(kind==='playPause')return !!(actions & (state.track.playing ? 2|512 : 4|512));
    return !!(actions & ({next:32,previous:16,seek:256}[kind] || 0));
  }
  function formatTime(value) {
    const sec=Math.max(0,Math.floor(number(value)/1000));
    return Math.floor(sec/60)+':'+String(sec%60).padStart(2,'0');
  }

  function updateTransport() {
    const known=state.track.positionKnown!==false;
    $('playButton').disabled=!actionAllowed('playPause');
    $('previousButton').disabled=!actionAllowed('previous');
    $('nextButton').disabled=!actionAllowed('next');
    $('seek').disabled=!(actionAllowed('seek') && known && state.track.duration>0);
    if($('seek').disabled && seeking){seeking=false;seekPreview=null;snapFollow=true;}
    $('playIcon').setAttribute('href', state.track.playing ? '#i-pause' : '#i-play');
    $('playButton').setAttribute('aria-label',state.track.playing ? '일시정지' : '재생');
    $('sessionLabel').textContent=hasSource()?(state.track.playing?'지금 재생 중':'일시정지'):'음악을 기다리는 중';
    updateTimeLabels(seeking?seekPreview:position());
    $('overlayAccess').setAttribute('aria-pressed',String(!!state.settings.overlay));
  }

  function updateTimeLabels(p) {
    const known=state.track.positionKnown!==false;
    $('positionLabel').textContent=known?formatTime(p):'—:—';
    $('durationLabel').textContent=known && state.track.duration>0?'−'+formatTime(Math.max(0,state.track.duration-p)):'—:—';
    const description=known && state.track.duration>0?formatTime(p)+' / '+formatTime(state.track.duration):'재생 위치 정보 없음';
    if($('seek').getAttribute('aria-valuetext')!==description)$('seek').setAttribute('aria-valuetext',description);
  }

  function updateVolume() {
    if(volumeSeeking)return;
    const v=state.volume||{},available=!!v.available && number(v.max)>0;
    const ratio=clamp(number(v.level)/Math.max(1,number(v.max)),0,1);
    $('volume').disabled=!available;$('volume').value=Math.round(ratio*1000);
    $('volume').style.setProperty('--volume-progress',ratio*100+'%');
    $('volume').setAttribute('aria-valuetext',available?Math.round(ratio*100)+'%':'기기에서 음량 조절을 제공하지 않아요');
  }

  function sendVolume(final=false) {
    if($('volume').disabled)return;
    const now=performance.now(),value=clamp(number($('volume').value),0,1000);
    $('volume').style.setProperty('--volume-progress',value/10+'%');
    $('volume').setAttribute('aria-valuetext',Math.round(value/10)+'%');
    if(final || now-lastVolumeSent>=60){lastVolumeSent=now;command('setVolume',{value:Math.round(value)});}
  }

  function toggleLyrics() {
    lyricsVisible=!lyricsVisible;$('app').classList.toggle('artwork-mode',!lyricsVisible);
    $('lyricsButton').classList.toggle('selected',lyricsVisible);$('lyricsButton').setAttribute('aria-pressed',String(lyricsVisible));
    $('collapseButton').setAttribute('aria-label',lyricsVisible?'앨범 화면으로 전환':'가사 화면으로 전환');
    $('lyricsViewport').setAttribute('aria-hidden',String(!lyricsVisible));
    flowLayoutDirty=true;manualUntil=0;currentAnchor=-1;scrollMotion=null;snapFollow=true;
  }

  function updateControlVisibility(now) {
    const mode=window.Appearance.get().controls;
    const hidden=lyricsVisible && lineModels.length>0 && !lastSheet && !seeking && !volumeSeeking && !keyboardNavigation && (mode==='minimal'?now>=controlsUntil:mode==='auto'&&state.track.playing&&now>=controlsUntil);
    if(hidden!==controlsHidden){controlsHidden=hidden;$('app').classList.toggle('controls-hidden',hidden);document.querySelector('.player').inert=hidden;document.querySelector('.player').setAttribute('aria-hidden',String(hidden));}
    $('controlsToggle').setAttribute('aria-expanded',String(!hidden));
    $('controlsToggle').setAttribute('aria-label',hidden?'재생 컨트롤 펼치기':'재생 컨트롤 숨기기');
    $('controlsToggle').hidden=mode==='always'||!lyricsVisible||!lineModels.length;
  }

  function updateSettings() {
    const s=state.settings;
    const size=clamp(number(s.fontSize,28),22,46);
    if(window.Appearance.apply(s.appearance)){flowLayoutDirty=true;currentAnchor=-1;scrollMotion=null;snapFollow=true;}
    document.documentElement.style.setProperty('--line-size',size+'px');
    $('offset').value=clamp(number(s.offset),-5000,5000);
    $('offsetValue').textContent=(number(s.offset)>0?'+':'')+number(s.offset)+' ms';
    $('fontSize').value=size; $('fontSizeValue').textContent=size;
    $('wordMode').value=String(s.wordMode || 'auto').toLowerCase();
    $('provider').value=String(s.provider || 'auto').toLowerCase();
    $('searchMode').value=s.searchMode || 'best';
    $('keepScreen').checked=!!s.keepScreen;
    $('notificationState').textContent=state.permissions.notifications ? (state.permissions.connected===false ? '연결 중' : '연결됨') : '연결';
    $('notificationState').classList.toggle('good',!!state.permissions.notifications);
    $('overlayState').textContent=state.permissions.overlay ? (s.overlay ? '사용 중' : '켜기') : '권한 설정';
    $('backgroundMode').value=s.backgroundMode||'sound';
    $('backgroundIntensity').value=number(s.backgroundIntensity,70);$('backgroundIntensityValue').textContent=number(s.backgroundIntensity,70)+'%';
    $('backgroundParticles').checked=s.backgroundParticles!==false;updateAudioSettings();
    $('widgetTheme').value=s.widgetTheme||'dark';$('widgetSize').value=s.widgetSize||'medium';$('widgetCover').checked=s.widgetCover!==false;
  }

  function audioFrame(input){
    let data;try{data=typeof input==='string'?JSON.parse(input):input;}catch(_){return;}
    if(!data||typeof data!=='object')return;state.audio=data;window.ReactiveAmbient.receive(data);updateAudioSettings();
  }
  function updateAudioSettings(){
    const preview=window.ReactiveAmbient.isPreview(),data=state.audio||{},mode=state.settings.backgroundMode||'sound';
    const signature=JSON.stringify([preview,data.status,data.source,data.connected,data.available,data.message,mode]);if(signature===audioStatusSignature)return;audioStatusSignature=signature;
    let title=preview?'효과 미리보기':mode==='static'?'고정 그라데이션':mode==='ambient'?'흐르는 그라데이션':data.status==='live'?'소리에 반응 중':data.status==='silent'?'소리 입력 대기':data.status==='connecting'?'소리 연결 중':data.connected?'일시정지':'소리 연결';
    $('audioQuickLabel').textContent=title;$('audioQuick').setAttribute('aria-label','사운드 반응 배경 · '+title);$('audioQuick').classList.toggle('connected',!!data.available||preview);
    $('audioConnectLabel').textContent=data.connected?'소리 연결 끊기':'재생 소리에 연결';
    $('audioStatus').textContent=preview?'미리보기 중':data.message||'연결 안 됨';
    $('audioCapture').disabled=data.status==='connecting';
  }

  function updateEmpty() {
    const hasLines=lineModels.length>0;
    $('lyricsViewport').hidden=!hasLines;
    $('emptyState').hidden=hasLines;
    if(hasLines)return;
    let title,action;
    const status=String(state.lyrics.status || '').toLowerCase();
    if(!state.permissions.notifications && !hasSource()) {
      title='음악 감지 연결';
      action='음악 감지';
    } else if(!hasSource() && state.permissions.connected===false) {
      title='음악 연결 중';action='다시 확인';
    } else if(!hasSource()) {
      title='음악 재생 대기';action='음악 앱 열기';
    } else if(state.lyrics.loading || status.includes('load') || status.includes('search')) {
      title='가사를 찾고 있어요';action='가사 검색';
    } else {
      title='가사 없음';action='가사 검색';
    }
    $('emptyTitle').textContent=title;
    $('emptyAction').textContent=action;
    $('emptyAction').dataset.action=!state.permissions.notifications&&!hasSource()?'permission':!hasSource()&&state.permissions.connected===false?'refresh':!hasSource()?'player':'search';
    $('emptySecondary').hidden=!hasSource();
    $('emptySecondary').textContent='가사 파일 불러오기';
  }

  function updateBadge() {
    const l=state.lyrics;
    const exact=lineModels.some(m=>m.realTiming),synced=lineModels.some(m=>m.start>=0);
    let label='가사 대기 중';
    if(lineModels.length) {
      label=(l.provider || '가사 파일')+' · '+(synced ? (exact ? '단어별 싱크' : '줄별 싱크') : '텍스트 가사');
      if(synced && state.track.positionKnown===false) label+=' · 재생 위치 대기';
      else if(!exact && state.settings.wordMode==='always' && synced) label+=' · 단어 시간 추정';
      if(l.masked)label+=' · 제공처 마스킹 포함';
    } else if(l.loading || /loading|searching/i.test(l.status || '')) label='가사를 찾는 중';
    else if(hasSource()) label='가사 없음 · 직접 찾아보세요';
    const badge=$('lyricsBadge');
    badge.replaceChildren(document.createElement('span'),document.createTextNode(label));
    badge.classList.toggle('exact',exact);
  }

  function buildLyrics() {
    const fragment=document.createDocumentFragment();
    const agents=[];
    state.lyrics.lines.forEach(l=>{if(l.agent && !agents.includes(l.agent))agents.push(l.agent);});
    lineModels=state.lyrics.lines.map((raw,index)=>{
      const start=number(raw.start,-1),end=number(raw.end,-1),text=String(raw.text || '');
      const realTiming=!!raw.realTiming;
      const line=document.createElement('button'); line.type='button';line.className='lyric-line';line.dataset.index=String(index);
      line.classList.toggle('bg',!!raw.bg);
      if(raw.agent) {
        if(raw.agent==='v1000' || /group/i.test(raw.agent))line.classList.add('agent-center');
        else if(agents.indexOf(raw.agent)%2===1)line.classList.add('agent-right');
      }
      const roman=String(raw.romanization || '');
      if(roman){const element=document.createElement('span');element.className='romanization';element.textContent=roman;line.append(element);}
      const main=document.createElement('span');main.className='line-main';
      let words=Array.isArray(raw.words) ? raw.words.map(w=>({start:number(w.start,start),end:number(w.end,end),text:String(w.text || '')})) : [];
      const allowWordMode=state.settings.wordMode!=='never' && (realTiming || state.settings.wordMode==='always');
      if(allowWordMode && !words.length && start>=0 && end>start && state.settings.wordMode==='always') {
        const chars=Array.from(text),duration=end-start;
        words=chars.map((char,i)=>({start:start+duration*i/chars.length,end:start+duration*(i+1)/chars.length,text:char}));
      }
      const wordNodes=[];
      if(allowWordMode && words.length) {
        for(const word of words){const span=document.createElement('span');span.className='lyric-word';span.textContent=word.text;main.append(span);wordNodes.push({el:span,...word,progress:-1});}
        // Preserve native plain text when imported timing omits punctuation or spaces.
        if(words.map(w=>w.text).join('')!==text) {
          main.replaceChildren(document.createTextNode(text));wordNodes.length=0;
        }
      } else if(text.trim()) main.textContent=text;
      else {const gap=document.createElement('span');gap.className='line-gap';gap.setAttribute('aria-label','간주');for(let j=0;j<3;j++)gap.append(document.createElement('i'));main.append(gap);}
      line.append(main);
      if(raw.translation){const element=document.createElement('span');element.className='translation';element.textContent=String(raw.translation);line.append(element);}
      line.setAttribute('aria-label',text ? text+(raw.translation?' · '+raw.translation:'') : '간주');
      fragment.append(line);
      return {el:line,start,end,text,bg:!!raw.bg,realTiming,words:wordNodes,index,active:false};
    });
    $('lyricsList').replaceChildren(fragment);
    const timed=lineModels.filter(l=>l.start>=0),main=timed.filter(l=>!l.bg);
    flowGroups=[];
    for(const line of (main.length?main:timed).sort((a,b)=>a.start-b.start||a.index-b.index)){
      let group=flowGroups[flowGroups.length-1];
      if(!group || group.start!==line.start){group={start:line.start,end:line.end,index:line.index,lines:[]};flowGroups.push(group);}
      group.end=Math.max(group.end,line.end);group.lines.push(line);
    }
    currentAnchor=-1;priorActive='';scrollMotion=null;lastVisual=0;flowLayoutDirty=true;snapFollow=true;
  }

  function flowIndex(p) {
    if(!flowGroups.length || state.track.positionKnown===false)return -1;
    let low=0,high=flowGroups.length;
    while(low<high){const mid=(low+high)>>>1;if(flowGroups[mid].start<=p)low=mid+1;else high=mid;}
    return Math.max(0,low-1);
  }

  function selectAnchor(p) {
    const index=flowIndex(p);return index<0?-1:flowGroups[index].index;
  }

  function measureFlow() {
    const viewport=$('lyricsViewport');if(viewport.hidden || !viewport.clientHeight)return;
    for(const group of flowGroups){
      const top=Math.min(...group.lines.map(l=>l.el.offsetTop));
      const bottom=Math.max(...group.lines.map(l=>l.el.offsetTop+l.el.offsetHeight));
      // Start a wrapped verse near its first rows, then read toward its end.
      group.focus=top+Math.min((bottom-top)/2,viewport.clientHeight*.25);
      group.tail=bottom-Math.min((bottom-top)/2,viewport.clientHeight*.25);
    }
    flowLayoutDirty=false;
  }

  function flowTarget(p) {
    const viewport=$('lyricsViewport'),index=flowIndex(p);
    if(index<0)return null;
    const group=flowGroups[index],next=flowGroups[index+1];let focus=group.focus;
    if(!reducedMotion && window.Appearance.get().flow==='continuous'){
      if(next){
        const gap=next.start-Math.max(group.start,group.end);
        // A long instrumental holds the previous verse until the next entrance.
        const from=gap>1200?Math.max(group.start,next.start-650):group.start;
        const progress=clamp((p-from)/Math.max(1,next.start-from),0,1);
        focus+=(next.focus-group.focus)*progress;
      }else{
        focus+=(group.tail-group.focus)*clamp((p-group.start)/Math.max(1,group.end-group.start),0,1);
      }
    }
    return clamp(focus-viewport.clientHeight*(window.Appearance.get().focusPosition/100),0,Math.max(0,viewport.scrollHeight-viewport.clientHeight));
  }

  function scrollToAnchor(anchor, immediate=false) {
    if(anchor<0)return;
    snapFollow=immediate || reducedMotion;
    scrollMotion=snapFollow?null:{from:$('lyricsViewport').scrollTop,at:performance.now(),duration:450};
  }

  function seekTo(p) {
    if(!actionAllowed('seek') || state.track.positionKnown===false)return;
    const target=clamp(number(p),0,state.track.duration>0?state.track.duration:86400000),now=performance.now();
    seeking=false;seekPreview=null;manualUntil=0;scrollMotion=null;snapFollow=true;currentAnchor=-1;lastVisual=0;
    clock={base:target,at:now,playing:!!state.track.playing,speed:number(state.track.speed,1),correction:0};
    pendingSeek=native?{position:target,at:now}:null;
    command('transport',{action:'seek',position:Math.round(target)});
  }

  function frame(now) {
    if(document.visibilityState!=='hidden') {
      const p=seeking && seekPreview!==null?seekPreview:position(now),lp=p+number(state.settings.offset),anchor=selectAnchor(lp);
      if(flowLayoutDirty)measureFlow();
      if(currentAnchor<0 && anchor>=0)snapFollow=true;
      if(anchor!==currentAnchor && window.Appearance.get().flow==='line' && !snapFollow)scrollToAnchor(anchor);
      currentAnchor=anchor;
      if(manualUntil && now>=manualUntil && !dragging){manualUntil=0;scrollToAnchor(anchor);}
      const browsing=dragging || manualUntil>now;
      $('followButton').hidden=!(browsing && lineModels.length);
      if(!browsing && !flowLayoutDirty){
        let target=flowTarget(lp);
        if(target!==null){
          if(snapFollow || seeking){scrollMotion=null;snapFollow=false;}
          else if(scrollMotion){
            const t=clamp((now-scrollMotion.at)/scrollMotion.duration,0,1);
            target=scrollMotion.from+(target-scrollMotion.from)*(1-Math.pow(1-t,3));
            if(t===1)scrollMotion=null;
          }
          if(Math.abs($('lyricsViewport').scrollTop-target)>.25)$('lyricsViewport').scrollTop=target;
        }
      }
      if(now-lastVisual>=75) {
        lastVisual=now;const active=[];
        for(const l of lineModels)if(l.start>=0 && lp>=l.start && lp<l.end && state.track.positionKnown!==false)active.push(l.index);
        const signature=active.join(',')+'|'+anchor+'|'+browsing;
        if(signature!==priorActive) {
          priorActive=signature;
          $('lyricsViewport').classList.toggle('exploring',browsing);
          for(const l of lineModels) {
            const isActive=active.includes(l.index),distance=Math.abs(l.index-anchor);l.active=isActive;
            l.el.classList.toggle('current',isActive);
            l.el.classList.toggle('played',!isActive && l.start>=0 && lp>=l.end);
            l.el.classList.toggle('upcoming',!isActive && distance<3 && l.start>lp);
            l.el.classList.toggle('far',!isActive && distance>3 && !browsing);
            l.el.setAttribute('aria-current',isActive?'true':'false');
          }
        }
        if(!seeking) {
          const ratio=state.track.duration>0?clamp(p/state.track.duration,0,1):0;
          $('seek').value=Math.round(ratio*1000);$('seek').style.setProperty('--seek-progress',ratio*100+'%');
          updateTimeLabels(p);
        }
      }
      for(const l of lineModels) {
        if(!l.words.length || !l.active && (l.index<anchor-2 || l.index>anchor+5))continue;
        for(const w of l.words) {
          const progress=clamp((lp-w.start)/Math.max(1,w.end-w.start),0,1);
          const rounded=Math.round(progress*1000)/10;
          if(rounded!==w.progress){w.el.style.setProperty('--progress',rounded+'%');w.progress=rounded;}
          w.el.classList.toggle('long-note',l.realTiming && w.end-w.start>=700 && lp>=w.start && lp<w.end);
        }
      }
      if(seeking)updateTimeLabels(p);
      updateControlVisibility(now);
      paintAmbient(now);
    }
    requestAnimationFrame(frame);
  }

  function command(action,data={}) {
    if(!native)return demoCommand(action,data);
    try { window.Android.command(action,JSON.stringify(data)); }
    catch (_) { toast('요청을 전달하지 못했어요. 앱을 다시 열어 주세요.'); }
  }

  function setSetting(key,value) {
    state.settings[key]=value;updateSettings();updateBadge();
    if(key==='wordMode')buildLyrics();
    if(key==='fontSize' || key==='offset'){currentAnchor=-1;snapFollow=true;if(key==='fontSize')flowLayoutDirty=true;}
    command('setSetting',{key,value});
  }

  function toast(message) {
    clearTimeout(toastTimer);$('toast').textContent=String(message);$('toast').hidden=false;
    toastTimer=setTimeout(()=>{$('toast').hidden=true;},3300);
  }

  function openSheet(id) {
    lastFocus=document.activeElement;$('app').inert=true;
    document.body.classList.toggle('appearance-open',id==='appearanceSheet');
    for(const name of ['settingsSheet','searchSheet','sourcesSheet','appearanceSheet','playlistSheet'])$(name).hidden=name!==id;
    $('sheetBackdrop').hidden=false;document.body.classList.add('sheet-open');lastSheet=id;
    if(id==='searchSheet') {
      $('submitSearch').disabled=false;$('submitSearch').textContent='검색';
      $('searchTrack').value=state.track.title || '';$('searchArtist').value=state.track.artist || '';
      $('searchStatus').textContent='';$('searchResults').replaceChildren();
    }
    if(id==='sourcesSheet')renderSources();
    if(id==='playlistSheet'){showPlaylistTab(playlistTab);command('getPlaylists',{id:selectedLocal});}
    $(id).querySelector('button').focus({preventScroll:true});
  }

  function closeSheet() {
    if(!lastSheet)return false;$('app').inert=false;
    $(lastSheet).hidden=true;lastSheet=null;$('sheetBackdrop').hidden=true;document.body.classList.remove('sheet-open','appearance-open');
    controlsUntil=performance.now()+3500;flowLayoutDirty=true;snapFollow=true;
    const focusTarget=lastFocus&&lastFocus.getClientRects().length?lastFocus:$('settingsButton');
    focusTarget.focus({preventScroll:true});return true;
  }

  function renderSources() {
    const root=$('sourcesList');root.replaceChildren();
    const all=[{packageName:'',name:'자동 선택',playing:false},...state.sources];
    for(const s of all) {
      const b=document.createElement('button');b.className='source-item';
      const icon=document.createElement('span');icon.className='source-avatar';icon.textContent=s.packageName?String(s.name || '?').slice(0,1):'↻';
      const text=document.createElement('div'),title=document.createElement('strong'),small=document.createElement('small');
      title.textContent=s.name || s.packageName;small.textContent=s.packageName?(s.playing?'재생 중':'일시정지'):'';text.append(title,small);b.append(icon,text);
      if(s.packageName && s.packageName===state.track.packageName){const check=document.createElement('span');check.className='playing-tag';check.textContent='현재 연결';b.append(check);}
      b.addEventListener('click',()=>{command('selectSource',{packageName:s.packageName});closeSheet();});root.append(b);
    }
  }

  function showPlaylistTab(tab){
    playlistTab=tab==='spotify'?'spotify':'local';
    $('localPanel').hidden=playlistTab!=='local';$('spotifyPanel').hidden=playlistTab!=='spotify';
    $('localTab').setAttribute('aria-selected',String(playlistTab==='local'));$('spotifyTab').setAttribute('aria-selected',String(playlistTab==='spotify'));
    if(playlistTab==='spotify'){spotify({view:'loading'});command('spotifyPlaylists');}
  }

  function playlists(input){
    let data;try{data=typeof input==='string'?JSON.parse(input):input;}catch(_){return;}
    if(!data||typeof data!=='object')return;
    const items=Array.isArray(data.items)?data.items:[],selected=data.selected&&Array.isArray(data.selected.tracks)?data.selected:null;
    const previous=selectedLocal;selectedLocal=selected?String(selected.id):'';if(previous!==selectedLocal){$('playlistSearch').value='';localVisible=100;}
    localTracks=selected?selected.tracks:[];pendingTrackRemoval=-1;$('trackRemoveConfirm').hidden=true;
    $('playlistOrder').value=['all','one','shuffle'].includes(data.mode)?data.mode:'all';
    $('localStop').hidden=!selectedLocal||data.activeId!==selectedLocal;
    $('localBrowse').hidden=!!selected;$('localDetail').hidden=!selected;$('playlistRemoveConfirm').hidden=true;
    $('localStatus').textContent=items.length?'':'저장된 플레이리스트가 없습니다.';
    const root=$('playlistList');root.replaceChildren();
    for(const item of items){
      const button=document.createElement('button'),box=document.createElement('div'),title=document.createElement('strong'),sub=document.createElement('small'),arrow=document.createElement('span');
      button.className='playlist-item';title.textContent=String(item.name||'재생목록');sub.textContent=number(item.count)+'곡';arrow.textContent='›';arrow.setAttribute('aria-hidden','true');
      box.append(title,sub);button.append(box,arrow);button.addEventListener('click',()=>command('getPlaylists',{id:String(item.id)}));root.append(button);
    }
    if(!selected)return;
    $('playlistName').textContent=String(selected.name||'재생목록');
    renderLocalTracks();
  }

  function renderLocalTracks(){
    const tracks=$('playlistTracks'),query=$('playlistSearch').value.trim().toLocaleLowerCase();tracks.replaceChildren();
    let matches=0;
    localTracks.forEach((item,index)=>{
      if(query&&![item.title,item.artist,item.album].some(value=>String(value||'').toLocaleLowerCase().includes(query)))return;
      if(matches++>=localVisible)return;
      const row=document.createElement('div'),button=document.createElement('button'),remove=document.createElement('button'),position=document.createElement('span'),box=document.createElement('div'),title=document.createElement('strong'),sub=document.createElement('small');
      row.className='playlist-track-row';button.className='playlist-track';remove.className='playlist-track-remove';position.textContent=String(index+1);position.setAttribute('aria-hidden','true');title.textContent=String(item.title||'제목 없음');sub.textContent=[item.artist,item.album].filter(Boolean).join(' · ');
      box.append(title,sub);button.append(position,box);button.addEventListener('click',()=>{command('playLocal',{id:selectedLocal,index});closeSheet();});
      remove.textContent='빼기';remove.setAttribute('aria-label',String(item.title||'곡')+' 재생목록에서 빼기');remove.addEventListener('click',()=>{pendingTrackRemoval=index;$('trackRemoveName').textContent=String(item.title||'곡')+' 빼기';$('trackRemoveConfirm').hidden=false;$('trackRemoveYes').focus();});
      row.append(button,remove);tracks.append(row);
    });
    if(matches>localVisible){const more=document.createElement('button');more.className='secondary-button';more.textContent='더 보기';more.addEventListener('click',()=>{localVisible+=100;renderLocalTracks();});tracks.append(more);}
  }

  function spotify(input){
    let data;try{data=typeof input==='string'?JSON.parse(input):input;}catch(_){return;}
    if(!data||typeof data!=='object')return;
    const root=$('spotifyContent');root.replaceChildren();const view=String(data.view||'disconnected');
    const button=(label,action,secondary=true)=>{const el=document.createElement('button');el.className=secondary?'secondary-button':'mini-button';el.textContent=label;el.addEventListener('click',action);return el;};
    const status=message=>{const el=document.createElement('p');el.className='playlist-status';el.setAttribute('role','status');el.textContent=message;root.append(el);};
    const open=(type,id)=>command('spotifyOpen',{type,id});
    if(view==='loading'){status('불러오는 중');return;}
    if(view==='connecting'){status('Spotify 연결 중');root.append(button('연결 취소',()=>command('spotifyDisconnect')));return;}
    if(view==='disconnected'){root.append(button('Spotify 연결',()=>{spotify({view:'connecting'});command('spotifyConnect');}));return;}
    if(view==='error'){
      status(String(data.message||'Spotify에 연결하지 못했습니다.'));
      root.append(button(data.connected?'다시 불러오기':'다시 연결',()=>command(data.connected?'spotifyPlaylists':'spotifyConnect')));
    }else if(view==='lists'){
      const items=Array.isArray(data.items)?data.items:[];if(!items.length)status('저장된 플레이리스트가 없습니다.');
      for(const item of items){
        const row=document.createElement('div'),itemButton=document.createElement('button'),box=document.createElement('div'),title=document.createElement('strong'),sub=document.createElement('small'),external=button('Spotify에서 열기',()=>open('playlist',String(item.id)),false);
        row.className='spotify-row';itemButton.className='playlist-item';external.className='spotify-open';
        title.textContent=String(item.name||'재생목록');sub.textContent=[item.owner,number(item.count,-1)>=0?number(item.count)+'곡':''].filter(Boolean).join(' · ');box.append(title,sub);itemButton.append(box);
        itemButton.addEventListener('click',()=>{status('불러오는 중');command('spotifyTracks',{id:String(item.id),name:String(item.name||'재생목록')});});row.append(itemButton,external);root.append(row);
      }
    }else if(view==='tracks'||view==='tracksError'){
      if(view==='tracks'){
        if(number(data.offset)===0||spotifyListId!==String(data.id)){spotifyItems=[];spotifyListId=String(data.id);}
        spotifyItems.push(...(Array.isArray(data.items)?data.items:[]));
      }else if(spotifyListId!==String(data.id)||number(data.offset)===0){spotifyItems=[];spotifyListId=String(data.id);}
      const head=document.createElement('div'),name=document.createElement('h3');head.className='spotify-header';
      name.textContent=String(data.name||'재생목록');head.append(button('‹ 목록',()=>command('spotifyPlaylists'),false),name,button('Spotify에서 열기',()=>open('playlist',String(data.id)),false));root.append(head);
      if(view==='tracksError')status(String(data.message||'재생목록을 불러오지 못했습니다.'));
      if(view==='tracks'&&!spotifyItems.length&&!data.more)status('곡이 없습니다.');
      spotifyItems.forEach((item,index)=>{
        const row=document.createElement('button'),position=document.createElement('span'),box=document.createElement('div'),title=document.createElement('strong'),sub=document.createElement('small');
        row.className='playlist-track';position.textContent=String(index+1);position.setAttribute('aria-hidden','true');title.textContent=String(item.title||'제목 없음');sub.textContent=String(item.artist||'');box.append(title,sub);row.append(position,box);
        const type=item.type==='episode'?'episode':'track';row.disabled=!item.id;row.addEventListener('click',()=>open(type,String(item.id)));root.append(row);
      });
      if(data.more||view==='tracksError'&&number(data.offset)>0){const more=button(view==='tracksError'?'다시 시도':'더 보기',()=>{more.disabled=true;command('spotifyTracks',{id:String(data.id),name:String(data.name||''),offset:view==='tracksError'?number(data.offset):number(data.nextOffset)});});root.append(more);}
    }
    if(data.connected){const disconnect=button('Spotify 연결 해제',()=>command('spotifyDisconnect'));disconnect.classList.add('spotify-logout');root.append(disconnect);}
  }

  function searchResults(input) {
    let data;try{data=typeof input==='string'?JSON.parse(input):input;}catch(_){return;}
    $('submitSearch').disabled=false;$('submitSearch').textContent='검색';
    const items=data&&Array.isArray(data.items)?data.items:[];
    $('searchStatus').textContent=data&&data.error?String(data.error):items.length?items.length+'개의 가사를 찾았어요. 맞는 곡을 선택해 주세요.':'검색 결과가 없어요. 제목이나 가수 이름을 바꿔 보세요.';
    const root=$('searchResults');root.replaceChildren();
    for(const item of items) {
      const button=document.createElement('button');button.className='search-result';
      const detail=document.createElement('div'),title=document.createElement('strong'),subtitle=document.createElement('small'),meta=document.createElement('span');
      title.textContent=String(item.title || '제목 없음');subtitle.textContent=[item.artist,item.album,item.provider].filter(Boolean).join(' · ');
      meta.textContent=(item.duration>0?formatTime(item.duration):'—:—')+(number(item.score)>0?'\n'+item.score+'%':'');meta.style.whiteSpace='pre-line';meta.style.lineHeight='1.8';detail.append(title,subtitle);button.append(detail,meta);
      button.addEventListener('click',()=>{command('chooseResult',{id:item.id});closeSheet();});root.append(button);
    }
  }

  function attachEvents() {
    window.Appearance.mount(next=>{state.settings.appearance=next;updateSettings();command('setAppearance',{appearance:next});});
    $('appearanceButton').addEventListener('click',()=>openSheet('appearanceSheet'));
    $('controlsToggle').addEventListener('click',()=>{controlsUntil=controlsHidden?performance.now()+6000:0;updateControlVisibility(performance.now());});
    document.addEventListener('pointerdown',()=>{keyboardNavigation=false;if(window.Appearance.get().controls==='auto')controlsUntil=performance.now()+3500;},{passive:true});
    document.addEventListener('pointermove',e=>{if(e.pointerType==='mouse'&&window.Appearance.get().controls==='auto')controlsUntil=performance.now()+3500;},{passive:true});
    document.addEventListener('keydown',e=>{if(e.key==='Tab'){keyboardNavigation=true;controlsUntil=performance.now()+3500;}});
    if(window.ResizeObserver)new ResizeObserver(()=>{flowLayoutDirty=true;snapFollow=true;}).observe($('lyricsViewport'));
    $('settingsButton').addEventListener('click',()=>openSheet('settingsSheet'));
    $('addWidget').addEventListener('click',()=>command('addWidget'));
    for(const id of ['widgetTheme','widgetSize'])$(id).addEventListener('change',()=>setSetting(id,$(id).value));
    $('widgetCover').addEventListener('change',()=>setSetting('widgetCover',$('widgetCover').checked));
    $('sourceButton').addEventListener('click',()=>openSheet('sourcesSheet'));
    $('playlistButton').addEventListener('click',()=>openSheet('playlistSheet'));
    $('localTab').addEventListener('click',()=>showPlaylistTab('local'));
    $('spotifyTab').addEventListener('click',()=>showPlaylistTab('spotify'));
    $('importMusic').addEventListener('click',()=>command('importMusic',{id:selectedLocal}));
    $('importPlaylist').addEventListener('click',()=>command('importPlaylist'));
    $('playlistBack').addEventListener('click',()=>{$('playlistSearch').value='';command('getPlaylists',{id:''});});
    $('playlistSearch').addEventListener('input',()=>{localVisible=100;renderLocalTracks();});
    $('trackRemoveNo').addEventListener('click',()=>{$('trackRemoveConfirm').hidden=true;pendingTrackRemoval=-1;});
    $('trackRemoveYes').addEventListener('click',()=>{if(pendingTrackRemoval>=0)command('removeLocalTrack',{id:selectedLocal,index:pendingTrackRemoval});});
    $('playlistOrder').addEventListener('change',()=>command('localMode',{id:selectedLocal,mode:$('playlistOrder').value}));
    $('localStop').addEventListener('click',()=>command('stopLocal',{id:selectedLocal}));
    $('playlistRemove').addEventListener('click',()=>{$('playlistRemoveConfirm').hidden=false;$('playlistRemoveYes').focus();});
    $('playlistRemoveNo').addEventListener('click',()=>{$('playlistRemoveConfirm').hidden=true;$('playlistRemove').focus();});
    $('playlistRemoveYes').addEventListener('click',()=>command('removePlaylist',{id:selectedLocal}));
    $('searchButton').addEventListener('click',()=>openSheet('searchSheet'));
    $('emptyAction').addEventListener('click',()=>{const a=$('emptyAction').dataset.action;if(a==='permission')command('notificationAccess');else if(a==='player')command('openPlayer');else if(a==='refresh')command('refresh');else openSheet('searchSheet');});
    $('emptySecondary').addEventListener('click',()=>hasSource()?command('importLyrics'):openSheet('searchSheet'));
    $('notificationAccess').addEventListener('click',()=>command('notificationAccess'));
    $('overlayAccess').addEventListener('click',()=>command(state.permissions.overlay?'toggleOverlay':'overlayPermission'));
    $('openPlayer').addEventListener('click',()=>command('openPlayer'));
    $('settingsImport').addEventListener('click',()=>command('importLyrics'));
    $('lyricsButton').addEventListener('click',toggleLyrics);$('collapseButton').addEventListener('click',toggleLyrics);
    $('volume').addEventListener('input',()=>{volumeSeeking=true;sendVolume();});
    $('volume').addEventListener('change',()=>{sendVolume(true);volumeSeeking=false;});
    $('volume').addEventListener('pointercancel',()=>{volumeSeeking=false;updateVolume();});
    $('retryLyrics').addEventListener('click',()=>{command('refresh');closeSheet();});
    $('clearCache').addEventListener('click',()=>command('clearCache'));
    $('about').addEventListener('click',()=>command('about'));
    $('sourceRefresh').addEventListener('click',()=>command('refresh'));
    $('offset').addEventListener('input',()=>setSetting('offset',number($('offset').value)));
    $('offsetMinus').addEventListener('click',()=>setSetting('offset',clamp(number(state.settings.offset)-250,-5000,5000)));
    $('offsetPlus').addEventListener('click',()=>setSetting('offset',clamp(number(state.settings.offset)+250,-5000,5000)));
    $('resetOffset').addEventListener('click',()=>setSetting('offset',0));
    $('fontSize').addEventListener('input',()=>setSetting('fontSize',number($('fontSize').value)));
    for(const id of ['wordMode','provider','searchMode'])$(id).addEventListener('change',()=>setSetting(id,$(id).value));
    $('backgroundMode').addEventListener('change',()=>setSetting('backgroundMode',$('backgroundMode').value));
    $('backgroundIntensity').addEventListener('input',()=>setSetting('backgroundIntensity',number($('backgroundIntensity').value)));
    $('backgroundParticles').addEventListener('change',()=>setSetting('backgroundParticles',$('backgroundParticles').checked));
    for(const id of ['audioQuick','audioConnect'])$(id).addEventListener('click',()=>command('audioConnect'));
    $('audioCapture').addEventListener('click',()=>command('audioCapture'));
    $('backgroundPreview').addEventListener('click',()=>{window.ReactiveAmbient.preview();wasPreview=true;updateAudioSettings();});
    $('keepScreen').addEventListener('change',()=>setSetting('keepScreen',$('keepScreen').checked));
    $('playButton').addEventListener('click',()=>command('transport',{action:'playPause'}));
    $('nextButton').addEventListener('click',()=>command('transport',{action:'next'}));
    $('previousButton').addEventListener('click',()=>command('transport',{action:'previous'}));
    $('seek').addEventListener('input',()=>{
      if($('seek').disabled)return;
      seeking=true;seekPreview=number($('seek').value)/1000*state.track.duration;
      manualUntil=0;scrollMotion=null;snapFollow=true;lastVisual=0;
      $('positionLabel').textContent=formatTime(seekPreview);$('seek').style.setProperty('--seek-progress',$('seek').value/10+'%');
    });
    $('seek').addEventListener('change',()=>seekTo(number($('seek').value)/1000*state.track.duration));
    $('seek').addEventListener('pointercancel',()=>{seeking=false;seekPreview=null;snapFollow=true;lastVisual=0;});
    $('lyricsViewport').addEventListener('pointerdown',e=>{pointerStart={x:e.clientX,y:e.clientY};dragging=false;},{passive:true});
    $('lyricsViewport').addEventListener('pointermove',e=>{if(pointerStart&&Math.abs(e.clientY-pointerStart.y)+Math.abs(e.clientX-pointerStart.x)>9){dragging=true;manualUntil=performance.now()+3000;scrollMotion=null;suppressClickUntil=performance.now()+350;}},{passive:true});
    const endDrag=()=>{pointerStart=null;if(dragging){manualUntil=performance.now()+3000;suppressClickUntil=performance.now()+350;}dragging=false;};
    window.addEventListener('pointerup',endDrag,{passive:true});window.addEventListener('pointercancel',endDrag,{passive:true});
    $('lyricsViewport').addEventListener('wheel',()=>{manualUntil=performance.now()+3000;scrollMotion=null;},{passive:true});
    $('lyricsViewport').addEventListener('keydown',e=>{if(['ArrowUp','ArrowDown','PageUp','PageDown','Home','End'].includes(e.key)){manualUntil=performance.now()+3000;scrollMotion=null;}});
    $('lyricsList').addEventListener('click',e=>{
      const button=e.target.closest('.lyric-line');if(!button || performance.now()<suppressClickUntil)return;
      const line=lineModels[number(button.dataset.index,-1)];
      if(line && line.start>=0)seekTo(line.start-number(state.settings.offset));
    });
    $('followButton').addEventListener('click',()=>{manualUntil=0;scrollToAnchor(currentAnchor);});
    $('sheetBackdrop').addEventListener('click',closeSheet);
    document.querySelectorAll('.close-sheet').forEach(b=>b.addEventListener('click',closeSheet));
    document.addEventListener('keydown',e=>{if(e.key==='Escape'){closeSheet();return;}if(e.key==='Tab'&&lastSheet){const nodes=Array.from($(lastSheet).querySelectorAll('button,input,select,[tabindex]')).filter(n=>!n.disabled&&!n.hidden&&n.getClientRects().length),first=nodes[0],last=nodes[nodes.length-1];if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}}});
    $('searchForm').addEventListener('submit',e=>{e.preventDefault();const title=$('searchTrack').value.trim(),artist=$('searchArtist').value.trim();if(!title){$('searchTrack').focus();return;}$('searchResults').replaceChildren();$('searchStatus').textContent='가사를 검색하고 있어요…';$('submitSearch').disabled=true;$('submitSearch').textContent='검색 중…';command('search',{title,artist});});
    window.addEventListener('resize',()=>{currentAnchor=-1;flowLayoutDirty=true;resizeAmbient();});
    if(document.fonts && document.fonts.ready){const changed=()=>{flowLayoutDirty=true;currentAnchor=-1;};document.fonts.ready.then(changed);if(document.fonts.addEventListener)document.fonts.addEventListener('loadingdone',changed);}
    document.addEventListener('visibilitychange',()=>{if(!document.hidden && native){try{receive(window.Android.getBootstrap());}catch(_){}if(lastSheet==='playlistSheet'&&playlistTab==='spotify')command('spotifyPlaylists');}});
  }

  function initAmbient(){window.ReactiveAmbient.init();}
  function resizeAmbient(){window.ReactiveAmbient.resize();}
  function paintAmbient(now){window.ReactiveAmbient.frame(now,state.track.playing,state.settings);if(wasPreview&&!window.ReactiveAmbient.isPreview()){wasPreview=false;updateAudioSettings();}}
  function extractPalette(img){window.ReactiveAmbient.extractPalette(img);}

  function demoState() {
    const rows=[['창문 너머 번지는 빛',0,6000],['잠깐, 이 순간을 들어봐',6000,12000],['흘러가는 하루 사이',12000,19000],['네가 좋아하는 리듬으로',19000,26000],['작은 숨을 고르고',26000,32000],['다시 우리 속도로 걸어',32000,39000],['오래 남을 이 멜로디',39000,46000],['오늘의 색을 기억해',46000,53000]];
    return {track:{key:'browser-demo',title:'우리의 속도',artist:'BetterLyrics · 화면 데모',album:'Original demo / no audio',duration:53000,position:demoPosition,playing:demoPlaying,speed:1,positionKnown:true,appName:'미리보기',packageName:'demo'},lyrics:{status:'ready',provider:'직접 만든 데모',score:100,synced:true,lines:rows.map(([text,start,end],i)=>({text,start,end,realTiming:true,agent:i%4===3?'v2':'v1',bg:false,translation:i===2?'하루의 빈틈을 채우는 노래':'',romanization:'',words:text.split(' ').map((word,j,all)=>({text:word+(j<all.length-1?' ':''),start:start+(end-start)*j/all.length,end:start+(end-start)*(j+1)/all.length}))}))},volume:{available:true,level:6,max:15},permissions:{notifications:true,overlay:false},sources:[{packageName:'demo',name:'브라우저 데모',playing:demoPlaying}]};
  }
  function demoCommand(action,data){
    if(action==='setVolume'){state.volume={available:true,max:1000,level:number(data.value)};updateVolume();}
    else if(action==='transport'){
      demoPosition=position();demoTime=performance.now();
      if(data.action==='playPause')demoPlaying=!demoPlaying;
      if(data.action==='seek')demoPosition=number(data.position);
      if(data.action==='next'||data.action==='previous')demoPosition=data.action==='next'?19000:0;
      receive({tick:true,track:{key:'browser-demo',position:demoPosition,playing:demoPlaying,speed:1,positionKnown:true}});
    } else if(action==='getPlaylists')playlists({items:[],selected:{},mode:'all'});
    else if(action==='spotifyPlaylists')spotify({view:'disconnected'});
    else if(action==='search')setTimeout(()=>searchResults({items:[],error:'브라우저 데모에서는 온라인 검색을 실행하지 않아요.'}),500);
    else if(action==='refresh')toast('브라우저 미리보기입니다. 실제 음악 연결은 Android 앱에서 사용해 주세요.');
    else if(action==='setAppearance'){try{localStorage.setItem('betterlyrics.appearance.v1',JSON.stringify(data.appearance));}catch(_){}}
    else if(action!=='setSetting')toast('Android 앱에서 사용할 수 있는 기능이에요.');
  }

  window.App={receive,audio:audioFrame,searchResults,playlists,spotify,toast,back:closeSheet,handleBack:closeSheet,position:()=>position(),getState:()=>state};
  attachEvents();initAmbient();
  if(native){try{receive(window.Android.getBootstrap());}catch(_){updateSettings();updateEmpty();toast('음악 연결 정보를 읽지 못했어요. 설정에서 음악 감지를 확인해 주세요.');}}
  else {document.body.classList.add('demo-mode');$('demoBanner').hidden=false;const demo=demoState();try{demo.settings={appearance:window.Appearance.sanitize(JSON.parse(localStorage.getItem('betterlyrics.appearance.v1')||'null'))};}catch(_){}receive(demo);}
  requestAnimationFrame(frame);
})();
