import io.betterlyrics.mobile.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.io.*;

/** Offline fixtures execute the production provider selection, hydration and cache logic. */
public final class RepositoryRegressionCheck {
    static int checks;
    static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static LyricsRepository.Song song(){LyricsRepository.Song s=new LyricsRepository.Song();s.title="시험곡";s.artist="테스트 가수";s.album="테스트 앨범";s.duration=180000;s.key="fixture";return s;}
    static JSONObject item(int id,String title,String artist,int seconds,String timed,String plain)throws Exception{
        return new JSONObject().put("id",id).put("trackName",title).put("artistName",artist).put("albumName","테스트 앨범").put("duration",seconds).put("syncedLyrics",timed==null?JSONObject.NULL:timed).put("plainLyrics",plain);
    }
    static LyricsRepository repo(Path path,JSONArray lrclib,JSONObject netease,JSONObject lyrics){return new LyricsRepository(path.toFile(),(address,body)->{
        if(address.contains("lrclib.net"))return lrclib.toString();
        if(address.contains("cloudsearch"))return netease==null?"{}":netease.toString();
        if(address.contains("song/lyric"))return lyrics==null?"{}":lyrics.toString();
        throw new IOException("Unexpected fixture URL");
    });}
    static JSONObject lyric(String raw)throws Exception{return new JSONObject().put("lyric",raw);}
    public static void main(String[] args)throws Exception{
        try{
            LyricsRepository.Song s=song();String korean="[00:10.00]가상의 한국어 첫 줄\n[00:20.00]두 번째 테스트 줄";
            JSONArray rows=new JSONArray().put(item(1,"아예 다른 곡",s.artist,180,korean,""));
            LyricsRepository repository=repo(Files.createTempDirectory("bl-wrong-"),rows,null,null);boolean rejected=false;
            try{repository.fetch(s,"lrclib","best",true);}catch(IOException expected){rejected=true;}
            ok(rejected,"wrong title rejected despite artist/album/duration matching");

            rows=new JSONArray().put(item(1,s.title,s.artist,180,null,"시간 없는 테스트 줄")).put(item(2,s.title,s.artist,181,korean,""));
            repository=repo(Files.createTempDirectory("bl-synced-"),rows,null,null);
            LyricsRepository.Result found=repository.fetch(s,"lrclib","best",true);
            ok(found.id.equals("lrclib:2")&&LyricsRepository.synced(found),"timed Korean lyrics outrank a slightly closer untimed record");
            ok(repository.fetch(s,"lrclib","best",false).cached,"new-policy automatic result survives cache round trip");

            JSONObject artist=new JSONObject().put("name","Test Singer").put("alias",new JSONArray().put(s.artist));
            JSONObject record=new JSONObject().put("id",42).put("name","Test Song").put("alia",new JSONArray().put(s.title)).put("dt",180000).put("ar",new JSONArray().put(artist)).put("al",new JSONObject().put("name",s.album));
            JSONObject netease=new JSONObject().put("result",new JSONObject().put("songs",new JSONArray().put(record)));
            JSONObject lyrics=new JSONObject().put("yrc",lyric("[10000,4000](10000,2000,0)가상의 (12000,2000,0)시험곡")).put("lrc",lyric(korean));
            repository=repo(Files.createTempDirectory("bl-alias-"),new JSONArray().put(item(1,s.title,s.artist,180,null,"plain test")),netease,lyrics);
            found=repository.fetch(s,"auto","best",true);
            ok(found.provider.equals("NetEase")&&found.lines.get(0).realTiming,"NetEase Korean aliases and timed lyrics beat untimed LRCLIB");
            ok(repository.fetch(s,"auto","best",false).cached,"provider aliases preserved in cache");
            repository=repo(Files.createTempDirectory("bl-sequential-"),new JSONArray().put(item(1,s.title,s.artist,180,null,"plain test")),netease,lyrics);
            ok(repository.fetch(s,"auto","sequential",true).provider.equals("NetEase"),"fast mode continues past untimed first provider");

            lyrics.put("yrc",lyric("[10000,4000](10000,2000,0)roman (12000,2000,0)words"));
            repository=repo(Files.createTempDirectory("bl-original-"),new JSONArray(),netease,lyrics);
            found=repository.fetch(s,"netease","best",true);
            ok(found.raw.equals(korean)&&!found.lines.get(0).realTiming,"romanized YRC does not replace Hangul original LRC");
            lyrics.put("yrc",lyric("[999999,4000](999999,2000,0)잘못된 시간"));
            repository=repo(Files.createTempDirectory("bl-fallback-"),new JSONArray(),netease,lyrics);
            ok(repository.fetch(s,"netease","best",true).raw.equals(korean),"out-of-range word timestamps fall back to line timestamps");

            Path directory=Files.createTempDirectory("bl-cache-");repository=repo(directory,new JSONArray().put(item(3,s.title,s.artist,180,korean,"")),null,null);
            found=repository.fetch(s,"lrclib","best",true);
            Path cached=Files.list(directory.resolve("lyrics")).filter(p->p.toString().endsWith(".json")).findFirst().get();
            JSONObject old=new JSONObject(new String(Files.readAllBytes(cached),StandardCharsets.UTF_8));old.remove("policy");old.put("raw","[00:01.00]obsolete test");Files.write(cached,old.toString().getBytes(StandardCharsets.UTF_8));
            found=repository.fetch(s,"lrclib","best",false);
            ok(!found.cached&&found.raw.equals(korean),"obsolete online cache is automatically refreshed");
            found.title="사용자가 고른 다른 표기";found.manual=true;repository.save(s,found);
            ok(repository.fetch(s,"lrclib","best",false).cached,"explicit manual choice is retained");
            old=new JSONObject(found.encode().toString()).put("provider","Local");old.remove("policy");Files.write(cached,old.toString().getBytes(StandardCharsets.UTF_8));
            ok(repository.fetch(s,"auto","best",false).provider.equals("Local"),"old imported lyrics are retained");
            String uncensored="[00:10.00]fuck this fictional test\n[00:20.00]씨발 테스트";
            String censored="[00:10.00]f**k this fictional test\n[00:20.00]＊＊＊ 테스트";
            rows=new JSONArray().put(item(1,s.title,s.artist,180,censored,"")).put(item(2,s.title,s.artist,181,uncensored,""));
            repository=repo(Files.createTempDirectory("bl-uncensored-"),rows,null,null);
            found=repository.fetch(s,"lrclib","best",true);
            ok(found.id.equals("lrclib:2")&&!found.masked,"uncensored actual source outranks censored candidate");
            ok(found.lines.get(0).text.equals("fuck this fictional test"),"source profanity is not modified");

            lyrics.put("yrc",lyric("[10000,3000](10000,3000,0)f**k test")).put("lrc",lyric(uncensored)).put("tlyric",lyric("[00:10.00]翻译文本"));
            repository=repo(Files.createTempDirectory("bl-yrc-censor-"),new JSONArray(),netease,lyrics);
            found=repository.fetch(s,"netease","best",true);
            ok(found.raw.equals(uncensored)&&!found.masked,"masked YRC uses real unmasked LRC instead of guessing");
            ok(found.lines.get(0).translation.isEmpty(),"Chinese NetEase translation omitted");
            repository=repo(Files.createTempDirectory("bl-source-censor-"),new JSONArray().put(item(1,s.title,s.artist,180,censored,"")),netease,lyrics);
            ok(repository.fetch(s,"auto","sequential",true).provider.equals("NetEase"),"fast mode searches next provider when first one is censored");

            JSONArray mutable=new JSONArray().put(item(1,s.title,s.artist,180,censored,""));
            repository=repo(Files.createTempDirectory("bl-only-censor-"),mutable,null,null);found=repository.fetch(s,"lrclib","best",true);
            ok(found.masked&&found.raw.equals(censored),"source-only masking is honestly flagged, not fabricated or deleted");
            mutable.put(0,item(1,s.title,s.artist,180,uncensored,""));
            found=repository.fetch(s,"lrclib","best",false);
            ok(!found.cached&&!found.masked,"masked automatic cache is retried for newly available originals");

            repository=repo(Files.createTempDirectory("bl-plain-original-"),new JSONArray().put(item(1,s.title,s.artist,180,censored,"fuck this fictional test\n씨발 테스트")),null,null);
            found=repository.fetch(s,"lrclib","best",true);
            ok(!found.masked&&!LyricsRepository.synced(found)&&found.lines.get(0).text.equals("fuck this fictional test"),"real unmasked plain text fallback does not invent timestamps");
            repository=repo(Files.createTempDirectory("bl-clean-edition-"),new JSONArray().put(item(1,s.title+" (Clean)",s.artist,180,uncensored,"")),null,null);rejected=false;
            try{repository.fetch(s,"lrclib","best",true);}catch(IOException expected){rejected=true;}
            ok(rejected,"clean edition is not automatically substituted for original");

            LyricsRepository.Result local=new LyricsRepository.Result();local.provider="Local";local.duration=180000;local.raw="[00:00.00]作词：Test Writer\n"+uncensored;repository.hydrate(local);
            ok(local.lines.size()==2&&local.lines.get(0).start==10000&&!local.masked,"local imports share Chinese credit cleanup and preserve source profanity/time");
            System.out.println("RepositoryRegressionCheck: "+checks+" checks passed");System.exit(0);
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
}
