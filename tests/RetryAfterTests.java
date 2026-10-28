import io.betterlyrics.mobile.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class RetryAfterTests {
    static int checks;
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static String request(String url)throws Exception{
        Method method=LyricsRepository.class.getDeclaredMethod("request",String.class,String.class);method.setAccessible(true);
        try{return (String)method.invoke(null,url,null);}catch(InvocationTargetException error){throw (Exception)error.getCause();}
    }
    static void respond(ServerSocket server,AtomicInteger count,String status,String extra,String body)throws Exception{
        try(Socket socket=server.accept()){
            count.incrementAndGet();BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
            for(String line;(line=input.readLine())!=null&&!line.isEmpty();){}
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);OutputStream out=socket.getOutputStream();
            out.write(("HTTP/1.1 "+status+"\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n"+extra+"\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
        }
    }
    public static void main(String[] args)throws Exception{
        long now=1700000000000L;SimpleDateFormat format=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);format.setTimeZone(TimeZone.getTimeZone("GMT"));
        ok(IoSupport.retryAfterMillis("2",now)==2000&&IoSupport.retryAfterMillis(format.format(new Date(now+3000)),now)==3000,"seconds and HTTP-date parsed");
        ok(IoSupport.retryAfterMillis("-1",now)<0&&IoSupport.retryAfterMillis("garbage",now)<0,"invalid headers are rejected");
        try(ServerSocket server=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"))){
            server.setSoTimeout(5000);AtomicInteger count=new AtomicInteger();Thread worker=new Thread(()->{try{respond(server,count,"503 Service Unavailable","Retry-After: 0\r\n","");respond(server,count,"200 OK","","ok");}catch(Exception error){throw new RuntimeException(error);}});worker.start();
            ok(request("http://127.0.0.1:"+server.getLocalPort()+"/").equals("ok"),"temporary 503 is retried once");worker.join(6000);ok(!worker.isAlive()&&count.get()==2,"retry count is bounded");
        }
        try(ServerSocket server=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"))){
            AtomicInteger count=new AtomicInteger();Thread worker=new Thread(()->{try{respond(server,count,"429 Too Many Requests","Retry-After: 999\r\n","");}catch(Exception error){throw new RuntimeException(error);}});worker.start();boolean failed=false;
            try{request("http://127.0.0.1:"+server.getLocalPort()+"/");}catch(IOException expected){failed=true;}worker.join(6000);
            ok(failed&&count.get()==1,"long server delay fails instead of retrying early or blocking");
        }
        System.out.println("RetryAfterTests: "+checks+" checks passed");
    }
}
