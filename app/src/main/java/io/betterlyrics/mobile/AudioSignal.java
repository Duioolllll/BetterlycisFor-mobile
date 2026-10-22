package io.betterlyrics.mobile;

import java.util.Arrays;

/** Local spectral features only. No PCM, recording, network or Android dependency. */
public final class AudioSignal {
    public static final int SIZE=2048,BANDS=16;
    public static final class Frame {
        public final float level,bass,mid,treble,beat;
        public final float[] bands;
        public final long sequence,time;
        Frame(float level,float bass,float mid,float treble,float beat,float[] bands,long sequence,long time){this.level=level;this.bass=bass;this.mid=mid;this.treble=treble;this.beat=beat;this.bands=bands.clone();this.sequence=sequence;this.time=time;}
    }
    private final double[] real=new double[SIZE],imag=new double[SIZE];
    private final float[] smoothed=new float[BANDS];
    private float level,bass,mid,treble,beat,average=.08f,priorBass;
    private long previous=-1,lastBeat=-1000,sequence;
    private static float clamp(double value){return (float)Math.max(0,Math.min(1,Double.isFinite(value)?value:0));}
    private static float smooth(float old,float target,double dt,double attack,double release){return (float)(old+(target-old)*(1-Math.exp(-dt/(target>old?attack:release))));}
    public Frame silence(long now){Arrays.fill(smoothed,0);level=bass=mid=treble=beat=priorBass=0;average=.08f;previous=now;return new Frame(0,0,0,0,0,smoothed,sequence,now);}
    public Frame pcm(short[] samples,int count,int sampleRate,long now){
        count=Math.max(0,Math.min(Math.min(count,samples.length),SIZE));double energy=0,mean=0;
        for(int i=0;i<count;i++)mean+=samples[i]/32768.0;if(count>0)mean/=count;
        Arrays.fill(real,0);Arrays.fill(imag,0);
        for(int i=0;i<count;i++){double x=samples[i]/32768.0-mean;energy+=x*x;real[i]=x*(.5-.5*Math.cos(2*Math.PI*i/(SIZE-1)));}
        fft(real,imag);
        float[] bands=new float[BANDS];double[] totals=new double[BANDS];int[] bins=new int[BANDS];
        for(int k=1;k<SIZE/2;k++){
            double hz=k*(double)sampleRate/SIZE;if(hz<30||hz>Math.min(14000,sampleRate*.48))continue;
            int index=Math.max(0,Math.min(BANDS-1,(int)(Math.log(hz/30)/Math.log(14000.0/30)*BANDS)));
            totals[index]+=Math.hypot(real[k],imag[k])*4/SIZE;bins[index]++;
        }
        for(int i=0;i<BANDS;i++)bands[i]=clamp(Math.sqrt(totals[i]/Math.max(1,bins[i]))*1.7);
        return update(bands,Math.sqrt(energy/Math.max(1,count)),now);
    }
    public Frame visualizer(byte[] spectrum,byte[] waveform,int sampleRate,long now){
        float[] bands=new float[BANDS];double[] totals=new double[BANDS];int[] bins=new int[BANDS];
        for(int k=1;k<spectrum.length/2;k++){
            double hz=k*(double)sampleRate/spectrum.length;if(hz<30||hz>14000)continue;
            int index=Math.max(0,Math.min(BANDS-1,(int)(Math.log(hz/30)/Math.log(14000.0/30)*BANDS)));
            totals[index]+=Math.hypot(spectrum[2*k],spectrum[2*k+1])/181.0;bins[index]++;
        }
        for(int i=0;i<BANDS;i++)bands[i]=clamp(Math.sqrt(totals[i]/Math.max(1,bins[i]))*1.4);
        double energy=0,mean=0;for(byte b:waveform)mean+=(b&255);mean/=Math.max(1,waveform.length);for(byte b:waveform){double x=((b&255)-mean)/128.0;energy+=x*x;}
        return update(bands,Math.sqrt(energy/Math.max(1,waveform.length)),now);
    }
    private Frame update(float[] input,double rms,long now){
        if(previous>=0&&now<previous)return silence(now);
        double dt=previous<0?.045:Math.max(.001,Math.min(.25,(now-previous)/1000.0));previous=now;
        if(rms<.0012){Arrays.fill(input,0);rms=0;}
        for(int i=0;i<BANDS;i++)smoothed[i]=smooth(smoothed[i],input[i],dt,.025,.22);
        float rawBass=0,rawMid=0,rawTreble=0;
        for(int i=0;i<5;i++)rawBass=Math.max(rawBass,input[i]);
        for(int i=5;i<12;i++)rawMid=Math.max(rawMid,input[i]);
        for(int i=12;i<BANDS;i++)rawTreble=Math.max(rawTreble,input[i]);
        average=smooth(average,rawBass,dt,.65,1.1);
        boolean hit=rms>.006&&rawBass>.10&&rawBass>average*1.35+.035&&rawBass-priorBass>.035&&now-lastBeat>=210;
        if(hit){sequence++;lastBeat=now;beat=clamp(.35+rawBass);}else beat=smooth(beat,0,dt,.01,.20);
        priorBass=rawBass;
        level=smooth(level,clamp(Math.sqrt(rms)*1.55),dt,.025,.25);
        bass=smooth(bass,rawBass,dt,.018,.25);mid=smooth(mid,rawMid,dt,.035,.20);treble=smooth(treble,rawTreble,dt,.02,.15);
        return new Frame(level,bass,mid,treble,beat,smoothed,sequence,now);
    }
    private static void fft(double[] r,double[] im){
        int n=r.length;for(int i=1,j=0;i<n;i++){int bit=n>>1;for(; (j&bit)!=0;bit>>=1)j^=bit;j^=bit;if(i<j){double x=r[i];r[i]=r[j];r[j]=x;}}
        for(int length=2;length<=n;length<<=1){double angle=-2*Math.PI/length,wr0=Math.cos(angle),wi0=Math.sin(angle);
            for(int start=0;start<n;start+=length){double wr=1,wi=0;for(int j=0;j<length/2;j++){
                int a=start+j,b=a+length/2;double tr=wr*r[b]-wi*im[b],ti=wr*im[b]+wi*r[b];r[b]=r[a]-tr;im[b]=im[a]-ti;r[a]+=tr;im[a]+=ti;double next=wr*wr0-wi*wi0;wi=wr*wi0+wi*wr0;wr=next;
            }}
        }
    }
}
