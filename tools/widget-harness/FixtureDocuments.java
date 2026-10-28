package io.betterlyrics.widgettest;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Read-only document tree backed by generated audio, used only in the test APK. */
public final class FixtureDocuments extends DocumentsProvider {
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
        if(parent.equals("root")){row(result,"album");row(result,"playlist");}else if(parent.equals("album")){row(result,"one");row(result,"two");}
        else throw new FileNotFoundException(parent);return result;
    }
    private void row(MatrixCursor result,String id)throws FileNotFoundException{
        String name,type;long size=0;
        switch(id){case "root":name="Test music";type=DocumentsContract.Document.MIME_TYPE_DIR;break;case "album":name="Album";type=DocumentsContract.Document.MIME_TYPE_DIR;break;
            case "playlist":name="fixture.m3u";type="audio/x-mpegurl";size=128;break;case "one":name="one.wav";type="audio/wav";size=320044;break;case "two":name="two.wav";type="audio/wav";size=320044;break;default:throw new FileNotFoundException(id);}
        MatrixCursor.RowBuilder row=result.newRow();add(result,row,DocumentsContract.Document.COLUMN_DOCUMENT_ID,id);add(result,row,DocumentsContract.Document.COLUMN_DISPLAY_NAME,name);add(result,row,DocumentsContract.Document.COLUMN_MIME_TYPE,type);add(result,row,DocumentsContract.Document.COLUMN_FLAGS,0);add(result,row,DocumentsContract.Document.COLUMN_SIZE,size);
    }
    private static void add(MatrixCursor cursor,MatrixCursor.RowBuilder row,String column,Object value){if(cursor.getColumnIndex(column)>=0)row.add(column,value);}
    @Override public boolean isChildDocument(String parent,String child){return parent.equals("root")&&(child.equals("album")||child.equals("playlist")||child.equals("one")||child.equals("two"))||parent.equals("album")&&(child.equals("one")||child.equals("two"));}
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{
        if(!mode.equals("r")||!id.equals("playlist")&&!id.equals("one")&&!id.equals("two"))throw new FileNotFoundException(id);
        File file=new File(getContext().getCacheDir(),id);if(!file.isFile())try(FileOutputStream out=new FileOutputStream(file)){
            if(id.equals("playlist"))out.write("#EXTM3U\n#EXTINF:20,Sample One\nAlbum/one.wav\nAlbum/two.wav\nAlbum/missing.wav\nhttps://example.invalid/stream\n".getBytes(StandardCharsets.UTF_8));
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
