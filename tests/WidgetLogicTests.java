import io.betterlyrics.mobile.LyricParser;
import io.betterlyrics.mobile.WidgetLyrics;
import java.util.Arrays;
import java.util.Collections;

public class WidgetLogicTests {
    private static int checks;
    private static void ok(boolean condition,String name){checks++;if(!condition)throw new AssertionError(name);}
    public static void main(String[] args){
        LyricParser.Line a=new LyricParser.Line(1000,3000,"첫 줄"), b=new LyricParser.Line(5000,8000,"둘째 줄");
        LyricParser.Line background=new LyricParser.Line(1200,2500,"배경 보컬");background.bg=true;
        WidgetLyrics.Selection first=WidgetLyrics.select(Arrays.asList(b,background,a),1500,true);
        ok(first.text.equals("첫 줄"),"foreground line selected with unsorted input");
        ok(first.nextChange==3000,"line end is next boundary");
        WidgetLyrics.Selection gap=WidgetLyrics.select(Arrays.asList(a,b),3500,true);
        ok(gap.text.equals("♪")&&gap.nextChange==5000,"instrumental gap and next line");
        ok(WidgetLyrics.select(Arrays.asList(a,b),5000,true).text.equals("둘째 줄"),"exact line boundary");
        ok(WidgetLyrics.select(Arrays.asList(a,b),999,true).nextChange==1000,"first entrance");
        ok(WidgetLyrics.select(Arrays.asList(a,b),9000,true).nextChange==-1,"no timer after final boundary");
        ok(WidgetLyrics.select(Arrays.asList(a,b),1500,false).text.isEmpty(),"unknown position hides timed lyrics");
        ok(WidgetLyrics.select(Arrays.asList(new LyricParser.Line(-1,-1,"정적인 가사")),0,false).text.equals("정적인 가사"),"plain lyrics remain visible");
        ok(WidgetLyrics.select(Collections.emptyList(),0,true).text.isEmpty(),"missing lyrics are empty");
        ok(WidgetLyrics.delay(1500,3000,1f)==1530,"normal playback boundary");
        ok(WidgetLyrics.delay(1500,3000,2f)==780,"speed changes timing");
        ok(WidgetLyrics.delay(1500,3000,0f)==-1,"paused speed schedules nothing");
        ok(WidgetLyrics.delay(0,100000,1f)==30000,"long wait periodically rechecks");
        ok(WidgetLyrics.delay(3000,3000,1f)==150,"near boundary avoids a tight loop");
        System.out.println("WidgetLogicTests: "+checks+" checks passed");
    }
}
