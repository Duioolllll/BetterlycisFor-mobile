package io.betterlyrics.mobile;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import java.io.*;
import java.net.*;

/** Bounded, asynchronous album art loading. Never exposes remote URLs to the WebView. */
final class ArtworkResolver {
    static final class Source {
        Bitmap bitmap;Uri uri;Icon icon;Source next;
        String fingerprint(){String own=bitmap!=null?"bitmap:"+bitmap.getGenerationId()+":"+bitmap.getWidth()+":"+bitmap.getHeight():uri!=null?"uri:"+uri:icon!=null?"icon:"+icon.toString():"";return own+(next==null?"":";"+next.fingerprint());}
    }
    static Source metadata(MediaMetadata metadata){
        Source head=new Source(),tail=head;if(metadata==null)return head;
        for(String key:new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART,MediaMetadata.METADATA_KEY_ART,MediaMetadata.METADATA_KEY_ALBUM_ART_URI,MediaMetadata.METADATA_KEY_ART_URI,MediaMetadata.METADATA_KEY_DISPLAY_ICON,MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}){
            Source source=new Source();
            try{if(key.endsWith("_URI")){String value=metadata.getString(key);if(value==null||value.isEmpty())continue;source.uri=Uri.parse(value);}
                else{source.bitmap=metadata.getBitmap(key);if(source.bitmap==null||source.bitmap.isRecycled())continue;}
                tail.next=source;tail=source;
            }catch(Exception ignored){}
        }return head.next==null?head:head.next;
    }
    static String load(Context context,Source source){
        for(Source current=source;current!=null;current=current.next){if(Thread.currentThread().isInterrupted())return "";String image=loadSingle(context,current);if(!image.isEmpty())return image;}return "";
    }
    private static String loadSingle(Context context,Source source){
        Bitmap bitmap=null;boolean owned=false;
        try{
            bitmap=source.bitmap;
            if(bitmap==null&&source.uri!=null){bitmap=decode(read(context,source.uri));owned=true;}
            if(bitmap==null&&source.icon!=null){
                // Notification large icons may also be URI-backed, rather than bitmaps.
                if(Build.VERSION.SDK_INT>=28&&(source.icon.getType()==Icon.TYPE_URI||source.icon.getType()==Icon.TYPE_URI_ADAPTIVE_BITMAP)){bitmap=decode(read(context,source.icon.getUri()));owned=true;}
                Drawable drawable=bitmap==null?source.icon.loadDrawable(context):null;
                if(drawable instanceof BitmapDrawable)bitmap=((BitmapDrawable)drawable).getBitmap();
                else if(drawable!=null){bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);owned=true;drawable.setBounds(0,0,256,256);drawable.draw(new Canvas(bitmap));}
            }
            if(bitmap==null||bitmap.isRecycled())return "";
            int width=bitmap.getWidth(),height=bitmap.getHeight();float scale=Math.min(1,512f/Math.max(width,height));
            Bitmap small=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)),true);
            try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
                if(!small.compress(Bitmap.CompressFormat.JPEG,85,out))return "";
                return "data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
            }finally{if(small!=bitmap)small.recycle();}
        }catch(Exception|OutOfMemoryError ignored){return "";}finally{if(owned&&bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();}
    }
    private static Bitmap decode(byte[] bytes){
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)return null;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
        while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1024)options.inSampleSize*=2;
        return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
    }
    private static byte[] read(Context context,Uri uri)throws Exception{
        String scheme=uri.getScheme();
        if("content".equals(scheme)||"android.resource".equals(scheme))try(InputStream in=context.getContentResolver().openInputStream(uri)){return bounded(in);}
        if(!"https".equalsIgnoreCase(scheme))throw new IOException("Unsupported artwork URI");
        URL url=new URL(uri.toString());
        for(int redirect=0;redirect<4;redirect++){
            if(!"https".equalsIgnoreCase(url.getProtocol()))throw new IOException("HTTPS artwork required");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(5000);connection.setInstanceFollowRedirects(false);
            try{
                int status=connection.getResponseCode();
                if(status>=300&&status<400){String next=connection.getHeaderField("Location");if(next==null)throw new IOException("Missing redirect");url=new URL(url,next);continue;}
                if(status!=200)throw new IOException("Artwork HTTP "+status);
                if(connection.getContentLengthLong()>8*1024*1024)throw new IOException("Artwork too large");
                try(InputStream in=connection.getInputStream()){return bounded(in);}
            }finally{connection.disconnect();}
        }throw new IOException("Too many artwork redirects");
    }
    private static byte[] bounded(InputStream in)throws IOException{
        return IoSupport.read(in,8*1024*1024,20000);
    }
}
