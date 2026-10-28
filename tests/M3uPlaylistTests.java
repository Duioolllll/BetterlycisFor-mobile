import io.betterlyrics.mobile.M3uPlaylist;
import java.util.Arrays;
import java.util.Collections;

public final class M3uPlaylistTests {
    private static int checks;
    private static void ok(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    public static void main(String[] args){
        M3uPlaylist.Result list=M3uPlaylist.parse("\ufeff#EXTM3U\r\n#EXTINF:123,가수 - 노래\r\nMusic\\노래.mp3\r\n#EXTINF:-1,다른 곡\nfile:///C:/Music/A%20B+%2520.flac\nhttps://example.com/stream\n# comment\n");
        ok(list.entries.size()==2&&list.unsupported==1,"local entries and network streams distinguished");
        ok(list.entries.get(0).title.equals("가수 - 노래")&&list.entries.get(0).durationMs==123000,"EXTINF metadata");
        ok(list.entries.get(1).durationMs==-1,"unknown duration");
        M3uPlaylist.Document song=new M3uPlaylist.Document("Music/노래.mp3","content://tree/1");
        M3uPlaylist.Document encoded=new M3uPlaylist.Document("Music/A B+%20.flac","content://tree/2");
        ok(M3uPlaylist.match(list.entries.get(0),Arrays.asList(song,encoded))==song,"Windows relative path matches folder");
        ok(M3uPlaylist.match(list.entries.get(1),Arrays.asList(song,encoded))==encoded,"file URI decodes once and preserves plus");
        M3uPlaylist.Result folder=M3uPlaylist.parse("C:\\Old\\Artist\\same.mp3\n");
        M3uPlaylist.Document wrong=new M3uPlaylist.Document("Other/same.mp3","content://tree/3");
        M3uPlaylist.Document right=new M3uPlaylist.Document("Artist/same.mp3","content://tree/4");
        ok(M3uPlaylist.match(folder.entries.get(0),Arrays.asList(wrong,right))==right,"longest path suffix wins");
        M3uPlaylist.Document duplicate=new M3uPlaylist.Document("Else/same.mp3","content://tree/5");
        ok(M3uPlaylist.match(folder.entries.get(0),Arrays.asList(wrong,duplicate))==null,"ambiguous filename is not chosen");
        ok(M3uPlaylist.match(folder.entries.get(0),Collections.emptyList())==null,"missing file remains unmatched");
        ok(M3uPlaylist.parse("spotify:track:abc\ncontent://other/42\n").unsupported==2,"non-file schemes are not read");
        ok(M3uPlaylist.decode("노래.mp3".getBytes(java.nio.charset.Charset.forName("MS949"))).equals("노래.mp3"),"Korean Windows playlist encoding");
        StringBuilder many=new StringBuilder();for(int i=0;i<=M3uPlaylist.MAX_ENTRIES;i++)many.append("song.mp3\n");
        try{M3uPlaylist.parse(many.toString());throw new AssertionError("entry limit");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("M3uPlaylistTests: "+checks+" checks passed");
    }
}
