import io.betterlyrics.mobile.*;
import java.nio.file.*;
import java.util.*;
public class KoreanProviderCheck {
 public static void main(String[] args)throws Exception{
  try{
   LyricsRepository repo=new LyricsRepository(Files.createTempDirectory("bl-korean-probe-").toFile());
   String[][] songs={{"밤편지","아이유","Palette","253000"},{"봄날 (Spring Day)","방탄소년단 (BTS)","YOU NEVER WALK ALONE","274000"},{"Ditto","NewJeans","OMG","185000"}};
   for(String[] row:songs){
    LyricsRepository.Song song=new LyricsRepository.Song();song.title=row[0];song.artist=row[1];song.album=row[2];song.duration=Long.parseLong(row[3]);song.key=row[0];
    System.out.println("SONG "+song.title+" / "+song.artist);
    for(String source:new String[]{"lrclib","netease"}){
     try{
      List<LyricsRepository.Result> list=repo.search(song,source);int eligible=0;for(LyricsRepository.Result r:list)if(LyricsRepository.eligible(song,r))eligible++;
      System.out.println(source+": candidates="+list.size()+", eligible="+eligible);
      for(int i=0;i<Math.min(2,list.size());i++){LyricsRepository.Result r=list.get(i);System.out.println("  "+r.title+" / "+r.artist+" / "+r.duration+"ms / score="+r.score+" / aliases="+r.titleAliases+" ; "+r.artistAliases);}
     }catch(Exception failure){System.out.println(source+": "+failure.getClass().getSimpleName()+": "+failure.getMessage());}
    }
    try{LyricsRepository.Result result=repo.fetch(song,"auto","best",true);System.out.println("SELECTED "+result.provider+" / "+result.title+" / rows="+result.lines.size()+" / synced="+LyricsRepository.synced(result)+" / Hangul="+MatchScore.hasHangul(result.raw));}
    catch(Exception failure){System.out.println("NO AUTO RESULT: "+failure.getMessage());}
   }
  }finally{System.exit(0);}
 }
}
