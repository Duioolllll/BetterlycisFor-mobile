package io.betterlyrics.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.HttpsURLConnection;

/** Spotify authorization-code PKCE and read-only playlist access; no client secret is used. */
final class SpotifyClient {
    private static final String CLIENT_ID="32571da108f24ff8a022771cfdc65cf8";
    private static final String KEY_ALIAS="betterlyrics.spotify.v1";
    private static SpotifyClient instance;
    static synchronized SpotifyClient get(Context context){if(instance==null)instance=new SpotifyClient(context.getApplicationContext());return instance;}
    interface Callback {void done(JSONObject result);}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile int epoch;
    private volatile boolean connecting;
    private volatile ServerSocket callbackServer;
    private String access="",refresh="";private long expires;
    private SpotifyClient(Context context){this.context=context;loadTokens();}
    synchronized boolean connected(){return !refresh.isEmpty();}
    private JSONObject object(Object... values){
        JSONObject result=new JSONObject();try{for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);}catch(Exception error){throw new IllegalStateException(error);}return result;
    }
    private void deliver(int ticket,Callback callback,JSONObject result){main.post(()->{if(ticket==epoch)callback.done(result);});}
    private JSONObject failure(String message){return object("view","error","connected",connected(),"message",message);}
    private void closeServer(){ServerSocket server=callbackServer;callbackServer=null;if(server!=null)try{server.close();}catch(IOException ignored){}}
    synchronized void disconnect(){epoch++;connecting=false;closeServer();access="";refresh="";expires=0;context.getSharedPreferences("spotify_auth",0).edit().clear().apply();}

    void connect(Activity activity,Callback callback){
        final int ticket;final String verifier=SpotifyProtocol.random(),state=SpotifyProtocol.random();final ServerSocket server;final String redirect;
        synchronized(this){
            if(connecting){callback.done(failure("Spotify 연결을 진행 중입니다."));return;}
            ticket=++epoch;connecting=true;
            try{server=new ServerSocket();server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),4);server.setSoTimeout(1500);callbackServer=server;redirect="http://127.0.0.1:"+server.getLocalPort()+"/callback";}
            catch(Exception error){connecting=false;callback.done(failure("Spotify 로그인 연결을 열지 못했습니다."));return;}
        }
        try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(SpotifyProtocol.authorize(CLIENT_ID,redirect,state,verifier))));}
        catch(Exception error){synchronized(this){connecting=false;closeServer();}callback.done(failure("브라우저를 열지 못했습니다."));return;}
        worker.execute(()->{
            try{
                SpotifyProtocol.Callback response=awaitCallback(server,state,ticket);
                if(response.error!=null)throw new IOException("Spotify 연결이 취소되었습니다.");
                String body="grant_type=authorization_code&client_id="+SpotifyProtocol.encode(CLIENT_ID)+"&code="+SpotifyProtocol.encode(response.code)+"&redirect_uri="+SpotifyProtocol.encode(redirect)+"&code_verifier="+SpotifyProtocol.encode(verifier);
                JSONObject token=tokenRequest(body);
                synchronized(this){if(ticket!=epoch)return;storeTokens(token,false);connecting=false;}
                loadPlaylists(ticket,callback);
            }catch(Exception error){synchronized(this){if(ticket!=epoch)return;connecting=false;}deliver(ticket,callback,failure(safeMessage(error)));}
            finally{if(callbackServer==server)closeServer();else try{server.close();}catch(IOException ignored){}}
        });
    }
    private SpotifyProtocol.Callback awaitCallback(ServerSocket server,String state,int ticket)throws Exception {
        long deadline=System.currentTimeMillis()+180000;
        while(ticket==epoch&&System.currentTimeMillis()<deadline){
            try(Socket socket=server.accept()){
                socket.setSoTimeout(5000);String line=readLine(socket.getInputStream(),8192);String[] parts=line.split(" ",3);
                if(parts.length!=3||!"GET".equals(parts[0])){respond(socket,400);continue;}
                int headerBytes=0;while(true){String header=readLine(socket.getInputStream(),8192);headerBytes+=header.length();if(header.isEmpty())break;if(headerBytes>32768)throw new IOException("요청이 너무 큽니다.");}
                SpotifyProtocol.Callback response;
                try{response=SpotifyProtocol.callback(parts[1],state);}catch(IllegalArgumentException invalid){respond(socket,400);continue;}
                respond(socket,200);return response;
            }catch(SocketTimeoutException ignored){}catch(IllegalArgumentException ignored){}
        }
        throw new IOException("Spotify 로그인 시간이 초과되었습니다. 다시 연결해 주세요.");
    }
    private static String readLine(InputStream in,int limit)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        while(out.size()<limit){int b=in.read();if(b<0||b=='\n')break;if(b!='\r')out.write(b);}
        if(out.size()>=limit)throw new IOException("요청이 너무 깁니다.");return new String(out.toByteArray(),StandardCharsets.UTF_8);
    }
    private static void respond(Socket socket,int status)throws IOException {
        byte[] body=(status==200?"BetterLyrics 앱으로 돌아가 주세요.":"잘못된 요청입니다.").getBytes(StandardCharsets.UTF_8);
        OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+(status==200?" OK":" Bad Request")+"\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: "+body.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();
    }
    private static final class StatusException extends IOException {final int status;StatusException(int status){super("Spotify HTTP "+status);this.status=status;}}
    private JSONObject request(String address,String method,String bearer,String form)throws Exception {
        URL url=new URL(address);if(!"https".equals(url.getProtocol())||!(url.getHost().equals("accounts.spotify.com")||url.getHost().equals("api.spotify.com")))throw new IOException("잘못된 Spotify 주소입니다.");
        HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();
        try{
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(12000);connection.setReadTimeout(15000);connection.setRequestMethod(method);connection.setRequestProperty("Accept","application/json");
            if(bearer!=null)connection.setRequestProperty("Authorization","Bearer "+bearer);
            if(form!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");byte[] bytes=form.getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
            int status=connection.getResponseCode();if(status!=200)throw new StatusException(status);
            try(InputStream in=connection.getInputStream()){return new JSONObject(new String(IoSupport.read(in,2*1024*1024,20000),StandardCharsets.UTF_8));}
        }finally{connection.disconnect();}
    }
    private JSONObject tokenRequest(String form)throws Exception{return request("https://accounts.spotify.com/api/token","POST",null,form);}
    private String accessToken()throws Exception {
        final String previous;final int ticket;
        synchronized(this){
            if(!access.isEmpty()&&System.currentTimeMillis()+60000<expires)return access;
            if(refresh.isEmpty())throw new IOException("Spotify에 다시 연결해 주세요.");
            previous=refresh;ticket=epoch;
        }
        JSONObject token=tokenRequest("grant_type=refresh_token&refresh_token="+SpotifyProtocol.encode(previous)+"&client_id="+SpotifyProtocol.encode(CLIENT_ID));
        synchronized(this){if(ticket!=epoch)throw new IOException("Spotify 연결이 취소되었습니다.");storeTokens(token,true);return access;}
    }
    private synchronized void storeTokens(JSONObject token,boolean refreshing)throws Exception {
        String nextAccess=token.optString("access_token"),nextRefresh=token.optString("refresh_token");
        if(nextAccess.isEmpty()||(!refreshing&&nextRefresh.isEmpty()))throw new IOException("Spotify 연결을 완료하지 못했습니다.");
        String savedRefresh=nextRefresh.isEmpty()?refresh:nextRefresh;long seconds=Math.max(60,Math.min(86400,token.optLong("expires_in",3600)));
        JSONObject saved=object("access",nextAccess,"refresh",savedRefresh,"expires",System.currentTimeMillis()+seconds*1000);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        String iv=Base64.getEncoder().encodeToString(cipher.getIV()),encrypted=Base64.getEncoder().encodeToString(cipher.doFinal(saved.toString().getBytes(StandardCharsets.UTF_8)));
        if(!context.getSharedPreferences("spotify_auth",0).edit().putString("iv",iv).putString("token",encrypted).commit())throw new IOException("Spotify 연결 정보를 저장하지 못했습니다.");
        access=nextAccess;refresh=savedRefresh;expires=saved.optLong("expires");
    }
    private SecretKey key()throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);SecretKey key=(SecretKey)store.getKey(KEY_ALIAS,null);if(key!=null)return key;
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    private synchronized void loadTokens(){
        try{
            android.content.SharedPreferences prefs=context.getSharedPreferences("spotify_auth",0);String iv=prefs.getString("iv",""),token=prefs.getString("token","");if(iv.isEmpty()||token.isEmpty())return;
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.getDecoder().decode(iv)));
            JSONObject saved=new JSONObject(new String(cipher.doFinal(Base64.getDecoder().decode(token)),StandardCharsets.UTF_8));access=saved.optString("access");refresh=saved.optString("refresh");expires=saved.optLong("expires");
        }catch(Exception ignored){access="";refresh="";expires=0;}
    }
    private JSONObject api(String path)throws Exception {
        String token=accessToken();
        try{return request("https://api.spotify.com/v1"+path,"GET",token,null);}
        catch(StatusException error){
            if(error.status!=401)throw error;
            synchronized(this){expires=0;}return request("https://api.spotify.com/v1"+path,"GET",accessToken(),null);
        }
    }
    void playlists(Callback callback){int ticket=epoch;if(connecting){callback.done(object("view","connecting","connected",false));return;}if(!connected()){callback.done(object("view","disconnected","connected",false));return;}worker.execute(()->loadPlaylists(ticket,callback));}
    private void loadPlaylists(int ticket,Callback callback){
        try{
            JSONArray result=new JSONArray();
            for(int offset=0;offset<500;offset+=50){
                JSONObject page=api("/me/playlists?limit=50&offset="+offset);JSONArray items=page.optJSONArray("items");if(items==null)break;
                for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;String id=item.optString("id");if(!validId(id))continue;
                    JSONObject owner=item.optJSONObject("owner"),tracks=item.optJSONObject("items");if(tracks==null)tracks=item.optJSONObject("tracks");
                    result.put(object("id",id,"name",item.optString("name","재생목록"),"owner",owner==null?"":owner.optString("display_name"),"count",tracks==null?-1:tracks.optInt("total",-1)));
                }
                if(items.length()==0||page.isNull("next")||!page.has("next"))break;
            }
            deliver(ticket,callback,object("view","lists","connected",true,"items",result));
        }catch(Exception error){deliver(ticket,callback,failure(safeMessage(error)));}
    }
    void tracks(String id,String name,Callback callback){
        int ticket=epoch;if(!validId(id)){callback.done(failure("잘못된 Spotify 재생목록입니다."));return;}
        worker.execute(()->{
            try{
                JSONArray result=new JSONArray();
                for(int offset=0;offset<5000;offset+=50){
                    JSONObject page=api("/playlists/"+id+"/items?limit=50&offset="+offset+"&additional_types=track%2Cepisode");JSONArray items=page.optJSONArray("items");if(items==null)break;
                    for(int i=0;i<items.length();i++){
                        JSONObject row=items.optJSONObject(i);if(row==null)continue;JSONObject track=row.optJSONObject("item");if(track==null)track=row.optJSONObject("track");if(track==null)continue;
                        String type=track.optString("type","track"),trackId=track.optString("id"),artist="";JSONArray artists=track.optJSONArray("artists");
                        if(artists!=null)for(int j=0;j<artists.length();j++){JSONObject a=artists.optJSONObject(j);if(a!=null&&!a.optString("name").isEmpty())artist+=(artist.isEmpty()?"":", ")+a.optString("name");}
                        result.put(object("id",validId(trackId)?trackId:"","type",type,"title",track.optString("name","제목 없음"),"artist",artist,"duration",track.optLong("duration_ms",0)));
                    }
                    if(items.length()==0||page.isNull("next")||!page.has("next"))break;
                }
                deliver(ticket,callback,object("view","tracks","connected",true,"id",id,"name",name,"items",result));
            }catch(Exception error){deliver(ticket,callback,object("view","tracksError","connected",connected(),"id",id,"name",name,"message",safeMessage(error)));}
        });
    }
    static boolean validId(String id){return id!=null&&id.matches("[A-Za-z0-9]{22}");}
    private String safeMessage(Exception error){
        if(error instanceof StatusException){int code=((StatusException)error).status;
            if(code==401)return "Spotify 연결이 만료되었습니다. 다시 연결해 주세요.";
            if(code==403)return "접근 권한이 없습니다. Spotify 계정과 재생목록 권한을 확인해 주세요.";
            if(code==429)return "Spotify 요청이 많습니다. 잠시 후 다시 시도해 주세요.";
            return "Spotify에서 정보를 불러오지 못했습니다.";
        }
        String message=error.getMessage();return message!=null&&message.startsWith("Spotify")?message:"Spotify에 연결하지 못했습니다.";
    }
}
