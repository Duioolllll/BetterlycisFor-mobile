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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        final String uri,title,artist,album,lyricUri;final long duration;
        Track(String uri,String title,String artist,String album,long duration,String lyricUri){this.uri=uri;this.title=title;this.artist=artist;this.album=album;this.duration=duration;this.lyricUri=lyricUri;}
        JSONObject json(){return object("uri",uri,"title",title,"artist",artist,"album",album,"duration",duration,"lyricUri",lyricUri);}
        JSONObject display(){return object("title",title,"artist",artist,"album",album,"duration",duration);}
    }
    static final class Playlist {
        final String id,name;final ArrayList<Track> tracks;
        Playlist(String id,String name,List<Track> tracks){this.id=id;this.name=name;this.tracks=new ArrayList<>(tracks);}
    }
    static final class ImportResult {
        final Playlist playlist;final int added,missing,unsupported;
        ImportResult(Playlist playlist,int added,int missing,int unsupported){this.playlist=playlist;this.added=added;this.missing=missing;this.unsupported=unsupported;}
    }
    static final class ExportResult {
        final int exported,skipped;final String name;
        ExportResult(int exported,int skipped,String name){this.exported=exported;this.skipped=skipped;this.name=name;}
    }
    private final Context context;
    private final ArrayList<Playlist> playlists=new ArrayList<>();
    private Map<String,Playlist> groups;private JSONArray artists,albums;private boolean groupsTruncated;
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
                    String lyric=row.optString("lyricUri");if(!lyric.isEmpty()&&!"content".equalsIgnoreCase(Uri.parse(lyric).getScheme()))lyric="";
                    tracks.add(new Track(uri,row.optString("title"),row.optString("artist"),row.optString("album"),Math.max(0,row.optLong("duration")),lyric));
                }
                String id=item.optString("id"),name=item.optString("name");if(!id.isEmpty()&&!tracks.isEmpty())playlists.add(new Playlist(id,name,tracks));
            }
        }catch(Exception ignored){}
    }
    private void save(){
        JSONArray all=new JSONArray();
        for(Playlist playlist:playlists){JSONArray tracks=new JSONArray();for(Track track:playlist.tracks)tracks.put(track.json());all.put(object("id",playlist.id,"name",playlist.name,"tracks",tracks));}
        context.getSharedPreferences("local_music",0).edit().putString("playlists",all.toString()).apply();
        groups=null;artists=null;albums=null;
    }
    synchronized Playlist get(String id){for(Playlist item:playlists)if(item.id.equals(id))return item;if(id!=null&&id.startsWith("group:")){ensureGroups();return groups.get(id);}return null;}
    synchronized JSONArray list(){
        JSONArray out=new JSONArray();for(Playlist item:playlists)out.put(object("id",item.id,"name",item.name,"count",item.tracks.size()));return out;
    }
    synchronized JSONObject detail(String id){
        Playlist item=get(id);if(item==null)return new JSONObject();JSONArray rows=new JSONArray();for(Track track:item.tracks)rows.put(track.display());
        return object("id",item.id,"name",item.name,"tracks",rows,"readOnly",item.id.startsWith("group:"));
    }
    synchronized JSONObject grouped(){ensureGroups();return object("artists",artists,"albums",albums,"truncated",groupsTruncated);}
    private static String groupValue(String text,String fallback){String clean=text==null?"":text.trim();return clean.isEmpty()||clean.equals("<unknown>")?fallback:clean;}
    private static String groupId(String kind,String value){return "group:"+kind+":"+UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
    private static String groupKey(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
    private synchronized void ensureGroups(){
        if(groups!=null)return;
        LinkedHashMap<String,ArrayList<Track>> byArtist=new LinkedHashMap<>(),byAlbum=new LinkedHashMap<>();
        Map<String,String> artistLabels=new HashMap<>(),albumLabels=new HashMap<>(),albumArtists=new HashMap<>();Set<String> seen=new HashSet<>();groupsTruncated=false;
        outer:for(Playlist playlist:playlists)for(Track track:playlist.tracks){
            String identity=documentIdentity(Uri.parse(track.uri));if(!seen.add(identity))continue;
            if(seen.size()>20000){groupsTruncated=true;break outer;}
            String artist=groupValue(track.artist,"아티스트 정보 없음"),album=groupValue(track.album,"앨범 정보 없음");
            String ak=groupKey(artist),bk=groupKey(album)+"\n"+ak;
            byArtist.computeIfAbsent(ak,k->new ArrayList<>()).add(track);artistLabels.putIfAbsent(ak,artist);
            byAlbum.computeIfAbsent(bk,k->new ArrayList<>()).add(track);albumLabels.putIfAbsent(bk,album);albumArtists.putIfAbsent(bk,artist);
        }
        groups=new HashMap<>();artists=groupRows("artist",byArtist,artistLabels,null);albums=groupRows("album",byAlbum,albumLabels,albumArtists);
    }
    private JSONArray groupRows(String kind,Map<String,ArrayList<Track>> source,Map<String,String> labels,Map<String,String> subtitles){
        ArrayList<String> keys=new ArrayList<>(source.keySet());Collections.sort(keys,(a,b)->{int n=String.CASE_INSENSITIVE_ORDER.compare(labels.get(a),labels.get(b));return n!=0?n:a.compareTo(b);});
        JSONArray out=new JSONArray();for(String key:keys){String id=groupId(kind,key),name=labels.get(key);Playlist group=new Playlist(id,name,source.get(key));groups.put(id,group);out.put(object("id",id,"name",name,"subtitle",subtitles==null?"":subtitles.get(key),"count",group.tracks.size()));}return out;
    }
    synchronized boolean remove(String id){
        boolean removed=playlists.removeIf(item->item.id.equals(id));if(removed)save();return removed;
    }
    synchronized boolean rename(String id,String name){
        String clean=name.trim();if(clean.isEmpty()||clean.length()>80)return false;
        for(int i=0;i<playlists.size();i++){Playlist old=playlists.get(i);if(old.id.equals(id)){playlists.set(i,new Playlist(old.id,clean,old.tracks));save();return true;}}
        return false;
    }
    synchronized boolean removeTrack(String id,int index){
        for(int i=0;i<playlists.size();i++){
            Playlist old=playlists.get(i);if(!old.id.equals(id))continue;if(index<0||index>=old.tracks.size())return false;
            ArrayList<Track> tracks=new ArrayList<>(old.tracks);tracks.remove(index);
            if(tracks.isEmpty())playlists.remove(i);else playlists.set(i,new Playlist(old.id,old.name,tracks));save();return true;
        }
        return false;
    }
    synchronized boolean moveTrack(String id,int from,int to){
        for(int i=0;i<playlists.size();i++){
            Playlist old=playlists.get(i);if(!old.id.equals(id))continue;
            if(from<0||to<0||from>=old.tracks.size()||to>=old.tracks.size()||from==to)return false;
            ArrayList<Track> tracks=new ArrayList<>(old.tracks);Track moved=tracks.remove(from);tracks.add(to,moved);playlists.set(i,new Playlist(old.id,old.name,tracks));save();return true;
        }return false;
    }
    private synchronized Playlist add(String name,List<Track> tracks){
        if(tracks.isEmpty())throw new IllegalArgumentException("불러올 수 있는 곡이 없습니다.");
        if(playlists.size()>=100)throw new IllegalArgumentException("재생목록은 최대 100개까지 저장할 수 있습니다.");
        Playlist item=new Playlist(UUID.randomUUID().toString(),name,tracks);playlists.add(item);save();return item;
    }
    private synchronized Playlist append(String id,List<Track> added){
        for(int i=0;i<playlists.size();i++){
            Playlist old=playlists.get(i);if(!old.id.equals(id))continue;
            ArrayList<Track> tracks=new ArrayList<>(old.tracks);Set<String> seen=new HashSet<>();for(Track track:tracks)seen.add(track.uri);
            for(Track track:added)if(seen.add(track.uri))tracks.add(track);
            if(tracks.size()==old.tracks.size())throw new IllegalArgumentException("이미 추가된 음악입니다.");
            if(tracks.size()>MAX_TRACKS)throw new IllegalArgumentException("재생목록은 최대 5,000곡까지 저장할 수 있습니다.");
            Playlist updated=new Playlist(old.id,old.name,tracks);playlists.set(i,updated);save();return updated;
        }
        throw new IllegalArgumentException("재생목록을 찾을 수 없습니다.");
    }
    ImportResult importFiles(List<Uri> uris,String target)throws IOException {
        if(uris.size()>MAX_TRACKS)throw new IOException("재생목록은 최대 5,000곡까지 불러올 수 있습니다.");
        ArrayList<Track> tracks=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(Uri uri:uris){IoSupport.checkInterrupted();if(uri==null||!"content".equalsIgnoreCase(uri.getScheme())||!seen.add(uri.toString()))continue;tracks.add(metadata(uri,displayName(uri),"",0,""));}
        IoSupport.checkInterrupted();Playlist previous=target.isEmpty()?null:get(target);int before=previous==null?0:previous.tracks.size();
        Playlist saved=target.isEmpty()?add("내 음악",tracks):append(target,tracks);
        return new ImportResult(saved,saved.tracks.size()-before,0,0);
    }
    ImportResult importM3u(Uri file,Uri tree)throws Exception {
        ContentResolver resolver=context.getContentResolver();byte[] bytes;
        try(InputStream in=resolver.openInputStream(file)){bytes=IoSupport.read(in,2*1024*1024,20000);}
        M3uPlaylist.Result parsed=M3uPlaylist.parse(M3uPlaylist.decode(bytes));
        List<M3uPlaylist.Document> documents=scan(tree),audio=new ArrayList<>();Map<String,String> lyrics=new HashMap<>();
        for(M3uPlaylist.Document document:documents){
            if(document.path.matches("(?i).*\\.lrc$")){String key=lyricKey(document.path);if(lyrics.containsKey(key))lyrics.put(key,"");else lyrics.put(key,document.uri);}
            else audio.add(document);
        }
        M3uPlaylist.Matcher matcher=new M3uPlaylist.Matcher(audio);
        ArrayList<Track> tracks=new ArrayList<>();int missing=0;
        for(M3uPlaylist.Entry entry:parsed.entries){
            IoSupport.checkInterrupted();M3uPlaylist.Document document=matcher.match(entry);
            if(document==null){missing++;continue;}
            Uri uri=Uri.parse(document.uri);tracks.add(metadata(uri,document.path,entry.title,entry.durationMs,lyrics.getOrDefault(lyricKey(document.path),"")));
        }
        IoSupport.checkInterrupted();String name=displayName(file).replaceFirst("(?i)\\.m3u8?$","").trim();if(name.isEmpty())name="재생목록";
        return new ImportResult(add(name,tracks),tracks.size(),missing,parsed.unsupported);
    }
    ImportResult importFolder(Uri tree)throws Exception {
        List<M3uPlaylist.Document> documents=scan(tree);Map<String,String> lyrics=new HashMap<>();
        for(M3uPlaylist.Document document:documents)if(document.path.matches("(?i).*\\.lrc$")){String key=lyricKey(document.path);if(lyrics.containsKey(key))lyrics.put(key,"");else lyrics.put(key,document.uri);}
        ArrayList<Track> tracks=new ArrayList<>();
        for(M3uPlaylist.Document document:documents){
            IoSupport.checkInterrupted();if(document.path.matches("(?i).*\\.(lrc|m3u|m3u8)$"))continue;
            if(tracks.size()>=MAX_TRACKS)throw new IOException("폴더의 음악은 최대 5,000곡까지 불러올 수 있습니다.");
            tracks.add(metadata(Uri.parse(document.uri),document.path,"",0,lyrics.getOrDefault(lyricKey(document.path),"")));
        }
        Uri root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));String name=displayName(root).trim();
        if(name.isEmpty())name="음악 폴더";if(name.length()>80)name=name.substring(0,80);
        return new ImportResult(add(name,tracks),tracks.size(),0,0);
    }
    private static String documentIdentity(Uri uri){
        try{return String.valueOf(uri.getAuthority())+"|"+DocumentsContract.getDocumentId(uri);}catch(Exception ignored){return uri.toString();}
    }
    ExportResult exportM3u(String id,Uri tree)throws Exception {
        Playlist source=get(id);if(source==null||id.startsWith("group:"))throw new IOException("내보낼 재생목록을 선택해 주세요.");
        List<M3uPlaylist.Document> docs=scan(tree);Map<String,String> paths=new HashMap<>();Map<String,Integer> counts=new HashMap<>();
        for(M3uPlaylist.Document doc:docs){
            if(!M3uPlaylist.exportablePath(doc.path))continue;
            String identity=documentIdentity(Uri.parse(doc.uri)),key=groupKey(doc.path);counts.put(key,counts.getOrDefault(key,0)+1);
            if(paths.containsKey(identity)&&!doc.path.equals(paths.get(identity)))paths.put(identity,"");else if(!paths.containsKey(identity))paths.put(identity,doc.path);
        }
        ArrayList<M3uPlaylist.ExportEntry> entries=new ArrayList<>();int skipped=0;
        for(Track track:source.tracks){IoSupport.checkInterrupted();String path=paths.get(documentIdentity(Uri.parse(track.uri)));
            if(path==null||path.isEmpty()||counts.getOrDefault(groupKey(path),0)!=1){skipped++;continue;}
            entries.add(new M3uPlaylist.ExportEntry(path,track.title,track.artist,track.duration));
        }
        if(entries.isEmpty())throw new IOException("선택한 폴더에서 재생목록의 음악을 찾지 못했습니다.");
        String safe=source.name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();if(safe.isEmpty())safe="재생목록";if(safe.length()>60)safe=safe.substring(0,60);
        String name=safe+"-"+UUID.randomUUID().toString().substring(0,8)+".m3u8";
        Uri root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));Uri output=null;
        try{
            output=DocumentsContract.createDocument(context.getContentResolver(),root,"audio/x-mpegurl",name);if(output==null)throw new IOException("재생목록 파일을 만들 수 없습니다.");
            try(OutputStream stream=context.getContentResolver().openOutputStream(output,"w")){if(stream==null)throw new IOException("재생목록 파일을 열 수 없습니다.");stream.write(M3uPlaylist.export(entries).getBytes(StandardCharsets.UTF_8));}
        }catch(Exception error){if(output!=null)try{DocumentsContract.deleteDocument(context.getContentResolver(),output);}catch(Exception ignored){}throw error;}
        return new ExportResult(entries.size(),skipped,name);
    }
    private String displayName(Uri uri){
        try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst()){String value=c.getString(0);if(value!=null&&!value.trim().isEmpty())return value;}
        }catch(Exception ignored){}
        return uri.getLastPathSegment()==null?"음악":uri.getLastPathSegment();
    }
    private String lyricKey(String path){return java.text.Normalizer.normalize(path.replaceFirst("\\.[^.]+$",""),java.text.Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
    private Track metadata(Uri uri,String fallback,String label,long duration,String lyricUri){
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
        return new Track(uri.toString(),title,artist,album,ms,lyricUri);
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
                    }else if((type!=null&&type.startsWith("audio/"))||name.matches("(?i).*\\.(lrc|mp3|m4a|aac|wav|flac|ogg|opus|oga|wma|3gp|amr|aiff|aif)$")){
                        files.add(new M3uPlaylist.Document(path,DocumentsContract.buildDocumentUriUsingTree(tree,id).toString()));
                    }
                }
            }catch(SecurityException e){throw new IOException("음악 폴더 접근 권한이 없습니다.",e);}
        }
        return files;
    }
}
