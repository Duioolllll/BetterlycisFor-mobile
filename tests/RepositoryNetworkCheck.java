import io.betterlyrics.mobile.*;
import java.nio.file.Files;
import java.util.List;

/** Optional live smoke: executes the production HTTP/scoring/parsing/cache code, not Android session APIs. */
public final class RepositoryNetworkCheck {
    public static void main(String[] args) throws Exception {
        try {
            LyricsRepository repo=new LyricsRepository(Files.createTempDirectory("bl-network-").toFile());
            LyricsRepository.Song song=new LyricsRepository.Song();song.title="Get Lucky";song.artist="Daft Punk";song.album="Random Access Memories";song.duration=369000;song.key="network-smoke";
            for(String provider:new String[]{"lrclib","netease"}) {
                List<LyricsRepository.Result> candidates=repo.search(song,provider);
                if(candidates.isEmpty())throw new AssertionError(provider+" returned no candidates");
                LyricsRepository.Result found=null;
                for(LyricsRepository.Result candidate:candidates)try{found=repo.hydrate(candidate);break;}catch(Exception ignored){}
                if(found==null||found.lines.isEmpty())throw new AssertionError(provider+" returned no parseable lyrics");
                repo.save(song,found);LyricsRepository.Result cached=repo.fetch(song,provider,"sequential",false);
                if(!cached.cached||cached.lines.size()!=found.lines.size())throw new AssertionError(provider+" cache mismatch");
                long timed=found.lines.stream().filter(l->l.realTiming).count();
                System.out.println(provider+": "+candidates.size()+" candidates, "+found.lines.size()+" parsed rows, "+timed+" real word-timed rows; cache verified");
            }
            repo.clear();
            System.out.flush();System.exit(0);
        }catch(Throwable t){t.printStackTrace();System.exit(1);}
    }
}
