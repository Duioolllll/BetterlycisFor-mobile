package io.betterlyrics.widgettest;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/** Test document tree backed by generated audio and temporary exports. */
public final class FixtureDocuments extends DocumentsProvider {
    private final Map<String,String> exports=new ConcurrentHashMap<>();
    private static final String[] ROOTS={DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE,DocumentsContract.Root.COLUMN_FLAGS,DocumentsContract.Root.COLUMN_MIME_TYPES};
    private static final String[] DOCS={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS,DocumentsContract.Document.COLUMN_SIZE};
    @Override public boolean onCreate(){return true;}
    @Override public Cursor queryRoots(String[] projection){
        MatrixCursor result=new MatrixCursor(projection==null?ROOTS:projection);MatrixCursor.RowBuilder row=result.newRow();
        add(result,row,DocumentsContract.Root.COLUMN_ROOT_ID,"root");add(result,row,DocumentsContract.Root.COLUMN_DOCUMENT_ID,"root");add(result,row,DocumentsContract.Root.COLUMN_TITLE,"Test music");add(result,row,DocumentsContract.Root.COLUMN_FLAGS,DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD);add(result,row,DocumentsContract.Root.COLUMN_MIME_TYPES,"*/*");return result;
    }
    @Override public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException{
        MatrixCursor result=new MatrixCursor(projection==null?DOCS:projection);row(result,id);return result;
    }
    @Override public Cursor queryChildDocuments(String parent,String[] projection,String sort)throws FileNotFoundException{
        MatrixCursor result=new MatrixCursor(projection==null?DOCS:projection);
        if(parent.equals("root")){row(result,"album");row(result,"playlist");for(String id:exports.keySet())row(result,id);}else if(parent.equals("album")){row(result,"one");row(result,"two");row(result,"lyrics");}
        else throw new FileNotFoundException(parent);return result;
    }
    private void row(MatrixCursor result,String id)throws FileNotFoundException{
        String name,type;long size=0;
        switch(id){case "root":name="Test music";type=DocumentsContract.Document.MIME_TYPE_DIR;break;case "album":name="Album";type=DocumentsContract.Document.MIME_TYPE_DIR;break;
            case "playlist":name="fixture.m3u";type="audio/x-mpegurl";size=128;break;case "one":name="one.wav";type="audio/wav";size=320044;break;case "two":name="two.wav";type="audio/wav";size=320044;break;case "lyrics":name="one.lrc";type="text/plain";size=48;break;default:if(!exports.containsKey(id))throw new FileNotFoundException(id);name=exports.get(id);type=name.endsWith(".png")?"image/png":"audio/x-mpegurl";size=new File(getContext().getCacheDir(),id).length();}
        MatrixCursor.RowBuilder row=result.newRow();add(result,row,DocumentsContract.Document.COLUMN_DOCUMENT_ID,id);add(result,row,DocumentsContract.Document.COLUMN_DISPLAY_NAME,name);add(result,row,DocumentsContract.Document.COLUMN_MIME_TYPE,type);add(result,row,DocumentsContract.Document.COLUMN_FLAGS,id.equals("root")?DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE:exports.containsKey(id)?DocumentsContract.Document.FLAG_SUPPORTS_WRITE|DocumentsContract.Document.FLAG_SUPPORTS_DELETE:0);add(result,row,DocumentsContract.Document.COLUMN_SIZE,size);
    }
    private static void add(MatrixCursor cursor,MatrixCursor.RowBuilder row,String column,Object value){if(cursor.getColumnIndex(column)>=0)row.add(column,value);}
    @Override public boolean isChildDocument(String parent,String child){return parent.equals("root")&&(child.equals("album")||child.equals("playlist")||child.equals("one")||child.equals("two")||child.equals("lyrics")||exports.containsKey(child))||parent.equals("album")&&(child.equals("one")||child.equals("two")||child.equals("lyrics"));}
    @Override public String createDocument(String parent,String mime,String name)throws FileNotFoundException{if(!parent.equals("root")||!(mime.equals("audio/x-mpegurl")||mime.equals("image/png")))throw new FileNotFoundException(parent);String id="export-"+UUID.randomUUID();exports.put(id,name);return id;}
    @Override public void deleteDocument(String id)throws FileNotFoundException{if(exports.remove(id)==null)throw new FileNotFoundException(id);new File(getContext().getCacheDir(),id).delete();}
    @Override public synchronized ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{
        if(exports.containsKey(id)){int flags=mode.equals("r")?ParcelFileDescriptor.MODE_READ_ONLY:ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE;return ParcelFileDescriptor.open(new File(getContext().getCacheDir(),id),flags);}
        if(!mode.equals("r")||!id.equals("playlist")&&!id.equals("one")&&!id.equals("two")&&!id.equals("lyrics"))throw new FileNotFoundException(id);
        File file=new File(getContext().getCacheDir(),id);if(!file.isFile())try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file),16*1024)){
            if(id.equals("playlist"))out.write("#EXTM3U\n#EXTINF:20,Sample One\nAlbum/one.wav\nAlbum/two.wav\nAlbum/missing.wav\nhttps://example.invalid/stream\n".getBytes(StandardCharsets.UTF_8));
            else if(id.equals("lyrics"))out.write("[00:00.00]Fixture local lyric\n".getBytes(StandardCharsets.UTF_8));
            else writeWave(out,id.equals("one")?440:660);
        }catch(IOException error){throw new FileNotFoundException(error.toString());}
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    private static void le16(OutputStream out,int n)throws IOException{out.write(n&255);out.write((n>>>8)&255);}
    private static void le32(OutputStream out,int n)throws IOException{le16(out,n);le16(out,n>>>16);}
    private static void writeWave(OutputStream out,int frequency)throws IOException{
        int rate=8000,samples=rate*20,bytes=samples*2;out.write("RIFF".getBytes(StandardCharsets.US_ASCII));le32(out,36+bytes);out.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));le32(out,16);le16(out,1);le16(out,1);le32(out,rate);le32(out,rate*2);le16(out,2);le16(out,16);out.write("data".getBytes(StandardCharsets.US_ASCII));le32(out,bytes);
        for(int i=0;i<samples;i++)le16(out,(int)(Math.sin(2*Math.PI*frequency*i/rate)*1000));
    }
}
