package io.betterlyrics.mobile;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Private playlist index. Audio remains in documents explicitly selected by the user. */
final class LocalLibrary {
    static final int MAX_TRACKS=5000;
    private static LocalLibrary instance;
    static synchronized LocalLibrary get(Context context){
        if(instance==null)instance=new LocalLibrary(context.getApplicationContext());return instance;
    }
    private static JSONObject object(Object... values){
        JSONObject result=new JSONObject();
        try{for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);}
        catch(org.json.JSONException error){throw new IllegalStateException(error);}
        return result;
    }
    static final class Track {
        final String uri,title,artist,album;final long duration;
        Track(String uri,String title,String artist,String album,long duration){this.uri=uri;this.title=title;this.artist=artist;this.album=album;this.duration=duration;}
        JSONObject json(){return object("uri",uri,"title",title,"artist",artist,"album",album,"duration",duration);}
        JSONObject display(){return object("title",title,"artist",artist,"album",album,"duration",duration);}
    }
    static final class Playlist {
        final String id,name;final ArrayList<Track> tracks;
        Playlist(String id,String name,List<Track> tracks){this.id=id;this.name=name;this.tracks=new ArrayList<>(tracks);}
    }
    static final class ImportResult {
        final Playlist playlist;final int missing,unsupported;
        ImportResult(Playlist playlist,int missing,int unsupported){this.playlist=playlist;this.missing=missing;this.unsupported=unsupported;}
    }
    private final Context context;
    private final ArrayList<Playlist> playlists=new ArrayList<>();
    private LocalLibrary(Context context){this.context=context;load();}

    private void load(){
        try{
            JSONArray saved=new JSONArray(context.getSharedPreferences("local_music",0).getString("playlists","[]"));
            for(int i=0;i<Math.min(saved.length(),100);i++){
                JSONObject item=saved.optJSONObject(i);if(item==null)continue;JSONArray rows=item.optJSONArray("tracks");if(rows==null)continue;
                ArrayList<Track> tracks=new ArrayList<>();
                for(int j=0;j<Math.min(rows.length(),MAX_TRACKS);j++){
                    JSONObject row=rows.optJSONObject(j);if(row==null)continue;String uri=row.optString("uri");
                    if(!"content".equalsIgnoreCase(Uri.parse(uri).getScheme()))continue;
                    tracks.add(new Track(uri,row.optString("title"),row.optString("artist"),row.optString("album"),Math.max(0,row.optLong("duration"))));
                }
                String id=item.optString("id"),name=item.optString("name");if(!id.isEmpty()&&!tracks.isEmpty())playlists.add(new Playlist(id,name,tracks));
            }
        }catch(Exception ignored){}
    }
    private void save(){
        JSONArray all=new JSONArray();
        for(Playlist playlist:playlists){JSONArray tracks=new JSONArray();for(Track track:playlist.tracks)tracks.put(track.json());all.put(object("id",playlist.id,"name",playlist.name,"tracks",tracks));}
        context.getSharedPreferences("local_music",0).edit().putString("playlists",all.toString()).apply();
    }
    synchronized Playlist get(String id){for(Playlist item:playlists)if(item.id.equals(id))return item;return null;}
    synchronized JSONArray list(){
        JSONArray out=new JSONArray();for(Playlist item:playlists)out.put(object("id",item.id,"name",item.name,"count",item.tracks.size()));return out;
    }
    synchronized JSONObject detail(String id){
        Playlist item=get(id);if(item==null)return new JSONObject();JSONArray rows=new JSONArray();for(Track track:item.tracks)rows.put(track.display());
        return object("id",item.id,"name",item.name,"tracks",rows);
    }
    synchronized boolean remove(String id){
        boolean removed=playlists.removeIf(item->item.id.equals(id));if(removed)save();return removed;
    }
    private synchronized Playlist add(String name,List<Track> tracks){
        if(tracks.isEmpty())throw new IllegalArgumentException("불러올 수 있는 곡이 없습니다.");
        if(playlists.size()>=100)throw new IllegalArgumentException("재생목록은 최대 100개까지 저장할 수 있습니다.");
        Playlist item=new Playlist(UUID.randomUUID().toString(),name,tracks);playlists.add(item);save();return item;
    }
    ImportResult importFiles(List<Uri> uris)throws IOException {
        if(uris.size()>MAX_TRACKS)throw new IOException("재생목록은 최대 5,000곡까지 불러올 수 있습니다.");
        ArrayList<Track> tracks=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(Uri uri:uris){IoSupport.checkInterrupted();if(uri==null||!"content".equalsIgnoreCase(uri.getScheme())||!seen.add(uri.toString()))continue;tracks.add(metadata(uri,displayName(uri),"",0));}
        return new ImportResult(add("내 음악",tracks),0,0);
    }
    ImportResult importM3u(Uri file,Uri tree)throws Exception {
        ContentResolver resolver=context.getContentResolver();byte[] bytes;
        try(InputStream in=resolver.openInputStream(file)){bytes=IoSupport.read(in,2*1024*1024,20000);}
        M3uPlaylist.Result parsed=M3uPlaylist.parse(M3uPlaylist.decode(bytes));
        List<M3uPlaylist.Document> documents=scan(tree);M3uPlaylist.Matcher matcher=new M3uPlaylist.Matcher(documents);
        ArrayList<Track> tracks=new ArrayList<>();int missing=0;
        for(M3uPlaylist.Entry entry:parsed.entries){
            IoSupport.checkInterrupted();M3uPlaylist.Document document=matcher.match(entry);
            if(document==null){missing++;continue;}
            Uri uri=Uri.parse(document.uri);tracks.add(metadata(uri,document.path,entry.title,entry.durationMs));
        }
        String name=displayName(file).replaceFirst("(?i)\\.m3u8?$","").trim();if(name.isEmpty())name="재생목록";
        return new ImportResult(add(name,tracks),missing,parsed.unsupported);
    }
    private String displayName(Uri uri){
        try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst()){String value=c.getString(0);if(value!=null&&!value.trim().isEmpty())return value;}
        }catch(Exception ignored){}
        return uri.getLastPathSegment()==null?"음악":uri.getLastPathSegment();
    }
    private Track metadata(Uri uri,String fallback,String label,long duration){
        String path=fallback.replace('\\','/');String name=path.substring(path.lastIndexOf('/')+1).replaceFirst("\\.[^.]+$","");
        String title=label.isEmpty()?name:label,artist="",album="";long ms=Math.max(0,duration);
        MediaMetadataRetriever reader=new MediaMetadataRetriever();
        try{
            reader.setDataSource(context,uri);
            String t=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),a=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),b=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),d=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if(t!=null&&!t.trim().isEmpty())title=t.trim();if(a!=null)artist=a.trim();if(b!=null)album=b.trim();
            try{long n=Long.parseLong(d);if(n>0&&n<=86400000)ms=n;}catch(Exception ignored){}
        }catch(Exception ignored){}finally{try{reader.release();}catch(Exception ignored){}}
        if(title.trim().isEmpty())title="제목 없음";
        return new Track(uri.toString(),title,artist,album,ms);
    }
    private List<M3uPlaylist.Document> scan(Uri tree)throws IOException {
        if(tree==null||!"content".equalsIgnoreCase(tree.getScheme())||!DocumentsContract.isTreeUri(tree))throw new IOException("음악 폴더를 다시 선택해 주세요.");
        class Folder {final String id,path;final int depth;Folder(String id,String path,int depth){this.id=id;this.path=path;this.depth=depth;}}
        ArrayDeque<Folder> queue=new ArrayDeque<>();queue.add(new Folder(DocumentsContract.getTreeDocumentId(tree),"",0));
        Set<String> visited=new HashSet<>();List<M3uPlaylist.Document> files=new ArrayList<>();int scanned=0;
        while(!queue.isEmpty()){
            IoSupport.checkInterrupted();Folder folder=queue.removeFirst();if(!visited.add(folder.id))continue;
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,folder.id);
            String[] projection={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
            try(Cursor cursor=context.getContentResolver().query(children,projection,null,null,null)){
                if(cursor==null)throw new IOException("음악 폴더를 읽을 수 없습니다.");
                while(cursor.moveToNext()){
                    IoSupport.checkInterrupted();if(++scanned>20000)throw new IOException("선택한 폴더가 너무 큽니다. 더 작은 음악 폴더를 선택해 주세요.");
                    String id=cursor.getString(0),name=cursor.getString(1),type=cursor.getString(2);if(id==null||name==null)continue;
                    String path=folder.path.isEmpty()?name:folder.path+"/"+name;
                    if(DocumentsContract.Document.MIME_TYPE_DIR.equals(type)){
                        if(folder.depth>=20)throw new IOException("음악 폴더가 너무 깊습니다.");queue.addLast(new Folder(id,path,folder.depth+1));
                    }else if((type!=null&&type.startsWith("audio/"))||name.matches("(?i).*\\.(mp3|m4a|aac|wav|flac|ogg|opus|oga|wma|3gp|amr|aiff|aif)$")){
                        files.add(new M3uPlaylist.Document(path,DocumentsContract.buildDocumentUriUsingTree(tree,id).toString()));
                    }
                }
            }catch(SecurityException e){throw new IOException("음악 폴더 접근 권한이 없습니다.",e);}
        }
        return files;
    }
}
