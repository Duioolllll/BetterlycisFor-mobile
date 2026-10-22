import io.betterlyrics.mobile.*;
import java.util.*;

public final class TextPolicyTests {
    static int checks;
    static void ok(boolean v,String label){checks++;if(!v)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        List<LyricParser.Line> lines=LyricParser.parse("[00:00.00]作词 : Test Writer\n[00:01.00]作曲：Test Composer\n[00:10.00]A fictional line\n[00:20.00]한국어 테스트",30000);
        LyricParser.applyTranslation(lines,"[00:10.00]这是翻译\n[00:20.00]翻译文本","");LyricTextPolicy.apply(lines);
        ok(lines.size()==2,"Chinese credit rows removed");
        ok(lines.get(0).start==10000&&lines.get(0).end==20000,"original timestamps retained after removing credits");
        ok(lines.get(0).translation.isEmpty()&&lines.get(1).translation.isEmpty(),"unlabeled Chinese translations hidden");
        ok(lines.get(0).text.equals("A fictional line")&&lines.get(1).text.equals("한국어 테스트"),"English and Korean original text retained");
        lines=LyricParser.parse("[00:10.00]中文翻译\n[00:10.00]English original",20000);LyricTextPolicy.apply(lines);
        ok(lines.get(0).text.equals("English original")&&!lines.get(0).realTiming,"Chinese-first duplicate timestamp promotes real original without invented word times");
        lines=LyricParser.parse("[00:00.00]作词：Writer\n[00:00.00]Actual opening line",20000);LyricTextPolicy.apply(lines);
        ok(lines.size()==1&&lines.get(0).text.equals("Actual opening line"),"credit and original sharing timestamp do not lose opening lyric");
        LyricParser.Line row=new LyricParser.Line(1000,3000,"original");row.translations.put("zh-Hans","中文");row.translations.put("en","English translation");row.translation="中文";
        lines=new ArrayList<>(Arrays.asList(row));LyricTextPolicy.apply(lines);ok(row.translation.equals("English translation"),"English fallback beats Chinese");
        row.translations.put("ko","한국어 번역");LyricTextPolicy.apply(lines);ok(row.translation.equals("한국어 번역"),"Korean translation retained as first choice");
        row.translations.clear();row.translations.put("zh-Hant","Latin-only Chinese track");row.translation="Latin-only Chinese track";LyricTextPolicy.apply(lines);ok(row.translation.isEmpty(),"explicit Chinese language label overrides Latin content");
        row.translations.clear();row.translations.put("ja","愛");row.translation="愛";LyricTextPolicy.apply(lines);ok(row.translation.equals("愛"),"explicit Japanese label preserves Kanji-only Japanese text");
        ok(!LyricTextPolicy.chineseText("君の名前")&&!LyricTextPolicy.chineseText("愛を歌う"),"Japanese kana distinguishes Japanese from Chinese");
        ok(!LyricTextPolicy.chineseText("사랑(愛)")&&!LyricTextPolicy.chineseCredit("작곡: 테스트"),"Korean Hanja and original credits not mistaken for Chinese");
        ok(LyricTextPolicy.chineseCredit("歌词贡献者：Example"),"Chinese contributor metadata detected");
        ok(LyricTextPolicy.maskCount("f**k and s*** and ＊＊＊")==3,"provider censor patterns detected");
        ok(LyricTextPolicy.maskCount("fuck shit 씨발")==0,"uncensored source words are never masked");
        ok(LyricTextPolicy.maskCount("a single * stage marker")==0,"single decoration is not a censored word");
        ok(LyricTextPolicy.cleanEdition("Test Song (Clean)")&&!LyricTextPolicy.cleanEdition("Clean"),"edition labels distinguished from actual song titles");
        lines=LyricParser.parse("[00:01.00]fuck this test\n[00:02.00]씨발 테스트",3000);LyricTextPolicy.apply(lines);
        ok(lines.get(0).text.equals("fuck this test")&&lines.get(1).text.equals("씨발 테스트"),"original profanity is preserved verbatim");
        lines=LyricParser.parse("[00:01.00]f**k source",3000);LyricTextPolicy.apply(lines);ok(lines.get(0).text.equals("f**k source"),"irrecoverable source censor text is not guessed or silently deleted");
        System.out.println("TextPolicyTests: "+checks+" checks passed");
    }
}
