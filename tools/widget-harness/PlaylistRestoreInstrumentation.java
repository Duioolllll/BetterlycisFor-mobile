package io.betterlyrics.widgettest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import org.json.JSONArray;
import java.io.InputStream;
import java.lang.reflect.*;

/** Runs in a new target process after the import and checks actual persisted grants. */
public final class PlaylistRestoreInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private static Object call(Object target,String method,Class<?>[] types,Object... args)throws Exception{Method m=target.getClass().getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(target,args);}
    @Override public void onStart(){
        Bundle out=new Bundle();int code=Activity.RESULT_CANCELED;
        try{
            Context context=getTargetContext();Class<?> type=Class.forName("io.betterlyrics.mobile.LocalLibrary");Method get=type.getDeclaredMethod("get",Context.class);get.setAccessible(true);Object library=get.invoke(null,context);
            JSONArray lists=(JSONArray)call(library,"list",new Class<?>[]{});if(lists.length()!=1||lists.getJSONObject(0).optInt("count")!=2)throw new IllegalStateException("saved list missing after process restart");
            String id=lists.getJSONObject(0).getString("id");Object playlist=call(library,"get",new Class<?>[]{String.class},id);Field tracks=playlist.getClass().getDeclaredField("tracks");tracks.setAccessible(true);
            Object first=((java.util.List<?>)tracks.get(playlist)).get(0);Field uriField=first.getClass().getDeclaredField("uri");uriField.setAccessible(true);Uri uri=Uri.parse((String)uriField.get(first));
            Uri tree=DocumentsContract.buildTreeDocumentUri("io.betterlyrics.widgettest.documents","root");boolean granted=false;
            for(UriPermission permission:context.getContentResolver().getPersistedUriPermissions())if(permission.getUri().equals(tree)&&permission.isReadPermission())granted=true;
            if(!granted)throw new IllegalStateException("tree permission did not persist");
            try(InputStream in=context.getContentResolver().openInputStream(uri)){byte[] header=new byte[4];if(in==null||in.read(header)!=4||!new String(header,"US-ASCII").equals("RIFF"))throw new IllegalStateException("saved music cannot be reopened");}
            Class<?> spotify=Class.forName("io.betterlyrics.mobile.SpotifyClient");Constructor<?> ctor=spotify.getDeclaredConstructor(Context.class);ctor.setAccessible(true);Object client=ctor.newInstance(context);
            if(!((Boolean)call(client,"connected",new Class<?>[]{})))throw new IllegalStateException("encrypted Spotify session did not survive restart");
            call(client,"disconnect",new Class<?>[]{});out.putString("restore","ok");code=Activity.RESULT_OK;
        }catch(Throwable error){Log.e("PlaylistInstrumentation","Restore test failed",error);out.putString("error",error.toString());}
        finish(code,out);
    }
}
