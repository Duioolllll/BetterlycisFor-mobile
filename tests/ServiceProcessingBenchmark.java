import io.betterlyrics.mobile.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Run via benchmark-service.py; the baseline is compiled from a retained Git commit. */
public final class ServiceProcessingBenchmark {
    static volatile double sink;
    static final com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    static final short[] pcm=new short[AudioSignal.SIZE];
    static final byte[] spectrum=new byte[1024],wave=new byte[1024],hexBytes=new byte[256];
    static int parity;
    static void equal(AudioSignal.Frame a,BaselineAudioSignal.Frame b){
        float[] x={a.level,a.bass,a.mid,a.treble,a.beat},y={b.level,b.bass,b.mid,b.treble,b.beat};
        for(int i=0;i<x.length;i++)if(Float.floatToIntBits(x[i])!=Float.floatToIntBits(y[i]))throw new AssertionError("audio scalar changed");
        if(!Arrays.equals(a.bands,b.bands)||a.sequence!=b.sequence||a.time!=b.time)throw new AssertionError("audio frame changed");
        parity++;
    }
    static void parity(){
        AudioSignal current=new AudioSignal();BaselineAudioSignal baseline=new BaselineAudioSignal();Random random=new Random(42);
        int[] rates={8000,22050,44100,48000,96000};
        for(int frame=0;frame<1024;frame++){
            int rate=rates[(frame/17)%rates.length];for(int i=0;i<pcm.length;i++)pcm[i]=(short)random.nextInt();
            int count=frame%5==0?177:pcm.length;
            equal(current.pcm(pcm,count,rate,frame*47),baseline.pcm(pcm,count,rate,frame*47));
            if(frame%41==0)equal(current.silence(frame*47+1),baseline.silence(frame*47+1));
        }
        current=new AudioSignal();baseline=new BaselineAudioSignal();
        for(int frame=0;frame<1024;frame++){
            int size=frame%63<31?512:2048,rate=rates[(frame/19)%rates.length];byte[] fft=new byte[size],wav=new byte[size];random.nextBytes(fft);random.nextBytes(wav);
            equal(current.visualizer(fft,wav,rate,frame*45),baseline.visualizer(fft,wav,rate,frame*45));
        }
        AudioSignal.Frame retained=current.pcm(pcm,pcm.length,44100,60000);float[] copy=retained.bands.clone();current.pcm(new short[pcm.length],pcm.length,44100,60100);
        if(!Arrays.equals(retained.bands,copy))throw new AssertionError("scratch buffer escaped into an earlier frame");
    }
    static double[] measure(String kind,boolean optimized,int frames){
        AudioSignal current=new AudioSignal();BaselineAudioSignal baseline=new BaselineAudioSignal();double total=0;
        long allocated=bean.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime();
        for(int i=0;i<frames;i++){
            if(kind.equals("pcm"))total+=optimized?current.pcm(pcm,pcm.length,44100,i*47).level:baseline.pcm(pcm,pcm.length,44100,i*47).level;
            else if(kind.equals("visualizer"))total+=optimized?current.visualizer(spectrum,wave,44100,i*45).mid:baseline.visualizer(spectrum,wave,44100,i*45).mid;
            else if(optimized)total+=IoSupport.hex(hexBytes,true).length();
            else{StringBuilder hex=new StringBuilder();for(byte b:hexBytes)hex.append(String.format(Locale.ROOT,"%02X",b&255));total+=hex.length();}
        }
        double nanos=(System.nanoTime()-start)/(double)frames,bytes=(bean.getThreadAllocatedBytes(Thread.currentThread().getId())-allocated)/(double)frames;sink=total;
        return new double[]{nanos,bytes};
    }
    public static void main(String[] args){
        bean.setThreadAllocatedMemoryEnabled(true);parity();
        if(args.length>0&&args[0].equals("--check-only")){System.out.println("{\"audioFramesBitIdentical\":"+parity+",\"retainedFrameUnchanged\":true}");return;}
        for(int i=0;i<pcm.length;i++)pcm[i]=(short)(Math.sin(i*2*Math.PI*86/44100)*16000);
        new Random(41).nextBytes(spectrum);new Random(42).nextBytes(wave);new Random(43).nextBytes(hexBytes);
        System.out.println("{\"audioFramesBitIdentical\":"+parity+",\"retainedFrameUnchanged\":true,\"measurements\":[");
        String[] kinds={"pcm","visualizer","hex256"};
        for(int k=0;k<kinds.length;k++){
            String kind=kinds[k];int frames=k==2?200:2000;
            for(int w=0;w<3;w++){measure(kind,false,frames);measure(kind,true,frames);}
            double[] oldTime=new double[7],newTime=new double[7],oldBytes=new double[7],newBytes=new double[7];
            for(int round=0;round<7;round++){
                double[] oldResult,newResult;
                if(round%2==0){oldResult=measure(kind,false,frames);newResult=measure(kind,true,frames);}else{newResult=measure(kind,true,frames);oldResult=measure(kind,false,frames);}
                oldTime[round]=oldResult[0];oldBytes[round]=oldResult[1];newTime[round]=newResult[0];newBytes[round]=newResult[1];
            }
            Arrays.sort(oldTime);Arrays.sort(newTime);Arrays.sort(oldBytes);Arrays.sort(newBytes);
            System.out.printf(Locale.ROOT,"%s{\"operation\":\"%s\",\"iterationsPerRound\":%d,\"rounds\":7,\"baselineMedianNs\":%.2f,\"optimizedMedianNs\":%.2f,\"baselineBytesPerOp\":%.2f,\"optimizedBytesPerOp\":%.2f}",k==0?"":",\n",kind,frames,oldTime[3],newTime[3],oldBytes[3],newBytes[3]);
        }
        System.out.println("\n]}");
    }
}
