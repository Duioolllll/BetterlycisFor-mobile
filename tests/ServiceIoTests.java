import io.betterlyrics.mobile.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real stream/cache races and cooperative cancellation, with no live providers. */
public final class ServiceIoTests {
    static int checks;
    static void ok(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    interface Operation {void run()throws Exception;}
    static void fails(Class<? extends Exception> type,Operation op,String label)throws Exception {
        try{op.run();}catch(Exception error){ok(type.isInstance(error),label+": "+error);return;}
        throw new AssertionError(label+": no exception");
    }
    static LyricsRepository.Song song(){LyricsRepository.Song s=new LyricsRepository.Song();s.key="io-fixture";s.title="시험곡";s.artist="가수";s.duration=180000;return s;}
    static LyricsRepository.Result local(int index){LyricsRepository.Result r=new LyricsRepository.Result();r.provider="Local";r.duration=180000;r.raw="[00:01.00]원문 "+index+"\n[00:02.00]두 번째 줄";return r;}
    static LyricsRepository repository(Path path){return new LyricsRepository(path.toFile(),(url,body)->{throw new IOException("Unexpected network request on cache hit");});}
    static void streams()throws Exception {
        byte[] text="한국어 가사\nUTF-8".getBytes(StandardCharsets.UTF_8);
        AtomicBoolean closed=new AtomicBoolean();
        InputStream chunked=new ByteArrayInputStream(text){public synchronized int read(byte[] b,int off,int len){return super.read(b,off,Math.min(len,2));}public void close(){closed.set(true);}};
        ok(Arrays.equals(IoSupport.read(chunked,text.length,0),text),"small chunks preserve UTF-8 bytes and exact limit");
        ok(!closed.get(),"caller retains stream ownership");
        ok(IoSupport.read(new ByteArrayInputStream(new byte[0]),0,0).length==0,"zero-byte input at zero limit");
        AtomicInteger consumed=new AtomicInteger();
        InputStream oversized=new ByteArrayInputStream(new byte[100000]){public synchronized int read(byte[] b,int off,int len){int n=super.read(b,off,len);if(n>0)consumed.addAndGet(n);return n;}};
        fails(IoSupport.TooLargeException.class,()->IoSupport.read(oversized,17,0),"unknown-length input rejects excess");
        ok(consumed.get()==18,"oversize read stops at one-byte probe");
        fails(IOException.class,()->IoSupport.read(null,10,0),"missing stream is an I/O error");
        Thread.currentThread().interrupt();
        try{fails(InterruptedIOException.class,()->IoSupport.read(chunked,100,0),"cancel before a read");ok(Thread.currentThread().isInterrupted(),"cancellation preserves interrupt status");}finally{Thread.interrupted();}
        InputStream interrupting=new ByteArrayInputStream(text){public synchronized int read(byte[] b,int off,int len){Thread.currentThread().interrupt();return super.read(b,off,len);}};
        try{fails(InterruptedIOException.class,()->IoSupport.read(interrupting,100,0),"cancel during a read cannot publish data");}finally{Thread.interrupted();}
        InputStream stalled=new InputStream(){public int read(){return 0;}public int read(byte[] b,int off,int len){return 0;}};
        fails(InterruptedIOException.class,()->IoSupport.read(stalled,100,1),"zero-progress input observes its time budget");
        byte[] bytes=new byte[256];for(int i=0;i<256;i++)bytes[i]=(byte)i;
        StringBuilder lower=new StringBuilder(),upper=new StringBuilder();for(byte b:bytes){lower.append(String.format(Locale.ROOT,"%02x",b&255));upper.append(String.format(Locale.ROOT,"%02X",b&255));}
        ok(IoSupport.hex(bytes,false).equals(lower.toString()),"cache/MD5 lowercase hex preserves every byte");
        ok(IoSupport.hex(bytes,true).equals(upper.toString()),"EAPI uppercase hex preserves every byte");
    }
    static void cache()throws Exception {
        Path directory=Files.createTempDirectory("bl-io-cache-");LyricsRepository.Song s=song();
        try(LyricsRepository repository=repository(directory)){
            repository.save(s,local(0));
            String canonical=s.title+"|"+s.artist+"||180";
            String name=IoSupport.hex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)),false)+".json";
            ok(Files.isRegularFile(directory.resolve("lyrics").resolve(name)),"existing cache identity remains compatible");
            ExecutorService pool=Executors.newFixedThreadPool(4);CountDownLatch start=new CountDownLatch(1);
            try{
                List<Future<?>> jobs=new ArrayList<>();
                for(int worker=0;worker<4;worker++){final int id=worker;jobs.add(pool.submit(()->{start.await();for(int i=0;i<40;i++){if(id<2)repository.save(s,local(id*100+i));else if(!repository.fetch(s,"auto","best",false).cached)throw new AssertionError("partial cache read");}return null;}));}
                start.countDown();for(Future<?> job:jobs)job.get(15,TimeUnit.SECONDS);
            }finally{pool.shutdownNow();}
            ok(repository.fetch(s,"auto","best",false).cached,"parallel cache replacement/read stays valid without network fallback");
            try(java.util.stream.Stream<Path> files=Files.list(directory.resolve("lyrics"))){ok(files.noneMatch(p->p.toString().endsWith(".tmp")),"successful writes leave no temporary files");}
            byte[] before=Files.readAllBytes(directory.resolve("lyrics").resolve(name));Thread.currentThread().interrupt();
            try{repository.save(s,local(999));}finally{Thread.interrupted();}
            ok(Arrays.equals(before,Files.readAllBytes(directory.resolve("lyrics").resolve(name))),"cancelled write preserves previous cache entry");
            for(int i=0;i<205;i++){LyricsRepository.Song next=song();next.title+=" "+i;repository.save(next,local(i));}
            try(java.util.stream.Stream<Path> files=Files.list(directory.resolve("lyrics"))){ok(files.filter(p->p.toString().endsWith(".json")).count()==200,"cache eviction retains the 200-entry bound");}
            repository.clear();try(java.util.stream.Stream<Path> files=Files.list(directory.resolve("lyrics"))){ok(files.count()==0,"clear removes cached and temporary entries");}
        }
    }
    static void cancellation()throws Exception {
        Path directory=Files.createTempDirectory("bl-io-cancel-");AtomicInteger calls=new AtomicInteger();
        try(LyricsRepository repository=new LyricsRepository(directory.toFile(),(url,body)->{calls.incrementAndGet();Thread.currentThread().interrupt();return "[]";})){
            try{fails(InterruptedIOException.class,()->repository.fetch(song(),"auto","sequential",true),"cancelled provider response stops sequential fallback");}finally{Thread.interrupted();}
            ok(calls.get()==1,"cancelled search does not call the next provider");
            try(java.util.stream.Stream<Path> files=Files.list(directory.resolve("lyrics"))){ok(files.count()==0,"cancelled response is not cached");}
        }
        CountDownLatch started=new CountDownLatch(2),cancelled=new CountDownLatch(2),block=new CountDownLatch(1);AtomicReference<Throwable> result=new AtomicReference<>();
        try(LyricsRepository repository=new LyricsRepository(Files.createTempDirectory("bl-io-fanout-").toFile(),(url,body)->{started.countDown();try{block.await();return "[]";}catch(InterruptedException error){cancelled.countDown();throw error;}})){
            Thread parent=new Thread(()->{try{repository.fetch(song(),"auto","best",true);result.set(new AssertionError("cancelled fetch returned normally"));}catch(InterruptedException expected){}catch(Throwable error){result.set(error);}});
            parent.start();
            try{ok(started.await(5,TimeUnit.SECONDS),"both provider jobs started");parent.interrupt();parent.join(5000);ok(!parent.isAlive()&&result.get()==null,"cancelled parent exits cleanly");ok(cancelled.await(5,TimeUnit.SECONDS),"cancellation reaches both provider jobs");}
            finally{parent.interrupt();block.countDown();parent.join(5000);}
        }
    }
    public static void main(String[] args)throws Exception {streams();cache();cancellation();System.out.println("ServiceIoTests: "+checks+" checks passed");}
}
