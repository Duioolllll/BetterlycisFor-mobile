package io.betterlyrics.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import java.io.IOException;
import java.io.OutputStream;

/** Saves a snapshot of the current lyric without exposing the app's private cache. */
final class LyricCard {
    private static final int SIZE=1080;
    static final class Data {
        final String title,artist,lyric;
        Data(String title,String artist,String lyric){this.title=limit(title,160);this.artist=limit(artist,160);this.lyric=limit(lyric,600);}
        Bundle bundle(){Bundle b=new Bundle();b.putString("title",title);b.putString("artist",artist);b.putString("lyric",lyric);return b;}
        static Data from(Bundle b){return b==null?null:new Data(b.getString("title",""),b.getString("artist",""),b.getString("lyric",""));}
    }
    private static String limit(String value,int max){if(value==null)return "";String text=value.trim();return text.length()>max?text.substring(0,max):text;}
    static Data capture(AppState state){
        if(state==null||state.lyrics==null||state.lyrics.lines==null||state.lyrics.lines.isEmpty())return null;
        long position=state.position()+state.offset();LyricParser.Line active=null,first=null;
        for(LyricParser.Line line:state.lyrics.lines){
            if(line==null||line.bg||line.text==null||line.text.trim().isEmpty())continue;if(first==null)first=line;
            if(line.start>=0&&line.start<=position&&(line.end<0||position<line.end)&&(active==null||line.start>=active.start))active=line;
        }
        LyricParser.Line selected=active==null?first:active;if(selected==null)return null;
        return new Data(state.song.title,state.song.artist,selected.text);
    }
    static void write(Context context,Uri destination,Data data)throws IOException{
        if(destination==null||data==null||data.lyric.isEmpty())throw new IOException("저장할 가사가 없습니다.");
        Bitmap bitmap=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);
        try{
            Canvas canvas=new Canvas(bitmap);Paint background=new Paint(Paint.ANTI_ALIAS_FLAG);
            background.setShader(new LinearGradient(0,0,SIZE,SIZE,0xff192f29,0xff292138,Shader.TileMode.CLAMP));canvas.drawRect(0,0,SIZE,SIZE,background);background.setShader(null);
            background.setColor(0x33000000);canvas.drawRoundRect(72,72,SIZE-72,SIZE-72,44,44,background);
            TextPaint lyric=new TextPaint(Paint.ANTI_ALIAS_FLAG);lyric.setColor(Color.WHITE);lyric.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));lyric.setTextSize(68);
            StaticLayout text=StaticLayout.Builder.obtain(data.lyric,0,data.lyric.length(),lyric,800).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(12,1.12f).setMaxLines(6).setEllipsize(TextUtils.TruncateAt.END).build();
            canvas.save();canvas.translate(140,Math.max(190,(SIZE-text.getHeight())/2f-30));text.draw(canvas);canvas.restore();
            TextPaint meta=new TextPaint(Paint.ANTI_ALIAS_FLAG);meta.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));meta.setColor(0xffe5f1e3);meta.setTextSize(32);
            String title=TextUtils.ellipsize(data.title,meta,800,TextUtils.TruncateAt.END).toString();canvas.drawText(title,140,890,meta);
            meta.setColor(0xffb8cab9);meta.setTextSize(25);String artist=TextUtils.ellipsize(data.artist,meta,800,TextUtils.TruncateAt.END).toString();canvas.drawText(artist,140,930,meta);
            try(OutputStream out=context.getContentResolver().openOutputStream(destination,"w")){if(out==null||!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("가사 카드를 저장하지 못했습니다.");}
        }finally{bitmap.recycle();}
    }
}
