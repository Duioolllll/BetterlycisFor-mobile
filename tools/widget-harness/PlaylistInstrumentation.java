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
import java.util.concurrent.atomic.AtomicReference;

/** Actual Android document-grant, player, media-session and encrypted-token checks. */
public final class PlaylistInstrumentation extends Instrumentation {
    private interface Probe {boolean ready()throws Exception;}
    private void waitFor(Probe probe,String name)throws Exception{for(int i=0;i<100;i++){if(probe.ready())return;Thread.sleep(200);}throw new IllegalStateException("Timed out: "+name);}
    private Object call(Object target,String method,Class<?>[] types,Object... args)throws Exception{Method m=target.getClass().getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(target,args);}
    private Object mainCall(Object target,String method,Class<?>[] types,Object... args)throws Exception{
        AtomicReference<Object> result=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        runOnMainSync(()->{try{result.set(call(target,method,types,args));}catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new IllegalStateException(method,failure.get());return result.get();
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle output=new Bundle();int result=Activity.RESULT_CANCELED;
        try{
            Log.i("PlaylistInstrumentation","Starting activity");
            Context context=getTargetContext();Intent launch=new Intent(Intent.ACTION_MAIN).setClassName("io.betterlyrics.mobile","io.betterlyrics.mobile.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity=startActivitySync(launch);waitForIdleSync();
            Log.i("PlaylistInstrumentation","Requesting fixture document grants");
            context.startActivity(new Intent().setClassName("io.betterlyrics.widgettest","io.betterlyrics.widgettest.GrantActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Uri tree=DocumentsContract.buildTreeDocumentUri("io.betterlyrics.widgettest.documents","root"),file=DocumentsContract.buildDocumentUriUsingTree(tree,"playlist");
            waitFor(()->{try{context.getContentResolver().takePersistableUriPermission(file,Intent.FLAG_GRANT_READ_URI_PERMISSION);context.getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);return true;}catch(SecurityException error){return false;}},"document grants");
            Log.i("PlaylistInstrumentation","Importing M3U");
            Field pending=activity.getClass().getDeclaredField("pendingPlaylist");pending.setAccessible(true);
            runOnMainSync(()->{try{pending.set(activity,file);}catch(Exception error){throw new RuntimeException(error);}});
            Intent picked=new Intent().setData(tree).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            mainCall(activity,"onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},303,Activity.RESULT_OK,picked);
            Class<?> libraryClass=Class.forName("io.betterlyrics.mobile.LocalLibrary");Method get=libraryClass.getDeclaredMethod("get",Context.class);get.setAccessible(true);Object library=get.invoke(null,context);
            try{waitFor(()->((JSONArray)call(library,"list",new Class<?>[]{})).length()==1,"M3U import");}
            catch(IllegalStateException timeout){
                try{call(library,"importM3u",new Class<?>[]{Uri.class,Uri.class},file,tree);}
                catch(InvocationTargetException failure){throw new IllegalStateException("Direct M3U import failed",failure.getCause());}
                throw new IllegalStateException("Activity M3U import timed out although direct import succeeded",timeout);
            }
            JSONObject summary=((JSONArray)call(library,"list",new Class<?>[]{})).getJSONObject(0);String id=summary.getString("id");JSONObject detail=(JSONObject)call(library,"detail",new Class<?>[]{String.class},id);
            if(detail.getJSONArray("tracks").length()!=2||!detail.getJSONArray("tracks").getJSONObject(0).getString("title").equals("Sample One"))throw new IllegalStateException("M3U path resolution or order failed");
            Constructor<?> fresh=libraryClass.getDeclaredConstructor(Context.class);fresh.setAccessible(true);Object restored=fresh.newInstance(context);
            if(((JSONArray)call(restored,"list",new Class<?>[]{})).length()!=1)throw new IllegalStateException("playlist did not persist");
            Log.i("PlaylistInstrumentation","Starting local audio");
            mainCall(activity,"dispatch",new Class<?>[]{String.class,JSONObject.class},"playLocal",new JSONObject().put("id",id).put("index",0));
            Class<?> stateClass=Class.forName("io.betterlyrics.mobile.AppState");Object app=stateClass.getMethod("get",Context.class).invoke(null,context);
            Method track=stateClass.getMethod("track");
            waitFor(()->{JSONObject t=(JSONObject)track.invoke(app);return t.optBoolean("playing")&&t.optString("title").equals("Sample One");},"local audio starts");
            Log.i("PlaylistInstrumentation","Verifying playback controls and lyrics");
            Method snapshot=stateClass.getMethod("snapshot");waitFor(()->{JSONObject lyrics=((JSONObject)snapshot.invoke(app)).optJSONObject("lyrics");return lyrics!=null&&lyrics.optJSONArray("lines")!=null&&lyrics.optJSONArray("lines").length()>0&&lyrics.optJSONArray("lines").optJSONObject(0).optString("text").equals("Fixture local lyric");},"sidecar lyric");
            JSONObject started=(JSONObject)track.invoke(app);long first=started.optLong("position");
            waitFor(()->((JSONObject)track.invoke(app)).optLong("position")>first+300,"audio position advances");
            Field controllerField=stateClass.getDeclaredField("controller");controllerField.setAccessible(true);MediaController controller=(MediaController)controllerField.get(app);
            if(controller==null)throw new IllegalStateException("media controller unavailable");
            controller.getTransportControls().pause();waitFor(()->{PlaybackState p=controller.getPlaybackState();return p!=null&&p.getState()==PlaybackState.STATE_PAUSED;},"pause");
            controller.getTransportControls().seekTo(4000);waitFor(()->{PlaybackState p=controller.getPlaybackState();return p!=null&&p.getPosition()>=3900;},"seek");
            controller.getTransportControls().skipToNext();waitFor(()->{JSONObject t=(JSONObject)track.invoke(app);return t.optBoolean("playing")&&t.optString("title").equals("two");},"next song");
            if(context.getSystemService(android.app.NotificationManager.class).getActiveNotifications().length==0)throw new IllegalStateException("playback notification missing");
            mainCall(activity,"dispatch",new Class<?>[]{String.class,JSONObject.class},"removeLocalTrack",new JSONObject().put("id",id).put("index",0));
            detail=(JSONObject)call(library,"detail",new Class<?>[]{String.class},id);
            if(detail.getJSONArray("tracks").length()!=1||!((JSONObject)track.invoke(app)).optString("title").equals("two"))throw new IllegalStateException("removing an earlier track interrupted playback");
            Uri firstTrack=DocumentsContract.buildDocumentUriUsingTree(tree,"one");
            call(library,"importFiles",new Class<?>[]{java.util.List.class,String.class},java.util.Collections.singletonList(firstTrack),id);
            detail=(JSONObject)call(library,"detail",new Class<?>[]{String.class},id);if(detail.getJSONArray("tracks").length()!=2)throw new IllegalStateException("append failed");
            output.putString("local","ok");
            Log.i("PlaylistInstrumentation","Verifying encrypted Spotify credentials");

            Class<?> clientClass=Class.forName("io.betterlyrics.mobile.SpotifyClient");Constructor<?> ctor=clientClass.getDeclaredConstructor(Context.class);ctor.setAccessible(true);Object client=ctor.newInstance(context);
            JSONObject tokens=new JSONObject().put("access_token","synthetic-access-only").put("refresh_token","synthetic-refresh-only").put("expires_in",3600);
            call(client,"storeTokens",new Class<?>[]{JSONObject.class,boolean.class},tokens,false);
            String stored=context.getSharedPreferences("spotify_auth",0).getString("token","");
            if(stored.isEmpty()||stored.contains("synthetic-access-only")||stored.contains("synthetic-refresh-only"))throw new IllegalStateException("tokens were not encrypted");
            Object reloaded=ctor.newInstance(context);if(!((Boolean)call(reloaded,"connected",new Class<?>[]{})))throw new IllegalStateException("encrypted token did not reload");
            call(reloaded,"disconnect",new Class<?>[]{});if(!context.getSharedPreferences("spotify_auth",0).getString("token","").isEmpty())throw new IllegalStateException("disconnect did not clear tokens");
            call(client,"storeTokens",new Class<?>[]{JSONObject.class,boolean.class},tokens,false);output.putString("spotifyStorage","ok");

            Log.i("PlaylistInstrumentation","Verifying OAuth callback");

            Object callbackClient=ctor.newInstance(context);ServerSocket server=new ServerSocket();server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));server.setSoTimeout(1500);
            AtomicReference<Object> callback=new AtomicReference<>();AtomicReference<Throwable> callbackError=new AtomicReference<>();
            Thread listener=new Thread(()->{try{callback.set(call(callbackClient,"awaitCallback",new Class<?>[]{ServerSocket.class,String.class,int.class},server,"expected",0));}catch(Throwable error){callbackError.set(error);}});listener.start();
            request(server.getLocalPort(),"/callback?code=bad&state=wrong");request(server.getLocalPort(),"/callback?code=good&state=expected");listener.join(10000);server.close();
            if(listener.isAlive()||callbackError.get()!=null||callback.get()==null)throw new IllegalStateException("loopback callback failed",callbackError.get());
            Field code=callback.get().getClass().getDeclaredField("code");code.setAccessible(true);if(!"good".equals(code.get(callback.get())))throw new IllegalStateException("OAuth state was not enforced");
            output.putString("spotifyLoopback","ok");
            mainCall(activity,"dispatch",new Class<?>[]{String.class,JSONObject.class},"stopLocal",new JSONObject().put("id",id));
            Class<?> player=Class.forName("io.betterlyrics.mobile.LocalPlaybackService");Method current=player.getDeclaredMethod("currentPlaylist");current.setAccessible(true);
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
