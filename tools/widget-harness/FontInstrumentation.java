package io.betterlyrics.widgettest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Verifies bundled fonts and preference persistence in the actual Android WebView. */
public final class FontInstrumentation extends Instrumentation {
    private final CountDownLatch done=new CountDownLatch(1);
    private volatile String result="";
    public final class Probe { @JavascriptInterface public void done(String value){result=value;done.countDown();} }
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle output=new Bundle();int code=Activity.RESULT_CANCELED;
        try{
            Intent launch=new Intent(Intent.ACTION_MAIN).setClassName("io.betterlyrics.mobile","io.betterlyrics.mobile.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity=startActivitySync(launch);waitForIdleSync();
            Field field=activity.getClass().getDeclaredField("web");field.setAccessible(true);WebView web=(WebView)field.get(activity);
            if(web==null)throw new IllegalStateException("WebView unavailable");
            String script="(function wait(n){if(!window.Appearance||!document.querySelector('.font-choice[data-font=jua]')){if(n>300){FontProbe.done('UI timeout');return;}setTimeout(function(){wait(n+1)},100);return;}"+
                "Promise.all(['BL Nanum Gothic','BL Nanum Myeongjo','BL Jua','BL Nanum Pen'].map(function(f){return document.fonts.load('400 24px \\\"'+f+'\\\"','가나다라마바사')})).then(function(all){"+
                "if(!all.every(function(a){return a.length&&a.every(function(f){return f.status==='loaded'})})){FontProbe.done('font unavailable');return;}"+
                "document.querySelector('.font-choice[data-font=jua]').click();FontProbe.done('ok');}).catch(function(e){FontProbe.done(String(e));});})(0)";
            runOnMainSync(()->{web.addJavascriptInterface(new Probe(),"FontProbe");web.evaluateJavascript(script,null);});
            if(!done.await(60,TimeUnit.SECONDS)||!"ok".equals(result))throw new IllegalStateException("Font load: "+result);
            boolean saved=false;
            for(int i=0;i<30;i++){
                String raw=getTargetContext().getSharedPreferences("settings",0).getString("appearance","{}");
                if("jua".equals(new JSONObject(raw).optString("font"))){saved=true;break;}
                Thread.sleep(100);
            }
            if(!saved)throw new IllegalStateException("Font preference did not persist");
            output.putString("fonts","ok");code=Activity.RESULT_OK;
        }catch(Exception error){output.putString("error",error.toString());}
        finish(code,output);
    }
}
