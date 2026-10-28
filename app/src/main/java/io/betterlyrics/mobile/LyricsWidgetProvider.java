package io.betterlyrics.mobile;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.util.Base64;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import java.util.*;

/** Launcher widget. Updates are event-driven and scheduled at lyric boundaries. */
public final class LyricsWidgetProvider extends AppWidgetProvider {
    private static final Map<Integer,List<Object>> rendered=new HashMap<>();
    private static Boolean hasWidgets;
    private static String cachedArtwork="";
    private static Bitmap cachedBitmap;

    static boolean exists(Context context){
        if(hasWidgets==null)hasWidgets=AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context,LyricsWidgetProvider.class)).length>0;
        return hasWidgets;
    }
    @Override public void onEnabled(Context context){hasWidgets=true;LyricsListener.refresh(context);}
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){
        hasWidgets=ids.length>0||exists(context);refreshAll(context,AppState.get(context),true);LyricsListener.refresh(context);LyricsListener.refreshWidget(context);
    }
    @Override public void onAppWidgetOptionsChanged(Context context,AppWidgetManager manager,int id,Bundle options){
        super.onAppWidgetOptionsChanged(context,manager,id,options);hasWidgets=true;refreshAll(context,AppState.get(context),true);LyricsListener.refreshWidget(context);
    }
    @Override public void onDeleted(Context context,int[] ids){for(int id:ids)rendered.remove(id);}
    @Override public void onDisabled(Context context){hasWidgets=false;rendered.clear();cachedArtwork="";cachedBitmap=null;LyricsListener.refreshWidget(context);}

    static long nextDelay(AppState state){
        if(!state.playing()||!state.positionKnown()||state.lyrics==null)return -1;
        long position=state.position()+state.offset();
        WidgetLyrics.Selection selected=WidgetLyrics.select(state.lyrics.lines,position,true);
        return WidgetLyrics.delay(position,selected.nextChange,state.playbackSpeed());
    }
    static void refreshAll(Context context,AppState state,boolean force){
        if(!exists(context))return;
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        int[] ids=manager.getAppWidgetIds(new ComponentName(context,LyricsWidgetProvider.class));
        if(ids.length==0){hasWidgets=false;rendered.clear();return;}
        Set<Integer> active=new HashSet<>();for(int id:ids)active.add(id);rendered.keySet().retainAll(active);
        boolean local=state.controller!=null&&context.getPackageName().equals(state.controller.getPackageName());
        boolean permission=local||state.connected||state.granted(),connected=local||state.connected,playing=state.playing();
        long actions=state.playbackActions();
        boolean canPlay=(actions&(playing?(PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE):(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_PAUSE)))!=0;
        boolean canPrevious=(actions&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0,canNext=(actions&PlaybackState.ACTION_SKIP_TO_NEXT)!=0;
        String title=state.song.title.isEmpty()?context.getString(R.string.widget_name):state.song.title;
        String artist=state.song.artist;
        String lyric;
        if(!permission)lyric=context.getString(R.string.widget_connect);
        else if(!connected)lyric="음악 연결 중";
        else if(state.song.key.isEmpty())lyric="음악 재생 대기";
        else if(state.loading)lyric="가사 검색 중";
        else if(state.lyrics==null||state.lyrics.lines.isEmpty())lyric="가사 없음";
        else {
            WidgetLyrics.Selection selection=WidgetLyrics.select(state.lyrics.lines,state.position()+state.offset(),state.positionKnown());
            lyric=selection.text.isEmpty()?"재생 위치 대기":selection.text;
        }
        String theme=state.prefs.getString("widgetTheme","dark"),size=state.prefs.getString("widgetSize","medium");
        boolean showCover=state.prefs.getBoolean("widgetCover",true);
        boolean light="light".equals(theme);
        for(int id:ids){
            Bundle options=manager.getAppWidgetOptions(id);
            int width=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,250);
            int height=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,110);
            boolean artVisible=showCover&&width>=240;
            List<Object> signature=Arrays.asList(width,height,title,artist,lyric,artVisible?state.cover:"",theme,size,playing,canPlay,canPrevious,canNext);
            if(!force&&signature.equals(rendered.get(id)))continue;
            RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.lyrics_widget);
            int background=light?R.drawable.widget_background_light:"clear".equals(theme)?R.drawable.widget_background_clear:R.drawable.widget_background_dark;
            int primary=light?0xFF17251B:0xFFF5F7EF,secondary=light?0xFF4D5E51:0xFFBAC8BC;
            views.setInt(R.id.widget_root,"setBackgroundResource",background);
            views.setTextViewText(R.id.widget_title,title);views.setTextColor(R.id.widget_title,primary);
            views.setTextViewText(R.id.widget_artist,artist);views.setTextColor(R.id.widget_artist,secondary);
            views.setTextViewText(R.id.widget_lyric,lyric);views.setTextColor(R.id.widget_lyric,primary);
            views.setInt(R.id.widget_lyric,"setMaxLines",height<130?1:2);
            views.setTextViewTextSize(R.id.widget_lyric,TypedValue.COMPLEX_UNIT_SP,"small".equals(size)?16:"large".equals(size)?22:19);
            views.setViewVisibility(R.id.widget_previous,width<300?View.GONE:View.VISIBLE);
            views.setViewVisibility(R.id.widget_art,artVisible?View.VISIBLE:View.GONE);
            if(artVisible){Bitmap cover=artwork(state.cover);if(cover==null)views.setImageViewResource(R.id.widget_art,R.drawable.widget_art_placeholder);else views.setImageViewBitmap(R.id.widget_art,cover);}
            views.setImageViewResource(R.id.widget_play,playing?(light?R.drawable.widget_pause_dark:R.drawable.widget_pause_light):(light?R.drawable.widget_play_dark:R.drawable.widget_play_light));
            views.setImageViewResource(R.id.widget_previous,light?R.drawable.widget_previous_dark:R.drawable.widget_previous_light);
            views.setImageViewResource(R.id.widget_next,light?R.drawable.widget_next_dark:R.drawable.widget_next_light);
            views.setContentDescription(R.id.widget_play,context.getString(playing?R.string.widget_pause:R.string.widget_play));
            control(context,views,R.id.widget_play,WidgetActionReceiver.TOGGLE,canPlay);
            control(context,views,R.id.widget_previous,WidgetActionReceiver.PREVIOUS,canPrevious);
            control(context,views,R.id.widget_next,WidgetActionReceiver.NEXT,canNext);
            Intent open=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            views.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            manager.updateAppWidget(id,views);rendered.put(id,signature);
        }
    }
    private static void control(Context context,RemoteViews views,int id,String action,boolean enabled){
        views.setFloat(id,"setAlpha",enabled?1f:.3f);
        Intent intent=new Intent(context,WidgetActionReceiver.class).setAction(action);
        views.setOnClickPendingIntent(id,enabled?PendingIntent.getBroadcast(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE):null);
    }
    private static Bitmap artwork(String data){
        if(Objects.equals(cachedArtwork,data))return cachedBitmap;
        cachedArtwork=data;cachedBitmap=null;
        if(data==null||data.length()>3000000||!data.startsWith("data:image/"))return null;
        try{
            int comma=data.indexOf(',');if(comma<0)return null;
            byte[] bytes=Base64.decode(data.substring(comma+1),Base64.DEFAULT);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
            while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>192)options.inSampleSize*=2;
            cachedBitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        }catch(IllegalArgumentException|OutOfMemoryError ignored){}
        return cachedBitmap;
    }
}
