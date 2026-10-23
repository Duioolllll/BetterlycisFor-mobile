package io.betterlyrics.mobile;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.nio.file.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import java.util.concurrent.*;

/** Native HTTPS requests avoid WebView CORS and keep web content offline. */
public final class LyricsRepository implements AutoCloseable {
    public static final class Song {
        public String title="",artist="",album="",key="";
        public long duration;
        Song copy(){Song copy=new Song();copy.title=title;copy.artist=artist;copy.album=album;copy.key=key;copy.duration=duration;return copy;}
    }
    public static final class Result {
        public String id=UUID.randomUUID().toString(),title="",artist="",album="",provider="",raw="",plainRaw="",translation="",romanization="",trackKey="";
        public long duration;
        public int score;
        public boolean cached,manual,masked;
        public final List<String> titleAliases=new ArrayList<>(),artistAliases=new ArrayList<>();
        public List<LyricParser.Line> lines=new ArrayList<>();
        public JSONObject candidate() throws JSONException {
            return new JSONObject().put("id",id).put("title",title).put("artist",artist).put("album",album).put("provider",provider).put("duration",duration).put("score",score);
        }
        public JSONObject encode() throws JSONException {
            return candidate().put("raw",raw).put("plainRaw",plainRaw).put("translation",translation).put("romanization",romanization).put("policy",3).put("manual",manual).put("titleAliases",new JSONArray(titleAliases)).put("artistAliases",new JSONArray(artistAliases));
        }
    }
    private final File cache;
    private static final int MAX_BYTES=3*1024*1024;
    private final Object cacheLock=new Object();
    public interface Transport { String request(String address,String body)throws Exception; }
    private final Transport transport;
    private final ThreadPoolExecutor providers=(ThreadPoolExecutor)Executors.newFixedThreadPool(2);
    public LyricsRepository(Context context) { this(context.getFilesDir()); }
    public LyricsRepository(File filesDirectory) { this(filesDirectory,LyricsRepository::request); }
    public LyricsRepository(File filesDirectory,Transport transport) { cache=new File(filesDirectory,"lyrics");cache.mkdirs();this.transport=transport; }
    private static String query(String s) throws Exception { return URLEncoder.encode(s,"UTF-8"); }
    private String exchange(String address,String body)throws Exception {IoSupport.checkInterrupted();String text=transport.request(address,body);IoSupport.checkInterrupted();return text;}
    private String get(String address) throws Exception { return exchange(address,null); }
    private static String request(String address,String body) throws Exception {
        IoSupport.checkInterrupted();
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","BetterLyricsMobile/0.4.3 (Android; https://github.com/Duioolllll/BetterlycisFor-mobile)");
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("Referer","https://music.163.com/");
        try {
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");byte[] payload=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(payload.length);try(OutputStream out=c.getOutputStream()){out.write(payload);}}
            int status=c.getResponseCode();if(status==404)return "";
            if(status!=200)throw new IOException("HTTP "+status);
            if(c.getContentLengthLong()>MAX_BYTES)throw new IOException("응답이 너무 큽니다.");
            try(InputStream in=c.getInputStream()) {
                return new String(IoSupport.read(in,MAX_BYTES,20000),StandardCharsets.UTF_8);
            }
        }finally{c.disconnect();}
    }
    /** Port of the shipped Lyricify helper's EapiHelper, using public protocol constants. */
    private String eapi(String path,JSONObject payload) throws Exception {
        String raw=payload.toString(),message="nobody"+path+"use"+raw+"md5forencrypt";
        String md5=IoSupport.hex(MessageDigest.getInstance("MD5").digest(message.getBytes(StandardCharsets.UTF_8)),false);
        byte[] data=(path+"-36cd479b6b5-"+raw+"-36cd479b6b5-"+md5).getBytes(StandardCharsets.UTF_8);
        Cipher aes=Cipher.getInstance("AES/ECB/PKCS5Padding");aes.init(Cipher.ENCRYPT_MODE,new SecretKeySpec("e82ckenh8dichen8".getBytes(StandardCharsets.US_ASCII),"AES"));String encrypted=IoSupport.hex(aes.doFinal(data),true);
        return exchange("https://interface.music.163.com/eapi/"+path.substring("/api/".length()),"params="+encrypted);
    }
    public List<Result> search(Song song,String source) throws Exception {
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        List<Result> result=new ArrayList<>();
        if(source.equals("lrclib")) {
            String title=MatchScore.searchTitle(song.title),artist=MatchScore.searchArtist(song.artist);
            String text=get("https://lrclib.net/api/search?track_name="+query(song.title)+"&artist_name="+query(artist));
            JSONArray a=text.isEmpty()?new JSONArray():new JSONArray(text);
            // Bilingual/featured titles often have a different spelling in the catalogue.
            if(a.length()==0&&!title.equals(song.title)&&!title.isEmpty()){
                text=get("https://lrclib.net/api/search?track_name="+query(title)+"&artist_name="+query(artist));a=text.isEmpty()?new JSONArray():new JSONArray(text);
            }
            for(int i=0;i<Math.min(40,a.length());i++) {
                JSONObject item=a.getJSONObject(i);Result r=new Result();r.provider="LRCLIB";
                r.id="lrclib:"+item.optLong("id");r.title=item.optString("trackName");r.artist=item.optString("artistName");r.album=item.optString("albumName");r.duration=Math.round(item.optDouble("duration",0)*1000);
                r.raw=item.isNull("syncedLyrics")?"":item.optString("syncedLyrics","");
                r.plainRaw=item.isNull("plainLyrics")?"":item.optString("plainLyrics","");
                if(r.raw.trim().isEmpty())r.raw=r.plainRaw;
                if(r.raw.trim().isEmpty())continue;
                r.trackKey=song.key;score(song,r);
                result.add(r);
            }
        } else if(source.equals("netease")) {
            JSONObject payload=new JSONObject().put("s",MatchScore.searchTitle(song.title)+" "+MatchScore.searchArtist(song.artist)).put("type","1").put("limit","20").put("offset","0").put("total","true");
            String text=eapi("/api/cloudsearch/pc",payload);
            if(text.isEmpty())return result;
            JSONObject obj=new JSONObject(text).optJSONObject("result");JSONArray a=obj==null?null:obj.optJSONArray("songs");
            if(a!=null)for(int i=0;i<Math.min(20,a.length());i++) {
                JSONObject item=a.getJSONObject(i);Result r=new Result();r.provider="NetEase";r.title=item.optString("name");r.id="netease:"+item.optLong("id");
                JSONArray artists=item.optJSONArray("artists");if(artists==null)artists=item.optJSONArray("ar");StringBuilder names=new StringBuilder();
                aliases(item,r.titleAliases);
                if(artists!=null)for(int k=0;k<artists.length();k++){JSONObject person=artists.getJSONObject(k);if(k>0)names.append(", ");names.append(person.optString("name"));aliases(person,r.artistAliases);}
                r.artist=names.toString();JSONObject album=item.optJSONObject("album");if(album==null)album=item.optJSONObject("al");r.album=album==null?"":album.optString("name");r.duration=item.optLong("duration",item.optLong("dt"));r.trackKey=song.key;
                score(song,r);result.add(r);
            }
        }
        result.sort((a,b)->Integer.compare(b.score,a.score));return result;
    }
    private static void aliases(JSONObject item,List<String> out){for(String key:new String[]{"alia","alias","tns","transNames"}){
        JSONArray a=item.optJSONArray(key);if(a!=null)for(int i=0;i<Math.min(20,a.length());i++){String s=a.optString(i,"");if(!s.trim().isEmpty()&&!out.contains(s))out.add(s);}
    }}
    private static String matching(String expected,String main,List<String> aliases,boolean artist){
        String best=main;double similarity=MatchScore.textSimilarity(expected,main,artist);
        for(String alias:aliases){double next=MatchScore.textSimilarity(expected,alias,artist);if(next>similarity){best=alias;similarity=next;}}return best;
    }
    private static void score(Song s,Result r){r.score=MatchScore.score(s.title,s.artist,s.album,s.duration,matching(s.title,r.title,r.titleAliases,false),matching(s.artist,r.artist,r.artistAliases,true),r.album,r.duration);}
    public static boolean eligible(Song s,Result r){return !(LyricTextPolicy.cleanEdition(r.title)&&!LyricTextPolicy.cleanEdition(s.title))&&MatchScore.sameVersion(s.title,r.title)&&MatchScore.automatic(s.title,s.artist,s.duration,matching(s.title,r.title,r.titleAliases,false),matching(s.artist,r.artist,r.artistAliases,true),r.duration);}
    public static boolean synced(Result r){for(LyricParser.Line line:r.lines)if(line.start>=0&&!line.text.trim().isEmpty())return true;return false;}
    private static boolean plausible(Result r){
        int valid=0,beyond=0;for(LyricParser.Line line:r.lines)if(line.start>=0&&!line.text.trim().isEmpty()){valid++;if(r.duration>0&&line.start>r.duration+2000)beyond++;}
        return valid==0||beyond==0;
    }
    private static int quality(Song s,Result r){
        int score=r.score+(synced(r)?20:0)-(r.masked?50:0);boolean words=false;for(LyricParser.Line l:r.lines)if(l.realTiming){words=true;break;}
        if(words)score+=3;
        // This is a preference, not a language ban: Korean artists also sing in English.
        if(MatchScore.hasHangul(s.title)&&MatchScore.hasHangul(r.raw))score+=8;
        return score;
    }
    public Result hydrate(Result r) throws Exception {
        IoSupport.checkInterrupted();
        if(r.provider.equals("NetEase")&&r.raw.isEmpty()) {
            String id=r.id.substring("netease:".length());
            JSONObject payload=new JSONObject().put("id",id).put("cp","false").put("lv","0").put("kv","0").put("tv","0").put("rv","0").put("yv","0").put("ytv","0").put("yrv","0").put("csrf_token","");
            JSONObject item=new JSONObject(eapi("/api/song/lyric/v1",payload));
            String line=lyric(item,"lrc"),word=lyric(item,"yrc");
            boolean useWord=!word.trim().isEmpty()&&!(MatchScore.hasHangul(line)&&!MatchScore.hasHangul(word));
            if(useWord)try{
                r.lines=LyricParser.parse(word,r.duration);LyricTextPolicy.apply(r.lines);useWord=synced(r)&&plausible(r);
                // Compare actual lyric rows so a contributor's decorative '*' is not mistaken for censorship.
                if(useWord&&LyricTextPolicy.maskCount(r.lines)>0&&!line.trim().isEmpty()){
                    List<LyricParser.Line> alternative=LyricParser.parse(line,r.duration);LyricTextPolicy.apply(alternative);
                    if(!alternative.isEmpty()&&LyricTextPolicy.maskCount(alternative)<LyricTextPolicy.maskCount(r.lines))useWord=false;
                }
            }catch(Exception ignored){useWord=false;}
            r.raw=useWord?word:line;
            r.translation=lyric(item,useWord?"ytlrc":"tlyric");r.romanization=lyric(item,useWord?"yromalrc":"romalrc");
            if(r.translation.isEmpty())r.translation=lyric(item,"tlyric");if(r.romanization.isEmpty())r.romanization=lyric(item,"romalrc");
        }
        if(r.raw.trim().isEmpty())throw new IOException("이 결과에는 가사가 없습니다.");
        r.lines=LyricParser.parse(r.raw,r.duration);
        LyricParser.applyTranslation(r.lines,r.translation,r.romanization);
        LyricTextPolicy.apply(r.lines);
        if(LyricTextPolicy.maskCount(r.lines)>0&&!r.plainRaw.trim().isEmpty()){
            List<LyricParser.Line> alternative=LyricParser.parse(r.plainRaw,r.duration);LyricTextPolicy.apply(alternative);
            if(!alternative.isEmpty()&&LyricTextPolicy.maskCount(alternative)<LyricTextPolicy.maskCount(r.lines)){r.raw=r.plainRaw;r.lines=alternative;}
        }
        r.masked=LyricTextPolicy.maskCount(r.lines)>0;
        IoSupport.checkInterrupted();
        if(r.lines.isEmpty())throw new IOException("가사 시간 정보를 읽지 못했습니다.");
        return r;
    }
    private static String lyric(JSONObject item,String key){JSONObject v=item.optJSONObject(key);return v==null?"":v.optString("lyric","");}
    public Result fetch(Song song,String source,String mode,boolean force) throws Exception {
        IoSupport.checkInterrupted();
        if(!force){Result r=load(song);if(r!=null&&sourceAllows(source,r.provider))return r;}
        String[] active=source.equals("auto")?new String[]{"lrclib","netease"}:new String[]{source};
        Result best=null;Exception last=null;
        if(mode.equals("best")&&active.length>1) {
            List<Future<Result>> tasks=new ArrayList<>();
            for(String name:active)tasks.add(providers.submit(()->bestProvider(song,name)));
            try {
                for(Future<Result> future:tasks)try{Result r=future.get();if(r!=null&&(best==null||quality(song,r)>quality(song,best)))best=r;}catch(ExecutionException e){last=new IOException("가사 제공처 연결 실패",e);}
            }finally{for(Future<Result> task:tasks)if(!task.isDone())task.cancel(true);providers.purge();}
        }else for(String name:active)try{Result r=bestProvider(song,name);if(r!=null&&(best==null||quality(song,r)>quality(song,best)))best=r;if(best!=null&&synced(best)&&!best.masked)break;}catch(InterruptedException e){throw e;}catch(Exception e){IoSupport.checkInterrupted();last=e;}
        IoSupport.checkInterrupted();
        if(best==null)throw new IOException(last==null?"일치하는 가사를 찾지 못했습니다.":"가사 제공처 응답이 없거나 일치하는 가사가 없습니다. 재검색 또는 파일 불러오기를 이용해 주세요.",last);
        save(song,best);return best;
    }
    private Result bestProvider(Song song,String provider) throws Exception {
        Result best=null;int attempts=0;
        for(Result r:search(song,provider)) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            if(r.score<70||!eligible(song,r))continue;
            try{hydrate(r);if(!plausible(r))continue;if(best==null||quality(song,r)>quality(song,best))best=r;}catch(InterruptedException e){throw e;}catch(Exception ignored){IoSupport.checkInterrupted();}
            if(provider.equals("netease")&&++attempts>=(best!=null&&best.masked?6:3))break;
        }
        return best;
    }
    private boolean sourceAllows(String setting,String provider) { return setting.equals("auto")||provider.equals("Local")||setting.equals("lrclib")&&provider.equals("LRCLIB")||setting.equals("netease")&&provider.equals("NetEase"); }
    private File file(Song s) throws Exception {
        String key=s.title.trim()+"|"+s.artist.trim()+"|"+s.album.trim()+"|"+Math.round(s.duration/1000.0);
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));return new File(cache,IoSupport.hex(digest,false)+".json");
    }
    private Result load(Song song) {
        try{
            File f=file(song);byte[] bytes;
            synchronized(cacheLock){
                if(!f.isFile()||f.length()>MAX_BYTES)return null;
                try(InputStream in=new FileInputStream(f)){bytes=IoSupport.read(in,MAX_BYTES,0);}
            }
            JSONObject j=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(j.optInt("policy")!=3&&!j.optString("provider").equals("Local"))return null;
            Result r=new Result();r.title=j.optString("title");r.artist=j.optString("artist");r.album=j.optString("album");r.provider=j.optString("provider");r.duration=j.optLong("duration");r.raw=j.optString("raw");r.translation=j.optString("translation");r.romanization=j.optString("romanization");r.score=j.optInt("score");r.trackKey=song.key;r.cached=true;
            r.plainRaw=j.optString("plainRaw");
            r.manual=j.optBoolean("manual",false);JSONArray ta=j.optJSONArray("titleAliases"),aa=j.optJSONArray("artistAliases");
            if(ta!=null)for(int i=0;i<ta.length();i++)r.titleAliases.add(ta.optString(i));if(aa!=null)for(int i=0;i<aa.length();i++)r.artistAliases.add(aa.optString(i));
            if(!r.manual&&!r.provider.equals("Local")&&!eligible(song,r))return null;
            hydrate(r);
            return r.masked&&!r.manual&&!r.provider.equals("Local")?null:r;
        }catch(Exception e){return null;}
    }
    public void save(Song song,Result r) {
        if(r.raw.trim().isEmpty())return;
        try {
            IoSupport.checkInterrupted();
            byte[] bytes=r.encode().toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)return;
            File f=file(song);
            synchronized(cacheLock){
                IoSupport.checkInterrupted();boolean added=!f.isFile();
                File temp=File.createTempFile("lyrics-",".tmp",cache);
                try{
                    try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);}
                    IoSupport.checkInterrupted();
                    try{Files.move(temp.toPath(),f.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                    catch(AtomicMoveNotSupportedException unsupported){Files.move(temp.toPath(),f.toPath(),StandardCopyOption.REPLACE_EXISTING);}
                }finally{temp.delete();}
                // Replacing an existing entry cannot increase the cache size.
                if(added){
                    File[] all=cache.listFiles((d,n)->n.endsWith(".json"));
                    if(all!=null&&all.length>200){
                        Map<File,Long> modified=new HashMap<>();for(File entry:all)modified.put(entry,entry.lastModified());
                        Arrays.sort(all,Comparator.comparingLong(modified::get));for(int i=0;i<all.length-200;i++)all[i].delete();
                    }
                }
            }
        }catch(Exception ignored){}
    }
    public void clear() {synchronized(cacheLock){File[] all=cache.listFiles();if(all!=null)for(File f:all)f.delete();}}
    @Override public void close(){providers.shutdownNow();}
}
