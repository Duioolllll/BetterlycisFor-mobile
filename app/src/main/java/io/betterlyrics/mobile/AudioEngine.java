package io.betterlyrics.mobile;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.audiofx.Visualizer;
import android.os.*;
import org.json.*;

/** Lifecycle-bound output visualization. Neither input path opens the microphone. */
public final class AudioEngine {
    private final Context context;
    private Session session;
    private volatile AudioSignal.Frame latest=new AudioSignal().silence(0);
    private volatile String mode="visualizer",status="off",message="소리를 연결하면 배경이 반응해요.";
    private volatile boolean playing;
    private volatile boolean connected;
    private volatile long generation;
    private static final class Session {final HandlerThread thread=new HandlerThread("lyrics-spectrum");Handler handler;Visualizer effect;volatile boolean active=true;}
    public AudioEngine(Context context){this.context=context.getApplicationContext();}
    public void playing(boolean value){playing=value;}
    public boolean playing(){return playing;}
    public boolean permitted(){return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}
    public String mode(){return mode;}
    public synchronized long ticket(){return generation;}
    public synchronized void startVisualizer(){
        stop();mode="visualizer";
        if(!permitted()){status="permission";message="소리 반응을 켜려면 오디오 권한을 허용해 주세요.";return;}
        status="connecting";message="재생 소리에 연결하고 있어요.";long ticket=++generation;
        Session s=new Session();session=s;s.thread.start();s.handler=new Handler(s.thread.getLooper());
        s.handler.post(()->{
            try{
                Visualizer effect=new Visualizer(0);s.effect=effect;int[] range=Visualizer.getCaptureSizeRange();int size=Math.min(2048,range[1]);while(size>range[0]&&(size&(size-1))!=0)size&=size-1;
                if(effect.setCaptureSize(size)!=Visualizer.SUCCESS)throw new IllegalStateException();
                effect.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED);
                if(effect.setEnabled(true)!=Visualizer.SUCCESS)throw new IllegalStateException();
                synchronized(AudioEngine.this){if(!s.active||ticket!=generation){release(s);return;}connected=true;}
                byte[] fft=new byte[size],wave=new byte[size];AudioSignal signal=new AudioSignal();long[] lastSound={SystemClock.elapsedRealtime()};
                Runnable poll=new Runnable(){public void run(){
                    if(!s.active||ticket!=generation)return;
                    try{
                        long now=SystemClock.elapsedRealtime();
                        if(!playing){publish(ticket,signal.silence(now),"paused","일시정지 · 기본 그라데이션");}
                        else{
                            if(effect.getWaveForm(wave)!=Visualizer.SUCCESS||effect.getFft(fft)!=Visualizer.SUCCESS)throw new IllegalStateException();
                            AudioSignal.Frame frame=signal.visualizer(fft,wave,Math.max(8000,effect.getSamplingRate()/1000),now);
                            if(frame.level>.03f)lastSound[0]=now;
                            publish(ticket,frame,now-lastSound[0]>3500?"silent":"live",now-lastSound[0]>3500?"소리 입력이 없어요. 다른 연결 방식도 시도할 수 있어요.":"재생 소리에 반응 중");
                        }
                        if(s.active)s.handler.postDelayed(this,playing?45:150);
                    }catch(Exception error){failure(ticket,"기본 소리 연결을 지원하지 않아요. 다른 방식으로 연결해 보세요.");release(s);}
                }};poll.run();
            }catch(Exception|LinkageError error){failure(ticket,"이 기기는 기본 소리 연결을 지원하지 않아요. 다른 방식으로 연결해 보세요.");release(s);}
        });
    }
    private void release(Session s){s.active=false;Visualizer old=s.effect;s.effect=null;if(old!=null){try{old.setEnabled(false);}catch(Exception ignored){}try{old.release();}catch(Exception ignored){}}s.thread.quitSafely();}
    public synchronized void stop(){
        generation++;Session s=session;session=null;
        if(s!=null){
            s.active=false;
            if(s.handler!=null){s.handler.removeCallbacksAndMessages(null);s.handler.post(()->release(s));}
            else release(s);
        }
        context.stopService(new Intent(context,AudioCaptureService.class));connected=false;latest=new AudioSignal().silence(SystemClock.elapsedRealtime());status="off";message="소리를 연결하면 배경이 반응해요.";
    }
    public synchronized void suspend(){stop();status="paused";message="화면을 벗어나 소리 분석을 멈췄어요.";}
    public synchronized void startCapture(int resultCode,Intent data){
        stop();mode="capture";status="connecting";message="재생 소리에 연결하고 있어요.";long ticket=++generation;
        Intent intent=new Intent(context,AudioCaptureService.class).putExtra("resultCode",resultCode).putExtra("data",data).putExtra("ticket",ticket);
        try{context.startForegroundService(intent);}catch(Exception error){failure(ticket,"소리 연결을 시작하지 못했어요. 다시 연결해 주세요.");}
    }
    public synchronized void publish(long ticket,AudioSignal.Frame frame,String nextStatus,String nextMessage){if(ticket!=generation)return;connected=true;latest=frame;status=nextStatus;message=nextMessage;}
    public synchronized void failure(long ticket,String text){if(ticket!=generation)return;connected=false;latest=new AudioSignal().silence(SystemClock.elapsedRealtime());status="unavailable";message=text;}
    public JSONObject packet(){
        JSONObject out=new JSONObject();try{
            AudioSignal.Frame frame=latest;boolean fresh=playing&&SystemClock.elapsedRealtime()-frame.time<450&&status.equals("live");
            JSONArray bands=new JSONArray();for(float band:frame.bands)bands.put(fresh?band:0);
            out.put("status",status).put("message",message).put("source",mode).put("connected",connected).put("available",fresh).put("level",fresh?frame.level:0).put("bass",fresh?frame.bass:0).put("mid",fresh?frame.mid:0).put("treble",fresh?frame.treble:0).put("beat",fresh?frame.beat:0).put("sequence",frame.sequence).put("bands",bands);
        }catch(JSONException ignored){}return out;
    }
}
