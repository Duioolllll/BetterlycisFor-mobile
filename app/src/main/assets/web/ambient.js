'use strict';

// Album-colour fluid field. Real audio only, except the explicitly labelled five-second preview.
window.ReactiveAmbient=(()=>{
  const clamp=(x,a=0,b=1)=>Math.max(a,Math.min(b,Number.isFinite(Number(x))?Number(x):0));
  const root=()=>document.querySelector('.ambient');
  const reduced=window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  let gl,program,uniform={},canvas,particlesCanvas,ctx,initialized=false,dirty=true;
  let palette=[[.25,.49,.37],[.46,.19,.39],[.12,.34,.47],[.54,.35,.18]],previousPalette=palette.map(c=>c.slice()),paletteAt=0;
  let packet={},packetAt=-1,lastSequence=-1,previewUntil=0,previewSequence=-1;
  let energy=[0,0,0,0],rings=[],particles=[],lastAt=0,lastDraw=0,flow=0,frames=0,lastMode='',quality=.85;
  const fragment=`precision mediump float;
uniform vec2 r;uniform float t,gain;uniform vec4 audio;
uniform vec3 c1,c2,c3,c4;uniform vec4 pulse0,pulse1,pulse2;
float ring(vec2 p,vec4 q){float age=q.z;float radius=.04+age*.78;float d=length(p-q.xy);return exp(-pow((d-radius)/(.045+age*.055),2.))*q.w*pow(max(0.,1.-age),2.);}
void main(){
 vec2 uv=gl_FragCoord.xy/r;uv.y=1.-uv.y;float aspect=r.x/r.y;
 vec2 p=uv;vec2 centered=uv-.5;centered.x*=aspect;
 float ripples=ring(centered,pulse0)+ring(centered,pulse1)+ring(centered,pulse2);
 p=.5+(p-.5)/(1.+audio.y*.048*gain);
 float sway=.018+audio.z*.065*gain;
 p+=vec2(sin(p.y*5.4+t*.7),cos(p.x*4.7-t*.6))*sway;
 p+=vec2(sin(p.y*9.-t),cos(p.x*8.+t*.8))*.012;
 p+=centered*ripples*.045*gain;
 vec2 a=vec2(.13+.18*sin(t*.41),.18+.15*cos(t*.37));
 vec2 b=vec2(.82+.18*cos(t*.29),.24+.21*sin(t*.31));
 vec2 c=vec2(.23+.20*cos(t*.33),.83+.13*sin(t*.43));
 vec2 d=vec2(.87+.13*sin(t*.38),.83+.19*cos(t*.26));
 vec4 w=exp(-vec4(dot(p-a,p-a),dot(p-b,p-b),dot(p-c,p-c),dot(p-d,p-d))*4.2);
 vec3 col=(c1*w.x+c2*w.y+c3*w.z+c4*w.w)/max(.001,dot(w,vec4(1.)));
 float silk=sin((p.x+p.y)*4.5+t*.45)*sin(p.y*3.5-t*.27)*.045;
 col+=silk;col*=.94+audio.x*.26*gain+audio.y*.13*gain;
 col+=mix(c2,c3,.5)*ripples*.29*gain;
 float bloom=exp(-dot(centered,centered)*3.8)*audio.w*.075*gain;
 col+=bloom;col*=1.-smoothstep(.30,.96,length(centered))*.32;
 float grain=(fract(sin(dot(gl_FragCoord.xy,vec2(12.9898,78.233)))*43758.5453)-.5)/255.;
 gl_FragColor=vec4(clamp(col+grain,0.,.88),1.);
}`;
  function initGL(){
    try{
      gl=canvas.getContext('webgl',{alpha:false,antialias:false,depth:false,powerPreference:'low-power'});if(!gl)return;
      const shader=(type,text)=>{const s=gl.createShader(type);gl.shaderSource(s,text);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;};
      program=gl.createProgram();gl.attachShader(program,shader(gl.VERTEX_SHADER,'attribute vec2 a;void main(){gl_Position=vec4(a,0.,1.);}'));gl.attachShader(program,shader(gl.FRAGMENT_SHADER,fragment));gl.linkProgram(program);
      if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error('ambient shader link');gl.useProgram(program);
      const b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]),gl.STATIC_DRAW);
      const a=gl.getAttribLocation(program,'a');gl.enableVertexAttribArray(a);gl.vertexAttribPointer(a,2,gl.FLOAT,false,0,0);
      uniform={};for(const name of ['r','t','gain','audio','c1','c2','c3','c4','pulse0','pulse1','pulse2'])uniform[name]=gl.getUniformLocation(program,name);
      root().classList.add('webgl');resize();dirty=true;
    }catch(_){gl=null;root().classList.remove('webgl');}
  }
  function init(){
    if(initialized)return;initialized=true;canvas=document.getElementById('ambientCanvas');
    particlesCanvas=document.createElement('canvas');particlesCanvas.id='ambientParticles';root().append(particlesCanvas);ctx=particlesCanvas.getContext('2d',{alpha:true});
    initGL();resize();
    canvas.addEventListener('webglcontextlost',event=>{event.preventDefault();gl=null;root().classList.remove('webgl');dirty=true;});
    canvas.addEventListener('webglcontextrestored',initGL);
    document.addEventListener('visibilitychange',()=>{if(document.hidden){packet={};packetAt=-1;energy.fill(0);rings=[];particles=[];if(ctx)ctx.clearRect(0,0,particlesCanvas.width,particlesCanvas.height);}lastAt=0;});
  }
  function resize(){
    if(!canvas)return;const limit=window.devicePixelRatio>2?760:900;
    const scale=Math.min(1,limit/Math.max(innerWidth,innerHeight))*quality;
    canvas.width=Math.max(1,Math.round(innerWidth*scale));canvas.height=Math.max(1,Math.round(innerHeight*scale));
    if(gl)gl.viewport(0,0,canvas.width,canvas.height);
    particlesCanvas.width=Math.max(1,Math.round(innerWidth));particlesCanvas.height=Math.max(1,Math.round(innerHeight));dirty=true;
  }
  function receive(value){
    try{const data=typeof value==='string'?JSON.parse(value):value;if(!data||typeof data!=='object')return;packet=data;packetAt=performance.now();}catch(_){}
  }
  function trigger(strength,now){
    if(strength<.05)return;
    rings.unshift({x:(Math.random()-.5)*.18,y:(Math.random()-.5)*.23,at:now,strength:clamp(strength)});rings=rings.slice(0,3);
    for(let i=0;i<Math.round(10+strength*14)&&particles.length<70;i++)particles.push({x:Math.random(),y:Math.random(),vx:(Math.random()-.5)*.025,vy:-.018-Math.random()*.028,age:0,life:1.2+Math.random()*1.9,size:1+Math.random()*2.7,color:Math.floor(Math.random()*4)});
  }
  function preview(){previewUntil=performance.now()+5000;previewSequence=-1;}
  function isPreview(){return performance.now()<previewUntil;}
  function frame(now,playing,settings={}){
    if(!initialized)init();if(document.hidden)return;
    const dt=lastAt?Math.min(.12,Math.max(0,(now-lastAt)/1000)):1/60;lastAt=now;
    const mode=settings.backgroundMode||'sound',previewing=isPreview(),moving=(mode!=='static'||previewing)&&!reduced;
    let source=packet,enabled=mode==='sound'&&playing&&packet.available&&now-packetAt<450;
    if(previewing){const phase=(now%500)/500,hit=Math.floor(now/500);source={level:.52+.18*Math.sin(now/300),bass:Math.exp(-phase*9),mid:.4+.2*Math.sin(now/550),treble:Math.pow(Math.max(0,Math.sin(now/90)),4)*.7,beat:hit!==previewSequence?.85:0,sequence:hit};previewSequence=hit;enabled=true;}
    const target=enabled&&moving?[source.level,source.bass,source.mid,source.treble].map(x=>clamp(x)):[0,0,0,0];
    for(let i=0;i<4;i++)energy[i]+=(target[i]-energy[i])*(1-Math.exp(-dt/(target[i]>energy[i]?.035:.19)));
    if(enabled&&moving&&source.sequence!==lastSequence){lastSequence=source.sequence;trigger(clamp(source.beat),now);}
    if(mode!==lastMode){dirty=true;lastMode=mode;if(!moving){rings=[];particles=[];energy.fill(0);}}
    if(moving)flow+=dt*((playing||previewing)?.15+.30*energy[2]:.028);
    rings=rings.filter(x=>now-x.at<1100);
    const interval=moving?1000/30:1000;
    if(now-lastDraw<interval&&!dirty)return;
    if(!moving&&!dirty)return;
    const drawDt=Math.max(.001,Math.min(.10,(now-lastDraw)/1000));lastDraw=now;dirty=false;const start=performance.now(),strength=clamp(settings.backgroundIntensity===undefined?.7:settings.backgroundIntensity/100)*1.4;
    const blend=moving?clamp((now-paletteAt)/1500):1,colors=palette.map((c,i)=>c.map((v,j)=>previousPalette[i][j]+(v-previousPalette[i][j])*blend));
    if(gl){
      gl.useProgram(program);gl.uniform2f(uniform.r,canvas.width,canvas.height);gl.uniform1f(uniform.t,flow);gl.uniform1f(uniform.gain,strength);gl.uniform4fv(uniform.audio,energy);
      for(let i=0;i<4;i++)gl.uniform3fv(uniform['c'+(i+1)],colors[i]);
      for(let i=0;i<3;i++){const pulse=rings[i];gl.uniform4f(uniform['pulse'+i],pulse?pulse.x:0,pulse?pulse.y:0,pulse?clamp((now-pulse.at)/1100):1,pulse?pulse.strength:0);}
      gl.drawArrays(gl.TRIANGLES,0,6);frames++;
    }
    root().classList.toggle('ambient-still',!moving);root().style.setProperty('--audio-glow',String(energy[0]*strength*.15));
    if(ctx){
      ctx.clearRect(0,0,particlesCanvas.width,particlesCanvas.height);
      if(settings.backgroundParticles!==false&&moving&&strength>.01){
        ctx.globalCompositeOperation='lighter';
        for(const p of particles){p.age+=drawDt;p.x+=p.vx*drawDt*(1+energy[2]*3);p.y+=p.vy*drawDt*(1+energy[3]*3);
          const alpha=Math.sin(Math.PI*clamp(p.age/p.life))*(.10+energy[3]*.26)*strength,c=colors[p.color].map(x=>Math.round(x*255));
          ctx.fillStyle='rgba('+c.join(',')+','+alpha+')';ctx.beginPath();ctx.arc(p.x*innerWidth,p.y*innerHeight,p.size*3,0,Math.PI*2);ctx.fill();
          ctx.fillStyle='rgba(255,255,255,'+(alpha*.8)+')';ctx.beginPath();ctx.arc(p.x*innerWidth,p.y*innerHeight,p.size*.6,0,Math.PI*2);ctx.fill();
        }particles=particles.filter(p=>p.age<p.life&&p.y>-.05);
      }else particles=[];
    }
    if(performance.now()-start>24&&quality>.55){quality=Math.max(.55,quality-.1);resize();}
  }
  function extractPalette(img){
    try{
      const c=document.createElement('canvas');c.width=c.height=24;const context=c.getContext('2d',{willReadFrequently:true});context.drawImage(img,0,0,24,24);const data=context.getImageData(0,0,24,24).data,colors=[];
      for(let q=0;q<4;q++){let rr=0,gg=0,bb=0,n=0;for(let y=Math.floor(q/2)*12;y<Math.floor(q/2)*12+12;y++)for(let x=q%2*12;x<q%2*12+12;x++){const at=(y*24+x)*4,l=(data[at]+data[at+1]+data[at+2])/3;if(l<16||l>240)continue;rr+=data[at];gg+=data[at+1];bb+=data[at+2];n++;}
        if(!n)colors.push(palette[q].slice());else{const max=Math.max(rr/n,gg/n,bb/n,75);colors.push([rr/n,gg/n,bb/n].map(v=>clamp(v/max*.64,.06,.72)));}}
      previousPalette=palette.map(c=>c.slice());palette=colors;paletteAt=performance.now();dirty=true;
      ['--a','--b','--c','--d'].forEach((key,i)=>document.documentElement.style.setProperty(key,'rgb('+colors[i].map(x=>Math.round(x*255)).join(',')+')'));
    }catch(_){}
  }
  return {init,resize,frame,receive,preview,isPreview,extractPalette,metrics:()=>({webgl:!!gl,frames,quality,energy:energy.slice(),rings:rings.length,particles:particles.length,preview:isPreview(),reducedMotion:reduced})};
})();
