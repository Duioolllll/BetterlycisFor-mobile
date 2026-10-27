'use strict';

// Persist only this versioned, bounded visual schema; never arbitrary CSS/HTML.
window.Appearance = (() => {
  const defaults = Object.freeze({version:1,preset:'original',layout:'auto',align:'left',font:'system',weight:700,lineHeight:135,lineGap:18,opacity:42,blur:1,brightness:85,coverSize:100,lyricWidth:90,focusPosition:50,controls:'auto',showCover:true,showInfo:true,showTranslation:true,showRomanization:false,glow:true,flow:'continuous',textColor:'#ffffff'});
  const ranges={weight:[400,900],lineHeight:[110,180],lineGap:[4,40],opacity:[15,80],blur:[0,5],brightness:[25,100],coverSize:[60,130],lyricWidth:[60,100],focusPosition:[30,65]};
  const choices={preset:['original','focus','album','custom'],layout:['auto','immersive','split','reverse'],align:['left','center','right'],font:['system','serif','rounded'],controls:['auto','always','minimal'],flow:['continuous','line']};
  const presets={original:{},focus:{layout:'immersive',align:'center',weight:750,lineGap:26,opacity:25,blur:2.5,lyricWidth:94,controls:'auto'},album:{layout:'split',align:'left',coverSize:125,opacity:35,blur:1.5,controls:'always'}};
  let current={...defaults},onChange=()=>{},signature='';
  const $=id=>document.getElementById(id);
  function sanitize(input){
    const s=input&&typeof input==='object'&&!Array.isArray(input)?input:{},out={...defaults};
    for(const [key,[min,max]] of Object.entries(ranges))if(typeof s[key]==='number'&&Number.isFinite(s[key]))out[key]=Math.max(min,Math.min(max,s[key]));
    for(const [key,values] of Object.entries(choices))if(values.includes(s[key]))out[key]=s[key];
    for(const key of ['showCover','showInfo','showTranslation','showRomanization','glow'])if(typeof s[key]==='boolean')out[key]=s[key];
    if(typeof s.textColor==='string'&&/^#[0-9a-f]{6}$/i.test(s.textColor))out.textColor=s.textColor.toLowerCase();
    return out;
  }
  function apply(input){
    current=sanitize(input);const next=JSON.stringify(current),changed=next!==signature;signature=next;
    const root=document.documentElement,app=$('app');
    for(const key of ['layout','align','controls','font'])app.dataset[key]=current[key];
    for(const key of ['showCover','showInfo','showTranslation','showRomanization','glow'])app.classList.toggle('without-'+key,!current[key]);
    const values={'--lyric-weight':current.weight,'--lyric-height':current.lineHeight/100,'--lyric-gap':current.lineGap+'px','--lyric-opacity':current.opacity/100,'--lyric-blur':current.blur+'px','--background-brightness':current.brightness/100,'--cover-scale':current.coverSize/100,'--lyric-width':current.lyricWidth+'%','--lyric-color':current.textColor};
    for(const [key,value] of Object.entries(values))root.style.setProperty(key,String(value));
    root.style.setProperty('--lyric-font',current.font==='serif'?"'Noto Serif KR',serif":current.font==='rounded'?"'Arial Rounded MT Bold','Noto Sans KR',sans-serif":"Arial,'Noto Sans KR',Roboto,sans-serif");
    document.querySelectorAll('[data-appearance]').forEach(el=>{const key=el.dataset.appearance;if(el.type==='checkbox')el.checked=current[key];else el.value=String(current[key]);const out=$(el.id+'Value');if(out)out.textContent=current[key]+(el.dataset.unit||'');});
    document.querySelectorAll('[data-preset]').forEach(el=>el.setAttribute('aria-pressed',String(current.preset===el.dataset.preset)));
    $('appearanceSummary').textContent=({original:'기본 스타일',focus:'가사 집중',album:'앨범 중심',custom:'직접 꾸민 화면'})[current.preset];
    $('appearanceStatus').textContent='변경 사항이 자동으로 저장돼요';
    return changed;
  }
  function change(key,value){const next=sanitize({...current,[key]:value,preset:'custom'});onChange(next);}
  function preset(name){if(!Object.prototype.hasOwnProperty.call(presets,name))return;onChange(sanitize({...defaults,...presets[name],preset:name}));}
  function mount(callback){
    onChange=callback;
    const sections=[
      ['배치와 표시',[
        ['layout','화면 배치',[['auto','자동 · 가로는 나란히'],['immersive','전체 화면 가사'],['split','앨범 왼쪽 · 가사 오른쪽'],['reverse','가사 왼쪽 · 앨범 오른쪽']]],
        ['controls','재생 컨트롤',[['auto','자동 숨김 · 터치하면 표시'],['always','항상 표시'],['minimal','최소 표시 · 버튼으로 펼치기']]],
        ['coverSize','커버 크기',60,130,5,'%'],['lyricWidth','가사 영역 너비',60,100,2,'%'],
        ['showCover','앨범 커버 표시','toggle'],['showInfo','곡 제목·가수 표시','toggle']]],
      ['가사 모양',[
        ['align','가사 정렬',[['left','왼쪽'],['center','가운데'],['right','오른쪽']]],
        ['font','글꼴 스타일',[['system','기본 고딕'],['serif','명조'],['rounded','둥근 고딕 · 기기 지원 시']]],
        ['weight','글자 굵기',400,900,50,''],['lineHeight','줄바꿈 간격',110,180,5,'%'],['lineGap','가사 줄 사이 여백',4,40,2,' px'],
        ['textColor','강조 글자 색상','color'],['showTranslation','번역 표시','toggle'],['showRomanization','발음 표시','toggle']]],
      ['흐름과 효과',[
        ['flow','가사 이동',[['continuous','곡 진행도에 따라 흐르기'],['line','현재 줄을 중심에 유지']]],
        ['focusPosition','현재 가사의 세로 위치',30,65,1,'%'],['opacity','주변 가사 선명도',15,80,1,'%'],['blur','주변 가사 흐림',0,5,.5,' px'],
        ['brightness','배경 밝기',25,100,1,'%'],['glow','긴 음절의 빛 번짐','toggle']]]
    ];
    const host=$('appearanceFields');
    for(const [title,fields] of sections){
      const section=document.createElement('section');section.className='setting-section';
      const heading=document.createElement('h3');heading.textContent=title;section.append(heading);
      for(const [key,label,type,max,step,unit] of fields){
        const row=document.createElement('div'),id='appearance-'+key;
        const caption=document.createElement('label');caption.htmlFor=id;caption.textContent=label;
        const el=document.createElement(Array.isArray(type)?'select':'input');el.id=id;el.dataset.appearance=key;
        if(Array.isArray(type)){
          row.className='setting-select';for(const [value,text] of type){const option=document.createElement('option');option.value=value;option.textContent=text;el.append(option);}row.append(caption,el);
        }else if(type==='toggle'){
          row.className='appearance-toggle';el.type='checkbox';el.setAttribute('role','switch');row.append(caption,el);
        }else if(type==='color'){
          row.className='appearance-color';el.type='color';row.append(caption,el);
        }else{
          row.className='setting-range';const line=document.createElement('div');line.className='label-row';const output=document.createElement('output');output.id=id+'Value';output.htmlFor=id;line.append(caption,output);
          el.type='range';el.min=type;el.max=max;el.step=step;el.dataset.unit=unit||'';row.append(line,el);
        }
        el.addEventListener(el.type==='range'||el.type==='color'?'input':'change',()=>change(key,el.type==='checkbox'?el.checked:el.type==='range'?Number(el.value):el.value));section.append(row);
      }host.append(section);
    }
    document.querySelectorAll('[data-preset]').forEach(el=>el.addEventListener('click',()=>preset(el.dataset.preset)));
    $('appearanceReset').addEventListener('click',()=>preset('original'));
  }
  return {defaults,sanitize,apply,mount,preset,get:()=>({...current})};
})();
