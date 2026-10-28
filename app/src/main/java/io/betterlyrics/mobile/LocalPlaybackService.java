package io.betterlyrics.mobile;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.*;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.*;
import android.widget.Toast;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Foreground playback for documents chosen through Android's file picker. */
public final class LocalPlaybackService extends Service {
    private static final String CHANNEL="local_playback", PLAY="io.betterlyrics.local.PLAY", PAUSE="io.betterlyrics.local.PAUSE", NEXT="io.betterlyrics.local.NEXT", PREVIOUS="io.betterlyrics.local.PREVIOUS", START="io.betterlyrics.local.START", STOP="io.betterlyrics.local.STOP";
    private static final int NOTIFICATION=3181;
    private static LocalPlaybackService live;
    private MediaSession session;
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private boolean prepared,playWhenReady,resumeAfterFocus,focused;
    private long duration;
    private int index=-1;
    private LocalLibrary.Playlist playlist;
    private final ArrayDeque<Integer> history=new ArrayDeque<>();
    private final Random random=new Random();
    private final ExecutorService artwork=Executors.newSingleThreadExecutor();
    private final ExecutorService lyricFiles=Executors.newSingleThreadExecutor();
    private Future<?> artworkTask;private int artworkGeneration;private Bitmap cover;
    private Future<?> lyricTask;private int lyricGeneration;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable widgetTick=new Runnable(){public void run(){
        AppState state=AppState.get(LocalPlaybackService.this);
        if(live!=LocalPlaybackService.this||state.connected||state.controller==null||!state.controller.getSessionToken().equals(session.getSessionToken())||!LyricsWidgetProvider.exists(LocalPlaybackService.this))return;
        LyricsWidgetProvider.refreshAll(LocalPlaybackService.this,state,false);long delay=LyricsWidgetProvider.nextDelay(state);if(delay>0)handler.postDelayed(this,delay);
    }};
    private final BroadcastReceiver noisy=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction()))pause(false);}};

    static void play(Context context,String id,int index){
        Intent intent=new Intent(context,LocalPlaybackService.class).setAction(START).putExtra("playlist",id).putExtra("index",index);
        context.startForegroundService(intent);
    }
    static void removeIfPlaying(String id){LocalPlaybackService service=live;if(service!=null&&service.playlist!=null&&service.playlist.id.equals(id))service.stopPlayback();}
    static String currentPlaylist(){LocalPlaybackService service=live;return service!=null&&service.playlist!=null?service.playlist.id:"";}
    static void libraryChanged(Context context,String id){
        LocalPlaybackService service=live;if(service==null||service.playlist==null||!service.playlist.id.equals(id))return;
        LocalLibrary.Playlist updated=LocalLibrary.get(context).get(id);if(updated==null){service.stopPlayback();return;}
        String current=service.playlist.tracks.get(service.index).uri;int replacement=-1;
        if(service.index<updated.tracks.size()&&updated.tracks.get(service.index).uri.equals(current))replacement=service.index;
        else for(int i=0;i<updated.tracks.size();i++)if(updated.tracks.get(i).uri.equals(current)){replacement=i;break;}
        boolean wasPlaying=service.playWhenReady;service.playlist=updated;service.history.clear();
        if(replacement<0){service.index=Math.min(service.index,updated.tracks.size()-1);service.load(wasPlaying);}
        else {service.index=replacement;service.session.setMetadata(service.metadata(updated.tracks.get(replacement),service.duration));service.publish(true);}
    }
    static boolean refreshWidget(){
        LocalPlaybackService service=live;if(service==null||service.session==null)return false;
        AppState state=AppState.get(service);if(state.controller==null||!state.controller.getSessionToken().equals(service.session.getSessionToken()))return false;
        service.handler.removeCallbacks(service.widgetTick);service.handler.post(service.widgetTick);return true;
    }
    static void setMode(Context context,String mode){
        if(!mode.equals("all")&&!mode.equals("one")&&!mode.equals("shuffle"))return;
        context.getSharedPreferences("local_music",0).edit().putString("mode",mode).apply();
        if(live!=null)live.publish(false);
    }
    static String mode(Context context){String value=context.getSharedPreferences("local_music",0).getString("mode","all");return value.equals("one")||value.equals("shuffle")?value:"all";}

    @Override public void onCreate(){
        super.onCreate();live=this;audio=getSystemService(AudioManager.class);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"음악 재생",NotificationManager.IMPORTANCE_LOW));
        session=new MediaSession(this,"BetterLyrics local music");
        session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){resume();}
            @Override public void onPause(){pause(false);}
            @Override public void onSkipToNext(){step(1,false);}
            @Override public void onSkipToPrevious(){step(-1,false);}
            @Override public void onSeekTo(long value){seek(value);}
            @Override public void onStop(){stopPlayback();}
            @Override public void onCustomAction(String action,Bundle extras){if(STOP.equals(action))stopPlayback();}
        });
        session.setSessionActivity(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        session.setActive(true);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        String action=intent.getAction();
        if(START.equals(action)){
            LocalLibrary.Playlist selected=LocalLibrary.get(this).get(intent.getStringExtra("playlist"));int selectedIndex=intent.getIntExtra("index",-1);
            if(selected==null||selectedIndex<0||selectedIndex>=selected.tracks.size()){stopSelf();return START_NOT_STICKY;}
            playlist=selected;index=selectedIndex;history.clear();
            AppState.get(this).prefs.edit().putString("sourcePackage","").apply();
            startForeground(NOTIFICATION,notification());
            load(true);
        }else if(playlist!=null){
            if(PLAY.equals(action))resume();else if(PAUSE.equals(action))pause(false);else if(NEXT.equals(action))step(1,false);else if(PREVIOUS.equals(action))step(-1,false);else if(STOP.equals(action))stopPlayback();
        }else stopSelf();
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent){return null;}

    private AudioAttributes attributes(){return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();}
    private boolean requestFocus(){
        if(focused)return true;
        focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes()).setOnAudioFocusChangeListener(change->{
            if(change==AudioManager.AUDIOFOCUS_LOSS){resumeAfterFocus=false;pause(false);}
            else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){resumeAfterFocus=playWhenReady;pause(true);}
            else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){if(player!=null)try{player.setVolume(.2f,.2f);}catch(IllegalStateException ignored){}}
            else if(change==AudioManager.AUDIOFOCUS_GAIN){if(player!=null)try{player.setVolume(1f,1f);}catch(IllegalStateException ignored){}if(resumeAfterFocus){resumeAfterFocus=false;resume();}}
        }).build();
        focused=audio.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;return focused;
    }
    private void abandonFocus(){if(focused&&focusRequest!=null)audio.abandonAudioFocusRequest(focusRequest);focused=false;}
    private void releasePlayer(){if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}prepared=false;}
    private void load(boolean start){
        releasePlayer();playWhenReady=start;resumeAfterFocus=false;
        LocalLibrary.Track track=playlist.tracks.get(index);duration=track.duration;cover=null;loadArtwork(track);
        session.setMetadata(metadata(track,duration));publish(true);
        loadLyrics(track);
        try{
            MediaPlayer created=new MediaPlayer();player=created;created.setAudioAttributes(attributes());created.setDataSource(this,Uri.parse(track.uri));
            created.setOnPreparedListener(p->{if(p!=player)return;prepared=true;try{duration=p.getDuration();}catch(IllegalStateException ignored){}
                session.setMetadata(metadata(track,duration));if(playWhenReady){if(requestFocus())try{p.start();}catch(IllegalStateException error){playWhenReady=false;abandonFocus();toast("음악 파일을 재생할 수 없습니다.");}else{playWhenReady=false;toast("다른 앱에서 오디오를 사용 중입니다.");}}publish(true);
            });
            created.setOnSeekCompleteListener(p->{if(p==player)publish(false);});
            created.setOnCompletionListener(p->{if(p==player){playWhenReady=false;step(1,true);}});
            created.setOnErrorListener((p,what,extra)->{if(p==player){releasePlayer();playWhenReady=false;abandonFocus();toast("이 음악 파일을 재생할 수 없습니다.");publish(false);}return true;});
            created.prepareAsync();
        }catch(Exception error){releasePlayer();playWhenReady=false;abandonFocus();toast("음악 파일을 열 수 없습니다.");publish(false);}
    }
    private MediaMetadata metadata(LocalLibrary.Track track,long length){
        MediaMetadata.Builder builder=new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,playlist.id+":"+index).putString(MediaMetadata.METADATA_KEY_TITLE,track.title).putString(MediaMetadata.METADATA_KEY_ARTIST,track.artist).putString(MediaMetadata.METADATA_KEY_ALBUM,track.album).putLong(MediaMetadata.METADATA_KEY_DURATION,Math.max(0,length));
        if(cover!=null&&!cover.isRecycled())builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,cover);return builder.build();
    }
    private void loadArtwork(LocalLibrary.Track track){
        int ticket=++artworkGeneration;if(artworkTask!=null)artworkTask.cancel(true);
        artworkTask=artwork.submit(()->{
            MediaMetadataRetriever reader=new MediaMetadataRetriever();Bitmap bitmap=null;
            try{
                reader.setDataSource(this,Uri.parse(track.uri));byte[] bytes=reader.getEmbeddedPicture();
                if(bytes==null||bytes.length>8*1024*1024)return;
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                if(bounds.outWidth<=0||bounds.outHeight<=0)return;
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>512)options.inSampleSize*=2;
                bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            }catch(Exception|OutOfMemoryError ignored){}finally{try{reader.release();}catch(Exception ignored){}}
            if(bitmap==null)return;Bitmap image=bitmap;
            handler.post(()->{if(ticket!=artworkGeneration||live!=this||playlist==null){image.recycle();return;}cover=image;session.setMetadata(metadata(track,duration));publish(true);});
        });
    }
    private void loadLyrics(LocalLibrary.Track track){
        int ticket=++lyricGeneration;if(lyricTask!=null)lyricTask.cancel(true);if(track.lyricUri.isEmpty())return;
        AppState state=AppState.get(this);int stateGeneration=state.generation;String key=state.song.key;
        lyricTask=lyricFiles.submit(()->{
            try(InputStream in=getContentResolver().openInputStream(Uri.parse(track.lyricUri))){
                String text=LyricParser.decodeBytes(IoSupport.read(in,2*1024*1024,20000));IoSupport.checkInterrupted();
                handler.post(()->{if(ticket==lyricGeneration&&live==this&&state.generation==stateGeneration&&key.equals(state.song.key))state.importLyrics(text);});
            }catch(Exception ignored){}
        });
    }
    private void resume(){
        resumeAfterFocus=false;if(playlist==null)return;
        if(player==null){load(true);return;}playWhenReady=true;
        if(prepared){if(requestFocus())try{player.start();}catch(IllegalStateException e){playWhenReady=false;}else{playWhenReady=false;toast("다른 앱에서 오디오를 사용 중입니다.");}}
        publish(false);
    }
    private void pause(boolean transientLoss){
        playWhenReady=false;if(!transientLoss)resumeAfterFocus=false;
        if(prepared&&player!=null)try{if(player.isPlaying())player.pause();}catch(IllegalStateException ignored){}
        if(!transientLoss)abandonFocus();publish(false);
    }
    private void seek(long value){
        if(!prepared||player==null)return;try{player.seekTo((int)Math.max(0,Math.min(duration>0?duration:Integer.MAX_VALUE,value)));}catch(IllegalStateException ignored){}publish(false);
    }
    private void step(int direction,boolean completed){
        if(playlist==null||playlist.tracks.isEmpty())return;int next=index;String mode=mode(this);int count=playlist.tracks.size();
        if(completed&&mode.equals("one"))next=index;
        else if(mode.equals("shuffle")&&count>1){
            if(direction<0&&!history.isEmpty())next=history.removeLast();
            else {history.addLast(index);if(history.size()>200)history.removeFirst();next=random.nextInt(count-1);if(next>=index)next++;}
        }else next=(index+direction+count)%count;
        index=next;load(true);
    }
    private void publish(boolean metadataChanged){
        if(session==null||playlist==null)return;long position=0;boolean playing=false;
        if(prepared&&player!=null)try{position=player.getCurrentPosition();playing=player.isPlaying();}catch(IllegalStateException ignored){}
        int status=playing?PlaybackState.STATE_PLAYING:!prepared&&playWhenReady?PlaybackState.STATE_BUFFERING:PlaybackState.STATE_PAUSED;
        long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_STOP;
        PlaybackState playback=new PlaybackState.Builder().setActions(actions).addCustomAction(STOP,"재생 중지",android.R.drawable.ic_menu_close_clear_cancel).setState(status,position,playing?1f:0f,SystemClock.elapsedRealtime()).build();
        session.setPlaybackState(playback);
        getSystemService(NotificationManager.class).notify(NOTIFICATION,notification());
        AppState state=AppState.get(this);
        if(playWhenReady||state.controller==null||state.controller.getSessionToken().equals(session.getSessionToken()))state.accept(session.getController(),playback,metadataChanged,false);
        LyricsListener.refresh(this);
    }
    private PendingIntent action(String action,int request){return PendingIntent.getService(this,request,new Intent(this,LocalPlaybackService.class).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private Notification notification(){
        LocalLibrary.Track track=playlist==null||index<0?null:playlist.tracks.get(index);boolean playing=false;
        if(prepared&&player!=null)try{playing=player.isPlaying();}catch(IllegalStateException ignored){}
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_launcher).setContentTitle(track==null?"음악 재생":track.title).setContentText(track==null?"":track.artist)
            .setContentIntent(PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE))
            .setCategory(Notification.CATEGORY_TRANSPORT).setVisibility(Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).setOngoing(playing)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,"이전 곡",action(PREVIOUS,1)).build())
            .addAction(new Notification.Action.Builder(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playing?"일시정지":"재생",action(playing?PAUSE:PLAY,2)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,"다음 곡",action(NEXT,3)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"재생 중지",action(STOP,4)).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2)).build();
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    private void stopPlayback(){playWhenReady=false;lyricGeneration++;artworkGeneration++;if(lyricTask!=null)lyricTask.cancel(true);if(artworkTask!=null)artworkTask.cancel(true);releasePlayer();abandonFocus();playlist=null;stopForeground(true);stopSelf();}
    @Override public void onDestroy(){
        if(live==this)live=null;artworkGeneration++;lyricGeneration++;if(artworkTask!=null)artworkTask.cancel(true);if(lyricTask!=null)lyricTask.cancel(true);artwork.shutdownNow();lyricFiles.shutdownNow();handler.removeCallbacksAndMessages(null);try{unregisterReceiver(noisy);}catch(Exception ignored){}releasePlayer();abandonFocus();
        AppState state=AppState.get(this);if(state.controller!=null&&session!=null&&state.controller.getSessionToken().equals(session.getSessionToken()))state.reset();
        if(session!=null){session.setActive(false);session.release();session=null;}LyricsListener.refresh(this);super.onDestroy();
    }
}
