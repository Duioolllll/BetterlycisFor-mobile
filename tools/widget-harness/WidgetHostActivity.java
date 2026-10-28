package io.betterlyrics.widgettest;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.*;
import android.view.ViewGroup;
import android.widget.*;

/** Disposable launcher host and synthetic media session for emulator-only tests. */
public final class WidgetHostActivity extends Activity {
    private static final int HOST_ID=1938;
    private AppWidgetHost host;
    private AppWidgetManager manager;
    private AppWidgetHostView widgetView;
    private TextView probeStatus;
    private int widgetId;
    private MediaSession session;
    private boolean playing;
    private long position=1000;
    private final long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SEEK_TO;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);host=new AppWidgetHost(this,HOST_ID);manager=AppWidgetManager.getInstance(this);
        widgetId=host.allocateAppWidgetId();ComponentName provider=new ComponentName("io.betterlyrics.mobile","io.betterlyrics.mobile.LyricsWidgetProvider");
        if(!manager.bindAppWidgetIdIfAllowed(widgetId,provider)){TextView error=new TextView(this);error.setText("WIDGET_BIND_FAILED");setContentView(error);return;}
        host.startListening();resize(320,140);
        widgetView=host.createView(this,widgetId,manager.getAppWidgetInfo(widgetId));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(12),dp(12),dp(12));
        root.addView(widgetView,new LinearLayout.LayoutParams(dp(320),dp(140)));probeStatus=new TextView(this);root.addView(probeStatus);setContentView(root);
    }
    private void resize(int width,int height){
        Bundle options=new Bundle();options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,width);options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,width);options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height);options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height);manager.updateAppWidgetOptions(widgetId,options);
        if(widgetView!=null){ViewGroup.LayoutParams layout=widgetView.getLayoutParams();layout.width=dp(width);layout.height=dp(height);widgetView.setLayoutParams(layout);}
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);String command=intent.getStringExtra("command");
        if("start".equals(command))startSession();
        if("resize".equals(command))resize(intent.getIntExtra("width",220),intent.getIntExtra("height",90));
        if("inspect".equals(command))inspectStyle();
    }
    private void inspectStyle(){
        try{
            android.content.res.Resources resources=getPackageManager().getResourcesForApplication("io.betterlyrics.mobile");
            android.view.View panel=widgetView.findViewById(resources.getIdentifier("widget_root","id","io.betterlyrics.mobile"));
            android.view.View art=widgetView.findViewById(resources.getIdentifier("widget_art","id","io.betterlyrics.mobile"));
            TextView lyric=widgetView.findViewById(resources.getIdentifier("widget_lyric","id","io.betterlyrics.mobile"));
            int color=((GradientDrawable)panel.getBackground()).getColor().getDefaultColor();
            float size=lyric.getTextSize()/getResources().getDisplayMetrics().scaledDensity;
            probeStatus.setText(color==0xF5F5F7EF&&art.getVisibility()==android.view.View.GONE&&Math.abs(size-22)<.5f?"WIDGET_STYLE_OK":"WIDGET_STYLE_WAIT");
        }catch(Exception error){probeStatus.setText("WIDGET_STYLE_WAIT");}
    }
    private void startSession(){
        if(session!=null)return;
        session=new MediaSession(this,"Widget test");
        session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){position=4500;playing=true;publish();}
            @Override public void onPause(){position=currentPosition();playing=false;publish();}
            @Override public void onSkipToNext(){position=300000;publish();}
            @Override public void onSkipToPrevious(){position=1000;publish();}
            @Override public void onSeekTo(long value){position=value;publish();}
        });
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"widget-test").putString(MediaMetadata.METADATA_KEY_TITLE,"Widget Test").putString(MediaMetadata.METADATA_KEY_ARTIST,"Artist").putString(MediaMetadata.METADATA_KEY_ALBUM,"Album").putLong(MediaMetadata.METADATA_KEY_DURATION,600000).build());
        publish();session.setActive(true);
        NotificationManager notifications=getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("widget-test","Widget test",NotificationManager.IMPORTANCE_LOW));
        Notification notification=new Notification.Builder(this,"widget-test").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Widget Test").setContentText("Artist").setCategory(Notification.CATEGORY_TRANSPORT).setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken())).setOngoing(true).build();
        notifications.notify(1,notification);
    }
    private long currentPosition(){PlaybackState state=session.getController().getPlaybackState();return state==null?position:state.getPosition()+(playing?Math.max(0,SystemClock.elapsedRealtime()-state.getLastPositionUpdateTime()):0);}
    private void publish(){session.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,position,playing?1f:0f,SystemClock.elapsedRealtime()).build());}
    @Override protected void onDestroy(){if(session!=null)session.release();if(host!=null)host.stopListening();super.onDestroy();}
}
