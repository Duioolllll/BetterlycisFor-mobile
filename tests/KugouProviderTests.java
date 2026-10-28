import io.betterlyrics.mobile.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DeflaterOutputStream;
import org.json.*;

/** The actual provider pipeline with synthetic HTTP responses and a standard KRC container. */
public final class KugouProviderTests {
    static final String HASH="0123456789abcdef0123456789abcdef";
    static int checks;
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static byte[] krc(String text)throws Exception {
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        try(DeflaterOutputStream out=new DeflaterOutputStream(compressed)){out.write(text.getBytes(StandardCharsets.UTF_8));}
        byte[] key={64,71,97,119,94,50,116,71,81,54,49,45,(byte)206,(byte)210,110,105},data=compressed.toByteArray();
        ByteArrayOutputStream result=new ByteArrayOutputStream();result.write(new byte[]{'k','r','c','1'});for(int i=0;i<data.length;i++)result.write(data[i]^key[i%key.length]);return result.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        LyricsRepository.Song song=new LyricsRepository.Song();song.title="Fixture Song";song.artist="Fixture Artist";song.album="Fixture Album";song.duration=180000;song.key="fixture";
        JSONObject item=new JSONObject().put("hash",HASH).put("songname",song.title).put("singername",song.artist).put("album_name",song.album).put("duration",180);
        String raw="[10000,4000]<0,2000,0>fixture <2000,2000,0>words\n[20000,2000]<0,2000,0>second";
        String content=Base64.getEncoder().encodeToString(krc(raw));AtomicInteger downloads=new AtomicInteger();
        try(LyricsRepository repo=new LyricsRepository(Files.createTempDirectory("bl-kugou-").toFile(),(address,body)->{
            if(address.startsWith("https://mobilecdn.kugou.com/"))return new JSONObject().put("data",new JSONObject().put("info",new JSONArray().put(item).put(new JSONObject(item.toString()).put("hash","invalid")))).toString();
            if(address.startsWith("https://lyrics.kugou.com/search?"))return new JSONObject().put("candidates",new JSONArray()
                .put(new JSONObject().put("song","Different Song").put("singer",song.artist).put("duration",180000).put("id","3").put("accesskey","abc"))
                .put(new JSONObject().put("song",song.title).put("singer",song.artist).put("duration",180000).put("id","4").put("accesskey","secretABC"))).toString();
            if(address.startsWith("https://lyrics.kugou.com/download?")){downloads.incrementAndGet();if(!address.contains("id=4&"))throw new IOException("wrong candidate");return new JSONObject().put("content",content).toString();}
            throw new IOException("unexpected provider");
        })){
            List<LyricsRepository.Result> results=repo.search(song,"kugou");ok(results.size()==1&&results.get(0).id.equals("kugou:"+HASH),"invalid catalogue hashes are skipped");
            LyricsRepository.Result result=repo.fetch(song,"kugou","best",true);
            ok(result.provider.equals("Kugou")&&result.lines.size()==2&&result.lines.get(0).realTiming,"KRC is decoded and retains word timing");
            ok(result.lines.get(0).text.equals("fixture words")&&downloads.get()==1,"mismatched lyric candidate is not downloaded");
            LyricsRepository.Result cached=repo.fetch(song,"kugou","best",false);ok(cached.cached&&cached.lines.size()==2&&downloads.get()==1,"selected provider cache retains decoded lyrics");
        }
        AtomicInteger searches=new AtomicInteger();
        try(LyricsRepository repo=new LyricsRepository(Files.createTempDirectory("bl-kugou-limit-").toFile(),(address,body)->{
            if(address.contains("mobilecdn"))return new JSONObject().put("data",new JSONObject().put("info",new JSONArray().put(item).put(new JSONObject(item.toString()).put("hash","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")))).toString();
            searches.incrementAndGet();throw new LyricsRepository.ProviderUnavailableException(429);
        })){
            boolean failed=false;try{repo.fetch(song,"kugou","sequential",true);}catch(IOException expected){failed=true;}
            ok(failed&&searches.get()==1,"rate-limited provider is not queried for additional candidates");
        }
        System.out.println("KugouProviderTests: "+checks+" checks passed");
    }
}
