package io.betterlyrics.widgettest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Actual Android document-grant, player, media-session and encrypted-token checks. */
public final class PlaylistInstrumentation extends Instrumentation {
    private interface Probe {boolean ready()throws Exception;}
    private static final class CaptureActivity extends Activity {
        volatile Intent launched;
        @Override public void startActivity(Intent intent){launched=intent;}
    }
    private CaptureActivity captureActivity(){AtomicReference<CaptureActivity> result=new AtomicReference<>();runOnMainSync(()->result.set(new CaptureActivity()));return result.get();}
    private void waitFor(Probe probe,String name)throws Exception{for(int i=0;i<100;i++){if(probe.ready())return;Thread.sleep(200);}throw new IllegalStateException("Timed out: "+name);}
    private Object call(Object target,String method,Class<?>[] types,Object... args)throws Exception{Method m=target.getClass().getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(target,args);}
    private Object mainCall(Object target,String method,Class<?>[] types,Object... args)throws Exception{
        AtomicReference<Object> result=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        runOnMainSync(()->{try{result.set(call(target,method,types,args));}catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new IllegalStateException(method,failure.get());return result.get();
    }
    private String webValue(Activity activity,String script)throws Exception{
        Field field=activity.getClass().getDeclaredField("web");field.setAccessible(true);android.webkit.WebView web=(android.webkit.WebView)field.get(activity);
        if(web==null)return "";CountDownLatch done=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>("");
        runOnMainSync(()->web.evaluateJavascript(script,result->{value.set(result);done.countDown();}));
        if(!done.await(5,TimeUnit.SECONDS))throw new IllegalStateException("WebView did not answer");return value.get();
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle output=new Bundle();int result=Activity.RESULT_CANCELED;
        try{
            Log.i("PlaylistInstrumentation","Starting activity");
            Context context=getTargetContext();context.getSharedPreferences("settings",0).edit().putString("backgroundMode","static").apply();
            Intent launch=new Intent(Intent.ACTION_MAIN).setClassName("io.betterlyrics.mobile","io.betterlyrics.mobile.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity=startActivitySync(launch);waitForIdleSync();
            waitFor(()->"true".equals(webValue(activity,"!!(window.App&&document.getElementById('app').classList.contains('controls-hidden')&&document.getElementById('playerControls').inert)")),"idle playback controls hide in WebView");
            long now=android.os.SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,10,200,0),up=android.view.MotionEvent.obtain(now,now+40,android.view.MotionEvent.ACTION_UP,10,200,0);
            try{sendPointerSync(down);sendPointerSync(up);}finally{down.recycle();up.recycle();}
            waitFor(()->"true".equals(webValue(activity,"!!(window.App&&!document.getElementById('app').classList.contains('controls-hidden')&&!document.getElementById('playerControls').inert)")),"touch reveals playback controls");output.putString("controls","ok");
            Log.i("PlaylistInstrumentation","Requesting fixture document grants");
            context.startActivity(new Intent().setClassName("io.betterlyrics.widgettest","io.betterlyrics.widgettest.GrantActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Uri tree=DocumentsContract.buildTreeDocumentUri("io.betterlyrics.widgettest.documents","root"),file=DocumentsContract.buildDocumentUriUsingTree(tree,"playlist");
            waitFor(()->{try{context.getContentResolver().takePersistableUriPermission(file,Intent.FLAG_GRANT_READ_URI_PERMISSION);context.getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);return true;}catch(SecurityException error){return false;}},"document grants");
            AtomicBoolean pickerReturned=new AtomicBoolean();Thread watchdog=new Thread(()->{
                try{Thread.sleep(3500);if(!pickerReturned.get())for(java.util.Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet())if(entry.getKey().getName().equals("main")){
                    Log.i("PlaylistInstrumentation","Main thread while waiting for picker result:");for(StackTraceElement frame:entry.getValue())Log.i("PlaylistInstrumentation","  "+frame);
                }}catch(InterruptedException ignored){}
            },"picker-watchdog");watchdog.setDaemon(true);watchdog.start();
            // A real picker returns after the source activity has transitioned back.
            // Wait for the synthetic grant activity to finish that transition too.
            waitForIdleSync();waitFor(()->{AtomicReference<Boolean> focused=new AtomicReference<>(false);runOnMainSync(()->focused.set(!activity.isDestroyed()&&activity.hasWindowFocus()));return focused.get();},"focus after document grant");
            Log.i("PlaylistInstrumentation","Importing M3U");
            Field pending=activity.getClass().getDeclaredField("pendingPlaylist");pending.setAccessible(true);
            runOnMainSync(()->{try{pending.set(activity,file);}catch(Exception error){throw new RuntimeException(error);}});
            Intent picked=new Intent().setData(tree).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            mainCall(activity,"onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},303,Activity.RESULT_OK,picked);
            pickerReturned.set(true);
            ClassLoader targetLoader=activity.getClass().getClassLoader();Class<?> libraryClass=targetLoader.loadClass("io.betterlyrics.mobile.LocalLibrary");Method get=libraryClass.getDeclaredMethod("get",Context.class);get.setAccessible(true);Object library=get.invoke(null,context);
            try{waitFor(()->((JSONArray)call(library,"list",new Class<?>[]{})).length()==1,"M3U import");}
            catch(IllegalStateException timeout){
                Log.i("PlaylistInstrumentation","Retrying import directly for diagnosis; activity destroyed="+activity.isDestroyed());
                try{call(library,"importM3u",new Class<?>[]{Uri.class,Uri.class},file,tree);}
                catch(InvocationTargetException failure){throw new IllegalStateException("Direct M3U import failed",failure.getCause());}
                throw new IllegalStateException("Activity M3U import timed out although direct import succeeded",timeout);
            }
            JSONObject summary=((JSONArray)call(library,"list",new Class<?>[]{})).getJSONObject(0);String id=summary.getString("id");JSONObject detail=(JSONObject)call(library,"detail",new Class<?>[]{String.class},id);
            if(detail.getJSONArray("tracks").length()!=2||!detail.getJSONArray("tracks").getJSONObject(0).getString("title").equals("Sample One"))throw new IllegalStateException("M3U path resolution or order failed");
            Object folderResult=call(library,"importFolder",new Class<?>[]{Uri.class},tree);Field folderPlaylist=folderResult.getClass().getDeclaredField("playlist");folderPlaylist.setAccessible(true);Object folder=folderPlaylist.get(folderResult);Field folderId=folder.getClass().getDeclaredField("id");folderId.setAccessible(true);String addedFolder=(String)folderId.get(folder);
            JSONObject groups=(JSONObject)call(library,"grouped",new Class<?>[]{});JSONArray artists=groups.getJSONArray("artists"),albums=groups.getJSONArray("albums");
            if(artists.length()!=1||albums.length()!=1||artists.getJSONObject(0).getInt("count")!=2)throw new IllegalStateException("Library groups did not deduplicate imported files");
            JSONObject grouped=(JSONObject)call(library,"detail",new Class<?>[]{String.class},artists.getJSONObject(0).getString("id"));if(!grouped.optBoolean("readOnly")||grouped.getJSONArray("tracks").length()!=2)throw new IllegalStateException("Artist group did not open");
            if(!((Boolean)call(library,"remove",new Class<?>[]{String.class},addedFolder)))throw new IllegalStateException("Folder test cleanup failed");
            Object export=call(library,"exportM3u",new Class<?>[]{String.class,Uri.class},id,tree);Field exportName=export.getClass().getDeclaredField("name");exportName.setAccessible(true);String wanted=(String)exportName.get(export),exportId="";
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,"root");
            try(android.database.Cursor cursor=context.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){while(cursor!=null&&cursor.moveToNext())if(wanted.equals(cursor.getString(1))){exportId=cursor.getString(0);break;}}
            if(exportId.isEmpty())throw new IllegalStateException("Exported playlist was not created");
            try(java.io.InputStream input=context.getContentResolver().openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree,exportId))){java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[1024];for(int n;(n=input.read(buffer))>=0;)bytes.write(buffer,0,n);String text=new String(bytes.toByteArray(),StandardCharsets.UTF_8);if(!text.startsWith("#EXTM3U\n")||text.indexOf("./Album/one.wav")<0||text.indexOf("./Album/two.wav")<=text.indexOf("./Album/one.wav"))throw new IllegalStateException("M3U export did not preserve paths and order");}
            output.putString("library","ok");
            mainCall(activity,"dispatch",new Class<?>[]{String.class,JSONObject.class},"renamePlaylist",new JSONObject().put("id",id).put("name","Saved playlist"));
            detail=(JSONObject)call(library,"detail",new Class<?>[]{String.class},id);if(!detail.optString("name").equals("Saved playlist"))throw new IllegalStateException("playlist rename failed");
            Constructor<?> fresh=libraryClass.getDeclaredConstructor(Context.class);fresh.setAccessible(true);Object restored=fresh.newInstance(context);
            if(((JSONArray)call(restored,"list",new Class<?>[]{})).length()!=1)throw new IllegalStateException("playlist did not persist");
            Log.i("PlaylistInstrumentation","Starting local audio");
            mainCall(activity,"dispatch",new Class<?>[]{String.class,JSONObject.class},"playLocal",new JSONObject().put("id",id).put("index",0));
            Class<?> stateClass=targetLoader.loadClass("io.betterlyrics.mobile.AppState");Object app=stateClass.getMethod("get",Context.class).invoke(null,context);
            Method track=stateClass.getMethod("track");
            waitFor(()->{JSONObject t=(JSONObject)track.invoke(app);return t.optBoolean("playing")&&t.optString("title").equals("Sample One");},"local audio starts");
            mainCall(activity,"dispatch",new Class<?>[]{String.class,JSONObject.class},"moveLocalTrack",new JSONObject().put("id",id).put("from",0).put("to",1));
            detail=(JSONObject)call(library,"detail",new Class<?>[]{String.class},id);if(!"two".equals(detail.getJSONArray("tracks").getJSONObject(0).getString("title"))||!"Sample One".equals(((JSONObject)track.invoke(app)).optString("title")))throw new IllegalStateException("Reordering interrupted the playing song");
            mainCall(activity,"dispatch",new Class<?>[]{String.class,JSONObject.class},"moveLocalTrack",new JSONObject().put("id",id).put("from",1).put("to",0));
            Log.i("PlaylistInstrumentation","Verifying playback controls and lyrics");
            Method snapshot=stateClass.getMethod("snapshot");waitFor(()->{JSONObject lyrics=((JSONObject)snapshot.invoke(app)).optJSONObject("lyrics");return lyrics!=null&&lyrics.optJSONArray("lines")!=null&&lyrics.optJSONArray("lines").length()>0&&lyrics.optJSONArray("lines").optJSONObject(0).optString("text").equals("Fixture local lyric");},"sidecar lyric");
            Class<?> cardClass=targetLoader.loadClass("io.betterlyrics.mobile.LyricCard"),cardData=targetLoader.loadClass("io.betterlyrics.mobile.LyricCard$Data");
            Method capture=cardClass.getDeclaredMethod("capture",stateClass);capture.setAccessible(true);Object card=capture.invoke(null,app);if(card==null)throw new IllegalStateException("Current lyric was not captured");
            Uri cardFile=DocumentsContract.createDocument(context.getContentResolver(),DocumentsContract.buildDocumentUriUsingTree(tree,"root"),"image/png","fixture-card.png");
            Method writeCard=cardClass.getDeclaredMethod("write",Context.class,Uri.class,cardData);writeCard.setAccessible(true);writeCard.invoke(null,context,cardFile,card);
            try(java.io.InputStream input=context.getContentResolver().openInputStream(cardFile)){android.graphics.Bitmap image=android.graphics.BitmapFactory.decodeStream(input);if(image==null||image.getWidth()!=1080||image.getHeight()!=1080||image.getPixel(0,0)==image.getPixel(540,540))throw new IllegalStateException("Lyric card PNG is invalid");image.recycle();}output.putString("card","ok");
            Log.i("PlaylistInstrumentation","Local lyric loaded");
            JSONObject started=(JSONObject)track.invoke(app);long first=started.optLong("position");
            waitFor(()->((JSONObject)track.invoke(app)).optLong("position")>first+300,"audio position advances");
            Field listeningField=stateClass.getDeclaredField("listening");listeningField.setAccessible(true);Object listening=listeningField.get(app);
            JSONObject observed=(JSONObject)mainCall(listening,"snapshot",new Class<?>[]{});if(observed.optLong("total")<=0||observed.optJSONArray("items").length()==0)throw new IllegalStateException("Playing time was not recorded");
            JSONObject cleared=(JSONObject)mainCall(listening,"clear",new Class<?>[]{});if(cleared.optLong("total")!=0)throw new IllegalStateException("Listening history was not cleared");output.putString("listening","ok");
            Log.i("PlaylistInstrumentation","Playback position advances");
            Field controllerField=stateClass.getDeclaredField("controller");controllerField.setAccessible(true);MediaController controller=(MediaController)controllerField.get(app);
            if(controller==null)throw new IllegalStateException("media controller unavailable");
            controller.getTransportControls().pause();waitFor(()->{PlaybackState p=controller.getPlaybackState();return p!=null&&p.getState()==PlaybackState.STATE_PAUSED;},"pause");
            Log.i("PlaylistInstrumentation","Pause succeeded");
            controller.getTransportControls().seekTo(4000);waitFor(()->{PlaybackState p=controller.getPlaybackState();return p!=null&&p.getPosition()>=3900;},"seek");
            Log.i("PlaylistInstrumentation","Seek succeeded");
            controller.getTransportControls().skipToNext();waitFor(()->{JSONObject t=(JSONObject)track.invoke(app);return t.optBoolean("playing")&&t.optString("title").equals("two");},"next song");
            Log.i("PlaylistInstrumentation","Next succeeded");
            if(context.getSystemService(android.app.NotificationManager.class).getActiveNotifications().length==0)throw new IllegalStateException("playback notification missing");
            mainCall(activity,"dispatch",new Class<?>[]{String.class,JSONObject.class},"removeLocalTrack",new JSONObject().put("id",id).put("index",0));
            detail=(JSONObject)call(library,"detail",new Class<?>[]{String.class},id);
            if(detail.getJSONArray("tracks").length()!=1||!((JSONObject)track.invoke(app)).optString("title").equals("two"))throw new IllegalStateException("removing an earlier track interrupted playback");
            Uri firstTrack=DocumentsContract.buildDocumentUriUsingTree(tree,"one");
            call(library,"importFiles",new Class<?>[]{java.util.List.class,String.class},java.util.Collections.singletonList(firstTrack),id);
            detail=(JSONObject)call(library,"detail",new Class<?>[]{String.class},id);if(detail.getJSONArray("tracks").length()!=2)throw new IllegalStateException("append failed");
            output.putString("local","ok");
            Class<?> clientClass=targetLoader.loadClass("io.betterlyrics.mobile.SpotifyClient");Constructor<?> ctor=clientClass.getDeclaredConstructor(Context.class);ctor.setAccessible(true);Object client=ctor.newInstance(context);
            Log.i("PlaylistInstrumentation","Verifying actual Spotify authorization request");
            Class<?> callbackType=targetLoader.loadClass("io.betterlyrics.mobile.SpotifyClient$Callback");
            AtomicReference<JSONObject> authResult=new AtomicReference<>();
            Object authCallback=java.lang.reflect.Proxy.newProxyInstance(targetLoader,new Class<?>[]{callbackType},(proxy,method,arguments)->{if(method.getName().equals("done"))authResult.set((JSONObject)arguments[0]);return null;});
            CaptureActivity browser=captureActivity();
            mainCall(client,"connect",new Class<?>[]{Activity.class,callbackType},browser,authCallback);
            if(browser.launched==null||browser.launched.getData()==null)throw new IllegalStateException("Spotify browser request missing");
            Uri authorization=browser.launched.getData();String redirect=authorization.getQueryParameter("redirect_uri"),state=authorization.getQueryParameter("state");
            if(!"accounts.spotify.com".equals(authorization.getHost())||!"http://127.0.0.1:43123/callback".equals(redirect)||state==null||state.isEmpty()||!"S256".equals(authorization.getQueryParameter("code_challenge_method"))||!authorization.getQueryParameter("scope").contains("user-modify-playback-state"))throw new IllegalStateException("Spotify authorization request is missing callback or playback scope");
            request(43123,"/callback?error=access_denied&state=wrong");
            if(authResult.get()!=null)throw new IllegalStateException("Wrong OAuth state completed authorization");
            request(43123,"/callback?error=access_denied&state="+Uri.encode(state));
            waitFor(()->authResult.get()!=null,"authorization cancellation callback");
            if(!"error".equals(authResult.get().optString("view")))throw new IllegalStateException("Authorization cancellation did not return to the app");
            ServerSocket occupied=null;
            for(int attempt=0;attempt<50&&occupied==null;attempt++){
                ServerSocket candidate=new ServerSocket();candidate.setReuseAddress(true);
                try{candidate.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),43123));occupied=candidate;}
                catch(BindException busy){candidate.close();Thread.sleep(100);}
            }
            if(occupied==null)throw new IllegalStateException("Callback port could not be reused after authorization");
            try{
                Object blocked=ctor.newInstance(context);CaptureActivity blockedBrowser=captureActivity();AtomicReference<JSONObject> blockedResult=new AtomicReference<>();
                Object blockedCallback=java.lang.reflect.Proxy.newProxyInstance(targetLoader,new Class<?>[]{callbackType},(proxy,method,arguments)->{if(method.getName().equals("done"))blockedResult.set((JSONObject)arguments[0]);return null;});
                mainCall(blocked,"connect",new Class<?>[]{Activity.class,callbackType},blockedBrowser,blockedCallback);
                if(blockedBrowser.launched!=null||blockedResult.get()==null||!"error".equals(blockedResult.get().optString("view")))throw new IllegalStateException("Occupied callback port was not reported");
            }finally{occupied.close();}
            output.putString("spotifyAuthorize","ok");

            Log.i("PlaylistInstrumentation","Verifying encrypted Spotify credentials");
            JSONObject tokens=new JSONObject().put("access_token","synthetic-access-only").put("refresh_token","synthetic-refresh-only").put("expires_in",3600);
            call(client,"storeTokens",new Class<?>[]{JSONObject.class,boolean.class},tokens,false);
            String stored=context.getSharedPreferences("spotify_auth",0).getString("token","");
            if(stored.isEmpty()||stored.contains("synthetic-access-only")||stored.contains("synthetic-refresh-only"))throw new IllegalStateException("tokens were not encrypted");
            Object reloaded=ctor.newInstance(context);if(!((Boolean)call(reloaded,"connected",new Class<?>[]{})))throw new IllegalStateException("encrypted token did not reload");
            call(reloaded,"disconnect",new Class<?>[]{});if(!context.getSharedPreferences("spotify_auth",0).getString("token","").isEmpty())throw new IllegalStateException("disconnect did not clear tokens");
            call(client,"storeTokens",new Class<?>[]{JSONObject.class,boolean.class},tokens,false);output.putString("spotifyStorage","ok");

            Log.i("PlaylistInstrumentation","Verifying Spotify Connect playback requests");
            Class<?> transportType=targetLoader.loadClass("io.betterlyrics.mobile.SpotifyClient$Transport"),statusType=targetLoader.loadClass("io.betterlyrics.mobile.SpotifyClient$StatusException");
            Constructor<?> statusCtor=statusType.getDeclaredConstructor(int.class);statusCtor.setAccessible(true);
            AtomicInteger mode=new AtomicInteger(),attempts=new AtomicInteger(),refreshes=new AtomicInteger();AtomicReference<JSONObject> sent=new AtomicReference<>();
            Object transport=java.lang.reflect.Proxy.newProxyInstance(targetLoader,new Class<?>[]{transportType},(proxy,method,arguments)->{
                String address=(String)arguments[0],verb=(String)arguments[1];
                if(address.equals("https://accounts.spotify.com/api/token")){refreshes.incrementAndGet();return new JSONObject().put("access_token","synthetic-refreshed").put("expires_in",3600);}
                if(!address.equals("https://api.spotify.com/v1/me/player/play")||!verb.equals("PUT")||!"application/json".equals(arguments[4]))throw new IllegalStateException("Unexpected playback request");
                sent.set(new JSONObject((String)arguments[3]));
                if(mode.get()==404)throw (Exception)statusCtor.newInstance(404);
                if(mode.get()==401&&attempts.getAndIncrement()==0)throw (Exception)statusCtor.newInstance(401);
                return new JSONObject();
            });
            Constructor<?> injected=clientClass.getDeclaredConstructor(Context.class,transportType);injected.setAccessible(true);Object playerClient=injected.newInstance(context,transport);
            AtomicReference<JSONObject> playResult=new AtomicReference<>();
            Object playCallback=java.lang.reflect.Proxy.newProxyInstance(targetLoader,new Class<?>[]{callbackType},(proxy,method,arguments)->{if(method.getName().equals("done"))playResult.set((JSONObject)arguments[0]);return null;});
            String playlistId="0123456789ABCDEFGHIJKL";
            mainCall(playerClient,"play",new Class<?>[]{String.class,int.class,callbackType},playlistId,-1,playCallback);
            if(playResult.get()==null||!"reauthorize".equals(playResult.get().optString("state"))||sent.get()!=null)throw new IllegalStateException("Legacy Spotify token did not request playback permission");
            JSONObject authorized=new JSONObject(tokens.toString()).put("scope","playlist-read-private user-modify-playback-state");
            call(playerClient,"storeTokens",new Class<?>[]{JSONObject.class,boolean.class},authorized,false);playResult.set(null);
            mainCall(playerClient,"play",new Class<?>[]{String.class,int.class,callbackType},playlistId,51,playCallback);
            waitFor(()->playResult.get()!=null,"Spotify track playback request");
            if(!"accepted".equals(playResult.get().optString("state"))||sent.get()==null||!sent.get().optString("context_uri").equals("spotify:playlist:"+playlistId)||sent.get().getJSONObject("offset").getInt("position")!=51)throw new IllegalStateException("Selected playlist position was not sent to Spotify");
            playResult.set(null);sent.set(null);mode.set(401);attempts.set(0);
            mainCall(playerClient,"play",new Class<?>[]{String.class,int.class,callbackType},playlistId,-1,playCallback);
            waitFor(()->playResult.get()!=null,"Spotify token refresh and playlist playback");
            if(!"accepted".equals(playResult.get().optString("state"))||sent.get()==null||sent.get().has("offset")||refreshes.get()!=1||!((Boolean)call(playerClient,"playbackAuthorized",new Class<?>[]{})))throw new IllegalStateException("Playback refresh or scope persistence failed");
            playResult.set(null);mode.set(404);
            mainCall(playerClient,"play",new Class<?>[]{String.class,int.class,callbackType},playlistId,-1,playCallback);
            waitFor(()->playResult.get()!=null,"Spotify unavailable-device error");
            if(!"error".equals(playResult.get().optString("state"))||!playResult.get().optString("message").contains("먼저"))throw new IllegalStateException("Missing Spotify device was not explained");
            call(client,"storeTokens",new Class<?>[]{JSONObject.class,boolean.class},authorized,false);output.putString("spotifyPlayback","ok");

            Log.i("PlaylistInstrumentation","Verifying OAuth callback");

            Object callbackClient=ctor.newInstance(context);ServerSocket server=new ServerSocket();server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));server.setSoTimeout(1500);
            AtomicReference<Object> callback=new AtomicReference<>();AtomicReference<Throwable> callbackError=new AtomicReference<>();
            Thread listener=new Thread(()->{try{callback.set(call(callbackClient,"awaitCallback",new Class<?>[]{ServerSocket.class,String.class,int.class},server,"expected",0));}catch(Throwable error){callbackError.set(error);}});listener.start();
            request(server.getLocalPort(),"/callback?code=bad&state=wrong");request(server.getLocalPort(),"/callback?code=good&state=expected");listener.join(10000);server.close();
            if(listener.isAlive()||callbackError.get()!=null||callback.get()==null)throw new IllegalStateException("loopback callback failed",callbackError.get());
            Field code=callback.get().getClass().getDeclaredField("code");code.setAccessible(true);if(!"good".equals(code.get(callback.get())))throw new IllegalStateException("OAuth state was not enforced");
            output.putString("spotifyLoopback","ok");
            mainCall(activity,"dispatch",new Class<?>[]{String.class,JSONObject.class},"stopLocal",new JSONObject().put("id",id));
            Class<?> player=targetLoader.loadClass("io.betterlyrics.mobile.LocalPlaybackService");Method current=player.getDeclaredMethod("currentPlaylist");current.setAccessible(true);
            waitFor(()->"".equals(current.invoke(null)),"local player stops");
            Log.i("PlaylistInstrumentation","Playlist checks completed");
            result=Activity.RESULT_OK;
        }catch(Throwable error){Log.e("PlaylistInstrumentation","Playlist test failed",error);output.putString("error",error.toString());}
        finish(result,output);
    }
    private static void request(int port,String path)throws Exception{
        try(Socket socket=new Socket("127.0.0.1",port)){socket.setSoTimeout(5000);socket.getOutputStream().write(("GET "+path+" HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();while(socket.getInputStream().read()!=-1){}}
    }
}
