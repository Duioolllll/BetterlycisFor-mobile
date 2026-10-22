import io.betterlyrics.mobile.AudioSignal;
import java.util.Arrays;

public final class AudioSignalTests {
    static int checks;
    static void ok(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static short[] tone(double hz,double amplitude){short[] data=new short[AudioSignal.SIZE];for(int i=0;i<data.length;i++)data[i]=(short)Math.round(Math.sin(i*2*Math.PI*hz/44100)*32767*amplitude);return data;}
    static AudioSignal.Frame settled(double hz,double amplitude){AudioSignal signal=new AudioSignal();AudioSignal.Frame frame=null;for(int i=0;i<20;i++)frame=signal.pcm(tone(hz,amplitude),AudioSignal.SIZE,44100,i*47);return frame;}
    public static void main(String[] args){
        AudioSignal signal=new AudioSignal();AudioSignal.Frame silent=signal.pcm(new short[AudioSignal.SIZE],AudioSignal.SIZE,44100,1000);
        ok(silent.level==0&&silent.bass==0&&silent.sequence==0,"digital silence never generates beats");
        short[] dc=new short[AudioSignal.SIZE];Arrays.fill(dc,(short)20000);silent=signal.pcm(dc,dc.length,44100,1050);ok(silent.level==0,"DC is not music energy");
        AudioSignal.Frame bass=settled(86,.4),mid=settled(1000,.4),high=settled(8500,.4);
        ok(bass.bass>mid.bass*3&&bass.bass>bass.mid*2,"bass tone classified as bass");
        ok(mid.mid>mid.bass*2&&mid.mid>mid.treble*2,"mid tone classified as mid");
        ok(high.treble>high.bass*2&&high.treble>high.mid*2,"treble tone classified as treble");
        ok(settled(1000,.6).level>settled(1000,.03).level,"loudness changes visual energy");
        signal=new AudioSignal();long sequence=0;
        for(int i=0;i<30;i++){AudioSignal.Frame frame=signal.pcm(tone(86,.5),AudioSignal.SIZE,44100,1000+i*47);sequence=frame.sequence;}
        ok(sequence==1,"steady bass does not invent a metronome");
        for(int i=0;i<12;i++)signal.pcm(new short[AudioSignal.SIZE],AudioSignal.SIZE,44100,2500+i*47);
        AudioSignal.Frame hit=signal.pcm(tone(86,.5),AudioSignal.SIZE,44100,3200);ok(hit.sequence==2&&hit.beat>.1,"new real transient generates a beat after silence");
        AudioSignal.Frame paused=signal.silence(3250);ok(paused.level==0&&paused.beat==0&&paused.sequence==2,"pause immediately clears energy without forging beats");
        byte[] fft=new byte[1024],wave=new byte[1024];Arrays.fill(wave,(byte)128);
        silent=new AudioSignal().visualizer(fft,wave,44100,1);ok(silent.level==0&&silent.bass==0,"8-bit unsigned waveform silence");
        silent=new AudioSignal().visualizer(fft,new byte[1024],44100,1);ok(silent.level==0,"uninitialized flat waveform is not music");
        for(int i=0;i<wave.length;i++)wave[i]=(byte)(128+Math.round(Math.sin(i*2*Math.PI*1000/44100)*60));fft[46]=60;
        AudioSignal.Frame visual=new AudioSignal().visualizer(fft,wave,44100,100);ok(visual.mid>0&&visual.level>0,"Visualizer signed complex FFT and unsigned waveform parsed");
        for(float band:high.bands)ok(Float.isFinite(band)&&band>=0&&band<=1,"bounded finite spectrum band");
        signal=new AudioSignal();AudioSignal.Frame partial=signal.pcm(tone(200,.2),177,44100,50);ok(Float.isFinite(partial.level),"partial PCM read supported");
        System.out.println("AudioSignalTests: "+checks+" checks passed");
    }
}
