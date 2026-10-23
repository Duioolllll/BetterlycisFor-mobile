package io.betterlyrics.mobile;

import android.content.*;
import android.media.MediaMetadata;
import android.media.AudioManager;
import android.media.session.*;
import android.os.*;
import android.provider.Settings;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class AppState {
    private static AppState instance;
    public static synchronized AppState get(Context c){if(instance==null)instance=new AppState(c.getApplicationContext());return instance;}
    final Context context;
    final Handler main=new Handler(Looper.getMainLooper());
    final SharedPreferences prefs;
    final LyricsRepository repository;
    final AudioEngine audio;
    final ThreadPoolExecutor network=(ThreadPoolExecutor)Executors.newFixedThreadPool(2);
    final ThreadPoolExecutor artwork=(ThreadPoolExecutor)Executors.newFixedThreadPool(1);
    interface Listener {void changed(boolean full);}
    final List<Listener> listeners=new ArrayList<>();
    final Map<String,LyricsRepository.Result> candidates=new HashMap<>();
    MediaController controller;
    private PlaybackState playback;
    JSONArray sources=new JSONArray();
    LyricsRepository.Song song=new LyricsRepository.Song();
    LyricsRepository.Result lyrics;
    String appName="",cover="",status="음악을 재생해 주세요.",error="";
    boolean connected,loading;
    int generation,searchGeneration;
    Future<?> pending;
    private Future<?> pendingSearch;
    Future<?> pendingArt;
    private String artFingerprint="";
    private int artGeneration;
    private long artRetryAt;
    private AppState(Context c){context=c;prefs=c.getSharedPreferences("settings",0);repository=new LyricsRepository(c);audio=new AudioEngine(c);}
    public boolean granted(){String enabled=Settings.Secure.getString(context.getContentResolver(),"enabled_notification_listeners");if(enabled==null)return false;ComponentName self=new ComponentName(context,LyricsListener.class);for(String entry:enabled.split(":"))if(self.equals(ComponentName.unflattenFromString(entry)))return true;return false;}
    public void emit(){notifyListeners(true);}
    private void notifyListeners(boolean full){for(Listener r:new ArrayList<>(listeners))r.changed(full);LyricsListener.refreshOverlay();}
    void cancelWork(Future<?> work){if(work!=null)work.cancel(true);network.purge();}
    public void accept(MediaController next,PlaybackState currentPlayback,boolean metadataChanged,boolean sourcesChanged) {
        boolean sameSession=controller!=null&&next!=null&&controller.getSessionToken().equals(next.getSessionToken());
        PlaybackState previous=playback;controller=next;playback=currentPlayback;
        boolean playbackChanged=(previous==null)!=(playback==null)||previous!=null&&playback!=null&&previous.getState()!=playback.getState();
        // Position/speed/action samples use the lightweight clock packet. Metadata
        // and track changes still publish a complete bootstrap, as do play/pause.
        if(!metadataChanged&&sameSession&&!song.key.isEmpty()){
            notifyListeners(sourcesChanged||playbackChanged);return;
        }
        MediaMetadata m=next==null?null:next.getMetadata();
        if(m==null||value(m,MediaMetadata.METADATA_KEY_TITLE).isEmpty()){if(!song.key.isEmpty())reset();else emit();return;}
        String title=value(m,MediaMetadata.METADATA_KEY_TITLE),artist=value(m,MediaMetadata.METADATA_KEY_ARTIST),album=value(m,MediaMetadata.METADATA_KEY_ALBUM);
        if(artist.isEmpty())artist=value(m,MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        String key=next.getPackageName()+"|"+value(m,MediaMetadata.METADATA_KEY_MEDIA_ID)+"|"+title+"|"+artist+"|"+album;
        long duration=Math.max(0,m.getLong(MediaMetadata.METADATA_KEY_DURATION));
        boolean changed=!key.equals(song.key);
        boolean durationChanged=duration>0&&song.duration!=duration;
        if(changed) {
            LyricsRepository.Song fresh=new LyricsRepository.Song();fresh.key=key;fresh.title=title;fresh.artist=artist;fresh.album=album;fresh.duration=duration;song=fresh;
            lyrics=null;cover="";candidates.clear();searchGeneration++;cancelWork(pendingSearch);
            try{appName=context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(next.getPackageName(),0)).toString();}catch(Exception e){appName=next.getPackageName();}
            fetch(false);
        }else if(durationChanged){
            song.duration=duration;
            if(lyrics==null||(!lyrics.manual&&!lyrics.provider.equals("Local")&&!LyricsRepository.eligible(song,lyrics)))fetch(true);
        }
        updateArtwork(m,changed);
        // Notification/session refreshes often repeat identical metadata. Artwork
        // completion emits its own full update if a new image actually loads.
        if(changed||durationChanged||sourcesChanged||playbackChanged)emit();else notifyListeners(false);
    }
    private void updateArtwork(MediaMetadata metadata,boolean changed){
        ArtworkResolver.Source primary=ArtworkResolver.metadata(metadata),fallback=LyricsListener.notificationArtwork(controller);
        String fingerprint=song.key+"|"+primary.fingerprint()+"|"+fallback.fingerprint();long now=SystemClock.elapsedRealtime();
        if(!changed&&fingerprint.equals(artFingerprint)&&(cover.length()>0||pendingArt!=null&&!pendingArt.isDone()||now<artRetryAt))return;
        artFingerprint=fingerprint;artRetryAt=now+10000;int ticket=++artGeneration;String key=song.key;
        if(pendingArt!=null)pendingArt.cancel(true);
        artwork.purge();
        if(primary.fingerprint().isEmpty()&&fallback.fingerprint().isEmpty())return;
        pendingArt=artwork.submit(()->{
            String loaded=ArtworkResolver.load(context,primary);if(loaded.isEmpty())loaded=ArtworkResolver.load(context,fallback);final String image=loaded;
            main.post(()->{if(ticket!=artGeneration||!key.equals(song.key)||image.isEmpty())return;cover=image;emit();});
        });
    }
    private String value(MediaMetadata m,String key){String v=m.getString(key);return v==null?"":v;}
    public void reset(){generation++;searchGeneration++;artGeneration++;artFingerprint="";if(pendingArt!=null)pendingArt.cancel(true);artwork.purge();cancelWork(pending);cancelWork(pendingSearch);song=new LyricsRepository.Song();controller=null;playback=null;lyrics=null;cover="";loading=false;status="음악을 재생해 주세요.";error="";emit();}
    public void fetch(boolean force){
        if(song.key.isEmpty())return;
        int ticket=++generation;LyricsRepository.Song selected=song.copy();cancelWork(pending);
        loading=true;status="가사를 찾고 있어요…";error="";emit();
        String provider=prefs.getString("provider","auto"),mode=prefs.getString("searchMode","best");
        pending=network.submit(()->{
            try{
                LyricsRepository.Result r=repository.fetch(selected,provider,mode,force);
                main.post(()->{if(ticket!=generation||!selected.key.equals(song.key))return;lyrics=r;loading=false;status=r.cached?"저장된 가사":"가사 연결됨";error="";emit();});
            }catch(Exception e){main.post(()->{if(ticket!=generation)return;loading=false;status="가사를 찾지 못했어요";error=e.getMessage()==null?"연결을 확인하고 다시 검색해 주세요.":e.getMessage();emit();});}
        });
    }
    public void importLyrics(String raw){
        if(song.key.isEmpty()){status="음악 앱에서 노래를 먼저 재생해 주세요.";emit();return;}
        final LyricsRepository.Song selected=song.copy();int ticket=++generation;cancelWork(pending);loading=true;emit();
        pending=network.submit(()->{try{LyricsRepository.Result r=new LyricsRepository.Result();r.title=selected.title;r.artist=selected.artist;r.album=selected.album;r.duration=selected.duration;r.provider="Local";r.raw=raw;r.score=100;r.trackKey=selected.key;repository.hydrate(r);repository.save(selected,r);IoSupport.checkInterrupted();main.post(()->{if(ticket!=generation)return;lyrics=r;loading=false;status="가사 파일을 불러왔어요";error="";emit();});}catch(Exception e){main.post(()->{if(ticket!=generation)return;loading=false;error=e.getMessage();status="가사 파일을 확인해 주세요";emit();});}});
    }
    public interface SearchCallback{void done(JSONObject response);}
    public void search(String title,String artist,SearchCallback callback){
        int ticket=++searchGeneration;cancelWork(pendingSearch);String trackKey=song.key;candidates.clear();LyricsRepository.Song q=song.copy();q.title=title.trim();q.artist=artist.trim();
        String provider=prefs.getString("provider","auto");
        pendingSearch=network.submit(()->{
            List<LyricsRepository.Result> all=new ArrayList<>();String failure="";
            for(String name:provider.equals("auto")?new String[]{"lrclib","netease"}:new String[]{provider}){
                if(Thread.currentThread().isInterrupted())return;
                try{all.addAll(repository.search(q,name));}catch(InterruptedException e){Thread.currentThread().interrupt();return;}catch(Exception e){if(Thread.currentThread().isInterrupted())return;failure="일부 가사 제공처에 연결하지 못했습니다.";}
            }
            if(Thread.currentThread().isInterrupted())return;
            all.sort((a,b)->Integer.compare(b.score,a.score));final String message=failure;
            main.post(()->{if(ticket!=searchGeneration||!trackKey.equals(song.key))return;JSONObject response=new JSONObject();JSONArray items=new JSONArray();try{for(LyricsRepository.Result r:all){candidates.put(r.id,r);items.put(r.candidate());}response.put("items",items).put("error",all.isEmpty()?(message.isEmpty()?"검색 결과가 없습니다.":message):"");}catch(Exception ignored){}callback.done(response);});
        });
    }
    public void choose(String id){
        LyricsRepository.Result r=candidates.get(id);if(r==null||!r.trackKey.equals(song.key))return;
        r.manual=true;
        int ticket=++generation;LyricsRepository.Song selected=song.copy();cancelWork(pending);loading=true;status="선택한 가사를 불러오는 중…";emit();
        pending=network.submit(()->{try{repository.hydrate(r);repository.save(selected,r);main.post(()->{if(ticket!=generation)return;lyrics=r;loading=false;status="선택한 가사";error="";emit();});}catch(Exception e){main.post(()->{if(ticket!=generation)return;loading=false;error=e.getMessage();emit();});}});
    }
    public long position(){PlaybackState p=playback;if(p==null)return 0;return MatchScore.position(p.getPosition(),p.getLastPositionUpdateTime(),SystemClock.elapsedRealtime(),p.getPlaybackSpeed(),p.getState()==PlaybackState.STATE_PLAYING,song.duration);}
    public boolean playing(){PlaybackState p=playback;return p!=null&&p.getState()==PlaybackState.STATE_PLAYING;}
    private String offsetKey(){return "trackOffset:"+song.key;}
    public int offset(){return song.key.isEmpty()?0:prefs.getInt(offsetKey(),0);}
    public JSONObject track()throws JSONException{
        PlaybackState p=playback;
        long now=SystemClock.elapsedRealtime(),position=p==null?0:MatchScore.position(p.getPosition(),p.getLastPositionUpdateTime(),now,p.getPlaybackSpeed(),p.getState()==PlaybackState.STATE_PLAYING,song.duration);
        return new JSONObject().put("key",song.key).put("title",song.title).put("artist",song.artist).put("album",song.album).put("duration",song.duration).put("packageName",controller==null?"":controller.getPackageName()).put("appName",appName).put("cover",cover).put("position",position).put("sampledAt",now).put("positionKnown",p!=null&&p.getPosition()>=0).put("speed",p==null?1:p.getPlaybackSpeed()).put("playing",p!=null&&p.getState()==PlaybackState.STATE_PLAYING).put("actions",p==null?0:p.getActions());
    }
    public JSONObject volume(){
        JSONObject result=new JSONObject();
        try{AudioManager manager=context.getSystemService(AudioManager.class);int max=manager==null?0:manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            result.put("available",manager!=null&&!manager.isVolumeFixed()&&max>0).put("max",max).put("level",manager==null?0:manager.getStreamVolume(AudioManager.STREAM_MUSIC));
        }catch(Exception ignored){try{result.put("available",false);}catch(JSONException ignoredJson){}}
        return result;
    }
    public JSONObject tick(){try{JSONObject t=track();t.remove("cover");return new JSONObject().put("tick",true).put("track",t).put("volume",volume());}catch(Exception e){return new JSONObject();}}
    public JSONObject snapshot(){
        JSONObject root=new JSONObject();try{
            JSONArray rows=new JSONArray();if(lyrics!=null)for(LyricParser.Line l:lyrics.lines){JSONArray words=new JSONArray();for(LyricParser.Word w:l.words)words.put(new JSONObject().put("start",w.start).put("end",w.end).put("text",w.text));rows.put(new JSONObject().put("start",l.start).put("end",l.end).put("text",l.text).put("translation",l.translation).put("romanization",l.romanization).put("agent",l.agent).put("bg",l.bg).put("realTiming",l.realTiming).put("words",words));}
            JSONObject lyric=new JSONObject().put("revision",generation).put("lines",rows).put("provider",lyrics==null?"":lyrics.provider).put("score",lyrics==null?0:lyrics.score).put("status",status).put("error",error).put("loading",loading).put("masked",lyrics!=null&&lyrics.masked).put("synced",lyrics!=null&&!lyrics.lines.isEmpty()&&lyrics.lines.get(0).start>=0);
            JSONObject settings=new JSONObject().put("offset",offset()).put("fontSize",prefs.getInt("fontSize",28)).put("wordMode",prefs.getString("wordMode","auto")).put("provider",prefs.getString("provider","auto")).put("searchMode",prefs.getString("searchMode","best")).put("keepScreen",prefs.getBoolean("keepScreen",true)).put("overlay",prefs.getBoolean("overlay",false));
            settings.put("backgroundMode",prefs.getString("backgroundMode","sound")).put("backgroundIntensity",prefs.getInt("backgroundIntensity",70)).put("backgroundParticles",prefs.getBoolean("backgroundParticles",true)).put("audioEnabled",prefs.getBoolean("audioEnabled",false));
            settings.put("appearance",UiAppearance.parse(prefs.getString("appearance","{}")));
            root.put("audio",audio.packet()).put("volume",volume());
            root.put("track",track()).put("lyrics",lyric).put("settings",settings).put("permissions",new JSONObject().put("notifications",granted()).put("overlay",Settings.canDrawOverlays(context)).put("connected",connected)).put("sources",sources);
        }catch(Exception ignored){}return root;
    }
    public void set(String key,Object value){
        SharedPreferences.Editor e=prefs.edit();
        if(key.equals("offset"))e.putInt(offsetKey(),Math.max(-10000,Math.min(10000,((Number)value).intValue())));
        else if(key.equals("fontSize"))e.putInt(key,Math.max(20,Math.min(48,((Number)value).intValue())));
        else if(key.equals("keepScreen")||key.equals("overlay"))e.putBoolean(key,Boolean.TRUE.equals(value));
        else if(key.equals("wordMode")&&Arrays.asList("auto","always","never").contains(String.valueOf(value)))e.putString(key,String.valueOf(value));
        else if(key.equals("provider")&&Arrays.asList("auto","lrclib","netease").contains(String.valueOf(value)))e.putString(key,String.valueOf(value));
        else if(key.equals("searchMode")&&Arrays.asList("sequential","best").contains(String.valueOf(value)))e.putString(key,String.valueOf(value));
        else if(key.equals("backgroundMode")&&Arrays.asList("sound","ambient","static").contains(String.valueOf(value)))e.putString(key,String.valueOf(value));
        else if(key.equals("backgroundIntensity"))e.putInt(key,Math.max(0,Math.min(100,((Number)value).intValue())));
        else if(key.equals("backgroundParticles"))e.putBoolean(key,Boolean.TRUE.equals(value));
        e.apply();if(key.equals("provider")||key.equals("searchMode"))fetch(true);else emit();
    }
}
