package io.betterlyrics.mobile;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.projection.*;
import android.os.*;

/** User-approved, audio-only playback analysis. No VirtualDisplay, microphone, file or network. */
public final class AudioCaptureService extends Service {
    private MediaProjection projection;
    private volatile AudioRecord recorder;
    private volatile boolean running;
    private volatile boolean stopping;
    private Thread reader;
    private AudioEngine engine;
    private long ticket;
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||Build.VERSION.SDK_INT<29){stopSelf();return START_NOT_STICKY;}
        engine=AppState.get(this).audio;ticket=intent.getLongExtra("ticket",-1);
        if(ticket!=engine.ticket()){stopSelf();return START_NOT_STICKY;}
        try{
            NotificationManager notifications=getSystemService(NotificationManager.class);notifications.createNotificationChannel(new NotificationChannel("audio-visuals","사운드 반응 배경",NotificationManager.IMPORTANCE_LOW));
            PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification note=new Notification.Builder(this,"audio-visuals").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("사운드 반응 배경").setContentText("재생 소리의 강약을 분석하고 있어요. 화면을 벗어나면 종료돼요.").setContentIntent(open).setOngoing(true).build();
            startForeground(24,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            Intent data=intent.getParcelableExtra("data");
            projection=getSystemService(MediaProjectionManager.class).getMediaProjection(intent.getIntExtra("resultCode",Activity.RESULT_CANCELED),data);
            if(projection==null)throw new IllegalStateException();
            projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){if(!stopping&&engine!=null)engine.failure(ticket,"소리 공유가 종료됐어요. 다시 연결해 주세요.");if(!stopping)stopSelf();}},new Handler(Looper.getMainLooper()));
            AudioPlaybackCaptureConfiguration configuration=new AudioPlaybackCaptureConfiguration.Builder(projection).addMatchingUsage(AudioAttributes.USAGE_MEDIA).build();
            int rate=44100,min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IllegalStateException();
            recorder=new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(Math.max(min*2,AudioSignal.SIZE*8)).setAudioPlaybackCaptureConfig(configuration).build();
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException();
            recorder.startRecording();running=true;
            reader=new Thread(()->analyze(rate),"playback-spectrum");reader.start();
        }catch(Exception error){engine.failure(ticket,"이 음악 앱의 소리를 가져올 수 없어요. 기본 그라데이션을 사용해요.");stopSelf();}
        return START_NOT_STICKY;
    }
    private void analyze(int rate){
        AudioRecord active=recorder;AudioSignal analyzer=new AudioSignal();short[] pcm=new short[AudioSignal.SIZE];long lastSound=SystemClock.elapsedRealtime();
        try{
            while(running&&ticket==engine.ticket()){
                int read=active.read(pcm,0,pcm.length,AudioRecord.READ_BLOCKING);if(read<0)throw new IllegalStateException();if(read==0)continue;
                long now=SystemClock.elapsedRealtime();AudioSignal.Frame frame=engine.playing()?analyzer.pcm(pcm,read,rate,now):analyzer.silence(now);
                if(frame.level>.03)lastSound=now;
                String status=!engine.playing()?"paused":now-lastSound>3500?"silent":"live";
                engine.publish(ticket,frame,status,status.equals("silent")?"소리 입력이 없어요. 음악 앱이 공유를 제한할 수 있어요.":status.equals("paused")?"일시정지 · 기본 그라데이션":"재생 소리에 반응 중");
            }
        }catch(Exception error){if(running)engine.failure(ticket,"소리 연결이 끊겼어요. 다시 연결해 주세요.");}
        finally{try{active.release();}catch(Exception ignored){}if(recorder==active)recorder=null;java.util.Arrays.fill(pcm,(short)0);stopSelf();}
    }
    @Override public void onDestroy(){stopping=true;running=false;AudioRecord active=recorder;if(active!=null){try{active.stop();}catch(Exception ignored){}if(reader==null)try{active.release();}catch(Exception ignored){}}MediaProjection old=projection;projection=null;if(old!=null)try{old.stop();}catch(Exception ignored){}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
