import io.betterlyrics.mobile.MatchScore;

public final class MathTests {
    static int checks;
    static void equal(long expected,long actual,String label){checks++;if(expected!=actual)throw new AssertionError(label+": expected "+expected+", got "+actual);}
    static void close(double expected,double actual,String label){checks++;if(Math.abs(expected-actual)>0.00001)throw new AssertionError(label+": "+actual);}
    static void ok(boolean actual,String label){checks++;if(!actual)throw new AssertionError(label);}
    public static void main(String[] args){
        equal(100,MatchScore.score("Song","Artist","Album",180000,"Song","Artist","Album",180000),"identical metadata");
        equal(70,MatchScore.score("Song","Artist","Album",180000,"Song","Artist","Album",190000),"10s different version loses duration weight");
        equal(100,MatchScore.score(" Song ","ARTIST","album",180000,"song","artist","Album",181000),"case trim and one second tolerance");
        close(.9611111111,MatchScore.similarity("MARTHA","MARHTA"),"Jaro-Winkler transposition");
        close(.84,MatchScore.similarity("DWAYNE","DUANE"),"Jaro-Winkler prefix");
        close(0,MatchScore.similarity("a","z"),"different one-character strings");
        close(1,MatchScore.similarity("",""),"missing both metadata fields");
        equal(12500,MatchScore.position(10000,500,2500,1.25f,true,180000),"playback speed extrapolation");
        equal(10000,MatchScore.position(10000,500,2500,1.25f,false,180000),"pause freezes clock");
        equal(3000,MatchScore.position(3000,9000,1000,1,true,180000),"invalid future timestamp");
        equal(180000,MatchScore.position(179500,500,2500,1,true,180000),"duration clamp");
        equal(0,MatchScore.position(-1,500,2500,1,true,180000),"unknown position stays unknown");
        equal(0,MatchScore.position(500,500,2500,-1,true,180000),"rewinding clamp");
        equal(1800,MatchScore.position(800,1000,2000,1,true,0),"unknown duration does not freeze position");
        ok(!MatchScore.automatic("밤편지","아이유",253000,"좋은 날","아이유",253000),"same artist and duration cannot compensate for wrong Korean title");
        ok(!MatchScore.automatic("Song","Artist",180000,"Song (Live)","Artist",180000),"same-length live version rejected");
        ok(!MatchScore.automatic("Song","Artist",180000,"Song","Artist",190000),"different-length recording rejected");
        ok(!MatchScore.automatic("Song","Artist",180000,"Song","Other",180000),"same title by another singer rejected");
        ok(MatchScore.automatic("봄날 (Spring Day)","방탄소년단 (BTS)",274000,"Spring Day","BTS",274500),"bilingual Korean metadata matches English alias");
        ok(MatchScore.automatic("봄날 (Spring Day)","방탄소년단 (BTS)",274000,"봄날","방탄소년단",274000),"bilingual metadata matches original title");
        ok(MatchScore.automatic("봄날","BTS",274000,"봄날","BTS",274000),"Unicode-decomposed Hangul matches composed spelling");
        ok(MatchScore.automatic("Love wins all","IU",271000,"Love Wins All","IU",271000),"case and spacing normalisation");
        ok(MatchScore.automatic("에잇 (Feat. SUGA)","아이유",167000,"에잇","아이유",167000),"featured guest does not change main title");
        ok(!MatchScore.automatic("좋은 날","아이유",240000,"좋은 말","아이유",240000),"similar short Korean titles not conflated");
        ok(!MatchScore.automatic("Song","Artist",180000,"Song (Japanese Ver.)","Artist",180000),"language edition rejected");
        ok(!MatchScore.automatic("시험곡","박효신",180000,"시험곡","박효준",180000),"similar Korean singer names are not aliases");
        ok(!MatchScore.automatic("시험곡","가수",180000,"시험곡 (Inst.)","가수",180000),"Korean instrumental release rejected");
        equal(100,MatchScore.score("밤편지","아이유","",0,"밤편지","아이유","Palette",253000),"missing album/duration does not lower a true match");
        System.out.println("MathTests: "+checks+" checks passed");
    }
}
