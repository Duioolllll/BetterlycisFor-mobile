package io.betterlyrics.mobile;

import android.app.*;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.media.AudioManager;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.media.session.*;
import android.media.session.MediaController;
import org.json.*;
import java.io.*;
import java.util.concurrent.Future;

public final class MainActivity extends Activity {
    private static final String TAG="MainActivity";
    private WebView web;
    private AppState state;
    private boolean ready,active;
    private boolean pendingCapture,projectionPending;
    private boolean publishQueued;
    private long lastTickAt;
    private Future<?> pendingFileRead;
    private volatile String bootstrap="{}";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable publishTask=()->{publishQueued=false;publish();};
    private final AppState.Listener observer=this::observe;
    private final Runnable ticker=new Runnable(){public void run(){if(active&&ready){lastTickAt=SystemClock.elapsedRealtime();send("receive",state.tick());handler.postDelayed(this,500);}}};
    private final Runnable audioTicker=new Runnable(){public void run(){if(!active||!ready)return;state.audio.playing(state.playing());send("audio",state.audio.packet());handler.postDelayed(this,50);}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);setVolumeControlStream(AudioManager.STREAM_MUSIC);state=AppState.get(this);bootstrap=state.snapshot().toString();
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(16,18,23));setContentView(root);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets p=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(p.left,p.top,p.right,p.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        try{web=new WebView(this);}catch(Exception e){TextView message=new TextView(this);message.setText("Android System WebView를 업데이트한 뒤 다시 실행해 주세요.");message.setTextColor(Color.WHITE);message.setPadding(32,80,32,32);root.addView(message);return;}
        web.setBackgroundColor(Color.TRANSPARENT);web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(true);settings.setSupportMultipleWindows(false);settings.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(),"Android");web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public void onPageFinished(WebView view,String url){if(!url.equals("file:///android_asset/web/index.html"))return;ready=true;publish();handler.removeCallbacks(ticker);handler.removeCallbacks(audioTicker);if(active){handler.post(ticker);handler.post(audioTicker);}}
        });
        root.addView(web,new FrameLayout.LayoutParams(-1,-1));web.loadUrl("file:///android_asset/web/index.html");
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::handleBack);
    }
    @Override protected void onResume(){super.onResume();active=true;if(state!=null){if(!state.listeners.contains(observer))state.listeners.add(observer);LyricsListener.refresh(this);reconcileAudio();publish();}if(web!=null)web.onResume();handler.removeCallbacks(ticker);handler.post(ticker);handler.removeCallbacks(audioTicker);handler.post(audioTicker);}
    @Override protected void onPause(){active=false;handler.removeCallbacks(ticker);handler.removeCallbacks(audioTicker);handler.removeCallbacks(publishTask);publishQueued=false;if(state!=null){state.listeners.remove(observer);state.audio.suspend();}if(web!=null)web.onPause();super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(state!=null){state.listeners.remove(observer);state.cancelWork(pendingFileRead);}if(web!=null){web.removeJavascriptInterface("Android");web.stopLoading();web.destroy();web=null;}super.onDestroy();}
    @Override public void onBackPressed(){handleBack();}
    private void handleBack(){if(web==null||!ready){finish();return;}web.evaluateJavascript("window.App&&App.handleBack?App.handleBack():false",result->{if(!"true".equals(result))finish();});}
    private void observe(boolean full){
        if(full){if(!publishQueued){publishQueued=true;handler.post(publishTask);}}
        else if(active&&ready){handler.removeCallbacks(ticker);handler.postDelayed(ticker,Math.max(0,50-(SystemClock.elapsedRealtime()-lastTickAt)));}
    }
    private void publish(){if(state==null)return;bootstrap=state.snapshot().toString();if(state.prefs.getBoolean("keepScreen",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(ready&&web!=null)web.evaluateJavascript("window.App&&App.receive("+bootstrap+")",null);}
    private void send(String method,JSONObject value){if(ready&&web!=null)web.evaluateJavascript("window.App&&App."+method+"("+value.toString()+")",null);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    public final class Bridge {
        @JavascriptInterface public String getBootstrap(){return bootstrap;}
        @JavascriptInterface public long elapsedRealtime(){return SystemClock.elapsedRealtime();}
        @JavascriptInterface public void command(String action,String json){
            if(action==null||action.length()>40||json==null||json.length()>8000)return;
            handler.post(()->{try{dispatch(action,new JSONObject(json));}catch(Exception e){bridgeFailure(action,e);}});
        }
    }
    private void bridgeFailure(String action,Exception error){
        Log.w(TAG,"Bridge command failed: "+action,error);
        toast(error instanceof JSONException?"요청 형식이 올바르지 않습니다.":"요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }
    private void dispatch(String action,JSONObject data)throws Exception{
        switch(action){
            case "notificationAccess":startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));break;
            case "overlayPermission":startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));break;
            case "toggleOverlay":if(!state.granted()){toast("음악 감지 권한을 먼저 켜 주세요.");startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}else if(!Settings.canDrawOverlays(this))startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));else state.set("overlay",!state.prefs.getBoolean("overlay",false));break;
            case "refresh":LyricsListener.refresh(this);state.fetch(true);break;
            case "clearCache":state.network.execute(()->{state.repository.clear();handler.post(()->{toast("저장된 가사를 지웠어요.");state.fetch(true);});});break;
            case "setAppearance":state.prefs.edit().putString("appearance",UiAppearance.sanitize(data.optJSONObject("appearance")).toString()).apply();publish();break;
            case "setSetting":state.set(data.optString("key"),data.get("value"));if(data.optString("key").equals("backgroundMode"))reconcileAudio();break;
            case "audioConnect":if(state.audio.packet().optBoolean("connected")){state.prefs.edit().putBoolean("audioEnabled",false).apply();state.audio.stop();publish();}else requestAudio(false);break;
            case "audioCapture":requestAudio(true);break;
            case "audioStop":state.prefs.edit().putBoolean("audioEnabled",false).apply();state.audio.stop();publish();break;
            case "selectSource":state.prefs.edit().putString("sourcePackage",data.optString("packageName","")).apply();LyricsListener.refresh(this);break;
            case "setVolume":{
                AudioManager manager=getSystemService(AudioManager.class);
                if(manager==null||manager.isVolumeFixed()){toast("기기에서 음량 조절을 제공하지 않아요.");break;}
                int max=manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int min=Build.VERSION.SDK_INT>=28?manager.getStreamMinVolume(AudioManager.STREAM_MUSIC):0;
                int value=(int)Math.round(max*Math.max(0,Math.min(1000,data.optInt("value")))/1000.0);
                manager.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(min,Math.min(max,value)),0);
                send("receive",new JSONObject().put("volume",state.volume()));break;
            }
            case "transport":transport(data);break;
            case "search":if(state.song.key.isEmpty()){toast("음악을 먼저 재생해 주세요.");break;}if(data.optString("title").trim().isEmpty())break;state.search(data.optString("title"),data.optString("artist"),response->send("searchResults",response));break;
            case "chooseResult":state.choose(data.optString("id"));break;
            case "importLyrics":if(state.song.key.isEmpty()){toast("음악을 먼저 재생해 주세요.");break;}Intent file=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");file.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(file,100);break;
            case "openPlayer":openPlayer();break;
            case "about":new AlertDialog.Builder(this).setTitle("BetterLyrics Mobile "+getPackageManager().getPackageInfo(getPackageName(),0).versionName).setMessage("BetterLyrics v1.3.527.0을 분석하여 Android에 맞게 구현한 비공식 이식판입니다.\n\n원작: Zhe Fang / BetterLyrics contributors\nGPL-3.0 · 보증 없음 · 같은 라이선스로 재배포 가능\n소스와 라이선스: github.com/Duioolllll/BetterlycisFor-mobile\n\n음악 감지는 미디어 세션만 사용합니다. 가사 검색 시 제목·가수·앨범 정보를 선택한 가사 제공처로 전송합니다.\n\nWindows DLL 플러그인, Wallpaper Engine, Windows 오디오 기능은 이 버전에 포함되지 않습니다. 재생 소리 분석은 별도 연결 시 기기에서만 수행합니다.\n아이콘: Bootstrap Icons (MIT).").setPositiveButton("확인",null).show();break;
        }
    }
    private void reconcileAudio(){
        if(projectionPending)return;
        if(!state.prefs.getBoolean("audioEnabled",false)||!state.prefs.getString("backgroundMode","sound").equals("sound")){state.audio.stop();return;}
        if(!state.prefs.getString("audioInput","visualizer").equals("capture")&&state.audio.permitted())state.audio.startVisualizer();
    }
    private void requestAudio(boolean capture){
        pendingCapture=capture;
        state.prefs.edit().putString("backgroundMode","sound").apply();
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},201);return;}
        launchAudio(capture);
    }
    private void launchAudio(boolean capture){
        state.prefs.edit().putBoolean("audioEnabled",true).putString("audioInput",capture?"capture":"visualizer").apply();state.audio.playing(state.playing());
        if(capture){
            if(Build.VERSION.SDK_INT<29){toast("다른 방식의 소리 연결은 Android 10 이상에서 지원해요.");return;}
            try{projectionPending=true;startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(),202);}
            catch(Exception error){projectionPending=false;toast("소리 연결 안내를 열 수 없어요.");}
        }else state.audio.startVisualizer();publish();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request!=201)return;if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)launchAudio(pendingCapture);else{state.prefs.edit().putBoolean("audioEnabled",false).apply();state.audio.stop();toast("기본 그라데이션으로 사용할게요. 소리 연결은 설정에서 다시 켤 수 있어요.");publish();}}
    private void transport(JSONObject request){
        MediaController c=state.controller;if(c==null){toast("재생 중인 음악 앱이 없습니다.");return;}
        PlaybackState p=c.getPlaybackState();if(p==null)return;long actions=p.getActions();MediaController.TransportControls controls=c.getTransportControls();
        switch(request.optString("action")){
            case "playPause":if(p.getState()==PlaybackState.STATE_PLAYING){if((actions&(PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE))!=0)controls.pause();}else if((actions&(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_PAUSE))!=0)controls.play();break;
            case "next":if((actions&PlaybackState.ACTION_SKIP_TO_NEXT)!=0)controls.skipToNext();else toast("이 앱은 다음 곡 제어를 제공하지 않습니다.");break;
            case "previous":if((actions&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0)controls.skipToPrevious();else toast("이 앱은 이전 곡 제어를 제공하지 않습니다.");break;
            case "seek":if((actions&PlaybackState.ACTION_SEEK_TO)!=0){long to=Math.max(0,request.optLong("position"));if(state.song.duration>0)to=Math.min(state.song.duration,to);controls.seekTo(to);}else toast("이 앱은 구간 이동을 제공하지 않습니다.");break;
        }
    }
    private void openPlayer(){
        Intent intent=state.controller==null?null:getPackageManager().getLaunchIntentForPackage(state.controller.getPackageName());
        if(intent==null)intent=Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,Intent.CATEGORY_APP_MUSIC);
        try{startActivity(intent);}catch(ActivityNotFoundException e){toast("스포티파이·유튜브 뮤직 등 음악 앱에서 노래를 재생해 주세요.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        if(request==202){super.onActivityResult(request,result,data);projectionPending=false;if(result==RESULT_OK&&data!=null)state.audio.startCapture(result,data);else{state.audio.stop();toast("기본 그라데이션으로 사용할게요.");}publish();return;}
        super.onActivityResult(request,result,data);if(request!=100||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();String selectedKey=state.song.key;state.cancelWork(pendingFileRead);
        pendingFileRead=state.network.submit(()->{try(InputStream in=getContentResolver().openInputStream(uri)){
            String text=LyricParser.decodeBytes(IoSupport.read(in,2*1024*1024,20000));IoSupport.checkInterrupted();
            handler.post(()->{if(isDestroyed())return;if(selectedKey.equals(state.song.key))state.importLyrics(text);else toast("곡이 변경되어 파일을 적용하지 않았어요. 다시 불러와 주세요.");});
        }catch(Exception e){if(Thread.currentThread().isInterrupted())return;handler.post(()->{if(!isDestroyed())toast(e instanceof IoSupport.TooLargeException?"가사 파일은 2MB 이하로 선택해 주세요.":e.getMessage()==null?"파일을 읽지 못했습니다.":e.getMessage());});}});
    }
}
