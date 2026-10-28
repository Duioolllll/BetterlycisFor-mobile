package io.betterlyrics.widgettest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Verifies bundled fonts and preference persistence in the actual Android WebView. */
public final class FontInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private String evaluate(WebView web,String script)throws Exception{
        CountDownLatch ready=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();
        runOnMainSync(()->web.evaluateJavascript(script,response->{value.set(response);ready.countDown();}));
        if(!ready.await(30,TimeUnit.SECONDS))throw new IllegalStateException("WebView callback timed out");
        return value.get();
    }
    @Override public void onStart(){
        Bundle output=new Bundle();int code=Activity.RESULT_CANCELED;
        try{
            Intent launch=new Intent(Intent.ACTION_MAIN).setClassName("io.betterlyrics.mobile","io.betterlyrics.mobile.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity=startActivitySync(launch);waitForIdleSync();
            Field field=activity.getClass().getDeclaredField("web");field.setAccessible(true);WebView web=(WebView)field.get(activity);
            if(web==null)throw new IllegalStateException("WebView unavailable");
            Field loaded=activity.getClass().getDeclaredField("ready");loaded.setAccessible(true);boolean documentLoaded=false;
            for(int i=0;i<150;i++){
                AtomicReference<Boolean> observed=new AtomicReference<>(false);
                runOnMainSync(()->{try{observed.set(loaded.getBoolean(activity));}catch(IllegalAccessException ignored){}});
                if(observed.get()){documentLoaded=true;break;}Thread.sleep(200);
            }
            if(!documentLoaded)throw new IllegalStateException("WebView page did not finish loading");
            boolean pageReady=false;
            for(int i=0;i<100;i++){
                if("true".equals(evaluate(web,"!!(window.Appearance&&window.App&&document.querySelector('.font-choice[data-font=jua]'))"))){pageReady=true;break;}
                Thread.sleep(200);
            }
            if(!pageReady)throw new IllegalStateException("Web UI did not load");
            String script="window.__fontTest='loading';Promise.all(['BL Nanum Gothic','BL Nanum Myeongjo','BL Jua','BL Nanum Pen'].map(function(f){return document.fonts.load('400 24px \\\"'+f+'\\\"','가나다라마바사')})).then(function(all){"+
                "if(!all.every(function(a){return a.length&&a.every(function(f){return f.status==='loaded'})})){window.__fontTest='font unavailable';return;}"+
                "document.querySelector('.font-choice[data-font=jua]').click();"+
                "[['widgetTheme','light'],['widgetSize','large']].forEach(function(x){var e=document.getElementById(x[0]);e.value=x[1];e.dispatchEvent(new Event('change',{bubbles:true}));});"+
                "var cover=document.getElementById('widgetCover');cover.checked=false;cover.dispatchEvent(new Event('change',{bubbles:true}));window.__fontTest='ok';}).catch(function(e){window.__fontTest=String(e);});";
            evaluate(web,script);
            String result="";
            for(int i=0;i<300;i++){
                result=evaluate(web,"window.__fontTest||'loading'");
                if("\"ok\"".equals(result))break;
                if(!"\"loading\"".equals(result))throw new IllegalStateException("Font load: "+result);
                Thread.sleep(100);
            }
            if(!"\"ok\"".equals(result))throw new IllegalStateException("Font load timed out");
            boolean saved=false;
            for(int i=0;i<30;i++){
                String raw=getTargetContext().getSharedPreferences("settings",0).getString("appearance","{}");
                android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("settings",0);
                if("jua".equals(new JSONObject(raw).optString("font"))&&"light".equals(prefs.getString("widgetTheme",""))&&"large".equals(prefs.getString("widgetSize",""))&&!prefs.getBoolean("widgetCover",true)){saved=true;break;}
                Thread.sleep(100);
            }
            if(!saved)throw new IllegalStateException("Font/widget preferences did not persist");
            output.putString("fonts","ok");code=Activity.RESULT_OK;
        }catch(Exception error){output.putString("error",error.toString());}
        finish(code,output);
    }
}
