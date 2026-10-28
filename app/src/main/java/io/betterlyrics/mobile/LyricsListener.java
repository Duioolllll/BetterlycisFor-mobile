package io.betterlyrics.mobile;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.*;
import android.media.session.MediaController;
import android.os.*;
import android.provider.Settings;
import android.service.notification.*;
import android.view.*;
import android.widget.*;
import android.text.TextUtils;
import org.json.*;
import java.util.*;

/** Android owns the binding lifecycle; this service is never started as a fake media FGS. */
public final class LyricsListener extends NotificationListenerService {
    private static LyricsListener live;
    private AppState state;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private MediaSessionManager manager;
    private final Map<MediaController,MediaController.Callback> callbacks=new LinkedHashMap<>();
    private final Map<String,String> appLabels=new LinkedHashMap<String,String>(32,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,String> entry){return size()>32;}};
    private boolean registered;
    private View overlay;
    private TextView overlayText,overlaySub;
    private WindowManager windows;
    private WindowManager.LayoutParams bounds;
    private final Runnable refreshTask=()->refreshSessions();
    private final MediaSessionManager.OnActiveSessionsChangedListener active=this::bindSessions;
    private final Runnable overlayTick=new Runnable(){public void run(){if(live!=LyricsListener.this)return;syncOverlay();if(overlay!=null)handler.postDelayed(this,150);}};
    private final Runnable widgetTick=new Runnable(){public void run(){if(live!=LyricsListener.this||!LyricsWidgetProvider.exists(LyricsListener.this))return;LyricsWidgetProvider.refreshAll(LyricsListener.this,state,false);long delay=LyricsWidgetProvider.nextDelay(state);if(delay>0)handler.postDelayed(this,delay);}};
    @Override public void onCreate(){super.onCreate();state=AppState.get(this);windows=(WindowManager)getSystemService(WINDOW_SERVICE);}
    @Override public void onListenerConnected(){
        super.onListenerConnected();live=this;state.connected=true;manager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
        try{manager.addOnActiveSessionsChangedListener(active,new ComponentName(this,LyricsListener.class),handler);registered=true;}catch(SecurityException ignored){}
        refreshSessions();state.emit();
    }
    @Override public void onListenerDisconnected(){cleanup();super.onListenerDisconnected();refresh(this);}
    @Override public void onDestroy(){cleanup();super.onDestroy();}
    private void cleanup(){
        if(registered&&manager!=null)try{manager.removeOnActiveSessionsChangedListener(active);}catch(Exception ignored){}
        registered=false;for(Map.Entry<MediaController,MediaController.Callback> e:callbacks.entrySet())try{e.getKey().unregisterCallback(e.getValue());}catch(Exception ignored){}callbacks.clear();appLabels.clear();handler.removeCallbacksAndMessages(null);removeOverlay();
        if(live==this){live=null;state.connected=false;if(!LocalPlaybackService.restoreState())state.reset();}
    }
    private boolean mediaNotification(Notification notification){return Notification.CATEGORY_TRANSPORT.equals(notification.category)||notification.extras!=null&&notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION);}
    @Override public void onNotificationPosted(StatusBarNotification sbn){if(mediaNotification(sbn.getNotification()))scheduleRefresh();}
    @Override public void onNotificationRemoved(StatusBarNotification sbn){if(mediaNotification(sbn.getNotification()))scheduleRefresh();}
    private void scheduleRefresh(){handler.removeCallbacks(refreshTask);handler.postDelayed(refreshTask,200);}
    public static void refresh(Context context){LyricsListener service=live;if(service!=null)service.handler.post(service.refreshTask);else if(AppState.get(context).granted())try{requestRebind(new ComponentName(context,LyricsListener.class));}catch(Exception ignored){}}
    public static void refreshOverlay(){LyricsListener service=live;if(service!=null){service.handler.removeCallbacks(service.overlayTick);service.handler.post(service.overlayTick);}}
    public static void refreshWidget(Context context){LyricsListener service=live;if(service!=null){service.handler.removeCallbacks(service.widgetTick);service.handler.post(service.widgetTick);}else if(!LocalPlaybackService.refreshWidget())LyricsWidgetProvider.refreshAll(context,AppState.get(context),false);}
    static ArtworkResolver.Source notificationArtwork(MediaController controller){
        ArtworkResolver.Source source=new ArtworkResolver.Source();LyricsListener service=live;
        if(service==null||controller==null)return source;
        try{
            MediaMetadata metadata=controller.getMetadata();String title=metadata==null?"":metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            StatusBarNotification[] notifications=service.getActiveNotifications();if(notifications==null)return source;
            for(StatusBarNotification item:notifications){
                if(!item.getPackageName().equals(controller.getPackageName()))continue;
                Notification notification=item.getNotification();Bundle extras=notification.extras;
                if(extras==null)continue;MediaSession.Token token=extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
                if(token==null||!token.equals(controller.getSessionToken()))continue;
                CharSequence shownTitle=extras.getCharSequence(Notification.EXTRA_TITLE);
                if(shownTitle!=null&&!MatchScore.normalized(shownTitle.toString()).equals(MatchScore.normalized(title)))continue;
                if(notification.getLargeIcon()!=null){source.icon=notification.getLargeIcon();return source;}
            }
        }catch(Exception ignored){}return source;
    }
    private void refreshSessions(){
        if(manager==null)return;
        try{bindSessions(manager.getActiveSessions(new ComponentName(this,LyricsListener.class)));}catch(SecurityException e){state.connected=false;if(!LocalPlaybackService.restoreState())state.reset();removeOverlay();}
    }
    private void bindSessions(List<MediaController> current){
        if(current==null)current=Collections.emptyList();
        Set<MediaSession.Token> tokens=new HashSet<>();for(MediaController c:current)tokens.add(c.getSessionToken());
        Iterator<Map.Entry<MediaController,MediaController.Callback>> it=callbacks.entrySet().iterator();
        while(it.hasNext()){Map.Entry<MediaController,MediaController.Callback> e=it.next();if(!tokens.contains(e.getKey().getSessionToken())){e.getKey().unregisterCallback(e.getValue());it.remove();}}
        for(MediaController c:current){boolean exists=false;for(MediaController old:callbacks.keySet())if(old.getSessionToken().equals(c.getSessionToken())){exists=true;break;}if(exists)continue;
            MediaController.Callback callback=new MediaController.Callback(){
                @Override public void onMetadataChanged(MediaMetadata metadata){select(true);}
                @Override public void onPlaybackStateChanged(PlaybackState playback){select(false);}
                @Override public void onSessionDestroyed(){scheduleRefresh();}
            };c.registerCallback(callback,handler);callbacks.put(c,callback);
        }
        select(true);
    }
    private void select(boolean metadataChanged){
        String pinned=state.prefs.getString("sourcePackage","");MediaController best=null;PlaybackState bestPlayback=null;int bestScore=-1;JSONArray list=new JSONArray();
        for(MediaController c:callbacks.keySet()) {
            MediaMetadata m=c.getMetadata();if(m==null||m.getString(MediaMetadata.METADATA_KEY_TITLE)==null)continue;
            PlaybackState p=c.getPlaybackState();boolean playing=p!=null&&p.getState()==PlaybackState.STATE_PLAYING;
            int score=(playing?100:0)+(p!=null&&p.getPosition()>=0?5:0)+(state.controller!=null&&c.getSessionToken().equals(state.controller.getSessionToken())?10:0)+(c.getPackageName().equals(pinned)?1000:0);
            if(score>bestScore){best=c;bestPlayback=p;bestScore=score;}
            String name=appLabels.get(c.getPackageName());
            if(name==null){name=c.getPackageName();try{name=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(name,0)).toString();}catch(Exception ignored){}appLabels.put(c.getPackageName(),name);}
            try{list.put(new JSONObject().put("packageName",c.getPackageName()).put("name",name).put("playing",playing));}catch(Exception ignored){}
        }
        boolean sourcesChanged=!list.toString().equals(state.sources.toString());
        state.sources=list;if(best==null&&LocalPlaybackService.restoreState())return;state.accept(best,bestPlayback,metadataChanged,sourcesChanged);
    }
    private int dp(float f){return Math.round(f*getResources().getDisplayMetrics().density);}
    private void syncOverlay(){
        boolean enabled=state.prefs.getBoolean("overlay",false)&&Settings.canDrawOverlays(this)&&state.connected;
        if(!enabled){removeOverlay();return;}
        if(overlay==null)createOverlay();
        if(overlay==null)return;
        long position=state.position()+state.offset();LyricParser.Line found=null;
        if(state.lyrics!=null)for(LyricParser.Line l:state.lyrics.lines){if(l.start>=0&&l.start<=position&&(!l.bg)&&(l.end<0||position<l.end))found=l;}
        String text=found==null?(state.song.title.isEmpty()?"음악을 재생해 주세요":state.song.title):found.text;
        String sub=found!=null&&!found.translation.isEmpty()?found.translation:state.song.artist;
        if(!TextUtils.equals(overlayText.getText(),text))overlayText.setText(text);
        if(!TextUtils.equals(overlaySub.getText(),sub))overlaySub.setText(sub);
        float alpha=state.playing()?1f:.78f;if(overlay.getAlpha()!=alpha)overlay.setAlpha(alpha);
    }
    private void createOverlay(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(12),dp(42),dp(12));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xE621242B);bg.setCornerRadius(dp(20));bg.setStroke(dp(1),0x40FFFFFF);panel.setBackground(bg);
        overlayText=new TextView(this);overlayText.setTextColor(Color.WHITE);overlayText.setTextSize(19);overlayText.setTypeface(Typeface.DEFAULT,Typeface.BOLD);overlayText.setMaxLines(3);panel.addView(overlayText);
        overlaySub=new TextView(this);overlaySub.setTextColor(0xB3FFFFFF);overlaySub.setTextSize(12);overlaySub.setMaxLines(2);panel.addView(overlaySub);
        FrameLayout container=new FrameLayout(this);container.addView(panel,new FrameLayout.LayoutParams(-1,-2));TextView close=new TextView(this);close.setText("×");close.setTextSize(25);close.setTextColor(Color.WHITE);close.setGravity(Gravity.CENTER);close.setContentDescription("플로팅 가사 닫기");FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.TOP|Gravity.END);container.addView(close,cp);close.setOnClickListener(v->{state.prefs.edit().putBoolean("overlay",false).apply();removeOverlay();state.emit();});
        bounds=new WindowManager.LayoutParams(Math.min(dp(380),getResources().getDisplayMetrics().widthPixels-dp(32)),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);bounds.gravity=Gravity.TOP|Gravity.START;bounds.x=dp(16);bounds.y=dp(120);
        panel.setOnTouchListener(new View.OnTouchListener(){float startX,startY;int x,y;boolean moved;public boolean onTouch(View v,android.view.MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:startX=e.getRawX();startY=e.getRawY();x=bounds.x;y=bounds.y;moved=false;return true;case MotionEvent.ACTION_MOVE:float dx=e.getRawX()-startX,dy=e.getRawY()-startY;moved|=Math.abs(dx)+Math.abs(dy)>dp(8);bounds.x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-bounds.width,x+(int)dx));bounds.y=Math.max(0,Math.min(getResources().getDisplayMetrics().heightPixels-dp(90),y+(int)dy));try{windows.updateViewLayout(container,bounds);}catch(Exception ignored){}return true;case MotionEvent.ACTION_UP:if(!moved)try{startActivity(new Intent(LyricsListener.this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Exception ignored){}return true;}return true;}});
        try{windows.addView(container,bounds);overlay=container;}catch(Exception e){state.prefs.edit().putBoolean("overlay",false).apply();}
    }
    private void removeOverlay(){handler.removeCallbacks(overlayTick);if(overlay!=null)try{windows.removeView(overlay);}catch(Exception ignored){}overlay=null;}
}
