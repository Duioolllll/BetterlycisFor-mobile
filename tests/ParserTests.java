import io.betterlyrics.mobile.LyricParser;
import io.betterlyrics.mobile.LyricParser.Line;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

/** JVM regression cases for the formats recovered from the original source and helper assembly. */
public final class ParserTests {
    static String repeat(String value,int count){StringBuilder out=new StringBuilder();for(int i=0;i<count;i++)out.append(value);return out.toString();}

    static int assertions=0;
    interface Checked { void run()throws Exception; }
    static void eq(Object expected,Object actual,String label){assertions++;if(!Objects.equals(expected,actual))throw new AssertionError(label+": expected "+expected+", got "+actual);}
    static void ok(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    static void rejects(Checked action,String label)throws Exception{assertions++;try{action.run();}catch(Exception expected){return;}throw new AssertionError(label+": accepted unsafe/invalid input");}
    static String tt(String head,String body){return "<tt xmlns='http://www.w3.org/ns/ttml' xmlns:ttm='http://www.w3.org/ns/ttml#metadata' xmlns:itunes='http://itunes.apple.com/lyric-ttml-extensions' xmlns:tts='http://www.w3.org/ns/ttml#styling'><head>"+head+"</head><body><div>"+body+"</div></body></tt>";}
    static byte[] krcContainer(String raw)throws Exception{
        byte[] key={64,71,97,119,94,50,116,71,81,54,49,45,(byte)206,(byte)210,110,105};
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        try(DeflaterOutputStream z=new DeflaterOutputStream(compressed)){z.write(raw.getBytes(StandardCharsets.UTF_8));}
        byte[] data=compressed.toByteArray(),result=new byte[data.length+4];result[0]='k';result[1]='r';result[2]='c';result[3]='1';
        for(int i=0;i<data.length;i++)result[i+4]=(byte)(data[i]^key[i%16]);return result;
    }
    public static void main(String[] args)throws Exception{
        List<Line> l=LyricParser.parse("[00:01.00]alpha\n[00:03.000]beta",6000);
        eq(2,l.size(),"LRC rows");eq(1000L,l.get(0).start,"LRC timestamp");eq(3000L,l.get(0).end,"LRC next-row end");
        ok(!l.get(0).realTiming,"line-only LRC remains Auto/line mode");ok(!l.get(0).words.isEmpty(),"estimated timing exists for forced word mode");
        eq(6000L,l.get(1).end,"last LRC uses duration");

        l=LyricParser.parse("[00:01.00]<00:01.00>alpha <00:02.00>beta<00:03.00>",5000);
        eq("alpha beta",l.get(0).text,"enhanced LRC text");ok(l.get(0).realTiming,"enhanced real flag");eq(2,l.get(0).words.size(),"enhanced word count");
        eq(2000L,l.get(0).words.get(0).end,"enhanced next-word boundary");eq(3000L,l.get(0).words.get(1).end,"enhanced terminal timestamp");
        l=LyricParser.parse("[00:01.00]alpha [00:02.00]beta",6000);
        ok(l.get(0).realTiming,"square-bracket word stamps accepted");eq("alpha beta",l.get(0).text,"square-bracket text");
        l=LyricParser.parse("[00:01.00][00:03.00]refrain",6000);
        eq(2,l.size(),"repeated LRC line tags");ok(!l.get(0).realTiming,"repeated line tags are not word timing");eq(3000L,l.get(1).start,"repeated LRC second time");
        l=LyricParser.parse("[offset:-250]\n[00:01.000]offset",5000);eq(750L,l.get(0).start,"LRC offset");
        l=LyricParser.parse("[00:01.000]original\n[00:01.000]번역\n[00:01.000]phonetic",5000);
        eq(1,l.size(),"LRC language rows grouped");eq("번역",l.get(0).translation,"intrinsic LRC translation");eq("phonetic",l.get(0).romanization,"third LRC language row");

        String body="<p begin='1s' end='4s' ttm:agent='lead'><span begin='1s' end='3s'>main</span><span ttm:role='x-bg' begin='1s' end='2s'><span begin='1s' end='2s'>back</span></span></p><p begin='1s' end='4s' ttm:agent='other'><span begin='1s' end='4s'>other</span></p>";
        l=LyricParser.parse(tt("",body),6000);
        eq(3,l.size(),"overlapping TTML original/bg/other voices retained");eq("main",l.get(0).text,"background excluded from main text");
        eq("back",l.get(1).text,"separate background text");ok(l.get(1).bg,"background marker");eq("lead",l.get(1).agent,"background inherits agent");eq("other",l.get(2).agent,"other voice preserved");
        ok(l.get(0).realTiming,"TTML timed spans real flag");

        String head="<metadata><translations><translation xml:lang='en'><text for='L1'>English</text></translation><translation xml:lang='ko'><text for='L1'>한국어<span ttm:role='x-bg' begin='1s' end='2s'>배경</span></text></translation></translations><transliterations><transliteration xml:lang='ja-Latn'><text for='L1'>romaji</text></transliteration></transliterations></metadata>";
        body="<p itunes:key='L1' begin='1s' end='3s'><span begin='1s' end='3s'>main</span><span ttm:role='x-bg' begin='1s' end='2s'><span begin='1s' end='2s'>back</span></span></p>";
        l=LyricParser.parse(tt(head,body),5000);
        eq("한국어",l.get(0).translation,"head translation keyed to paragraph");eq("romaji",l.get(0).romanization,"head transliteration keyed to paragraph");eq("배경",l.get(1).translation,"head background translation aligned separately");
        eq(2,l.get(0).translations.size(),"head language choices retained");LyricParser.applyLanguage(l,"en");eq("English",l.get(0).translation,"choose another embedded language");
        l=LyricParser.parse(tt("","<p begin='1s' end='3s'><span begin='1s' end='3s'>원문</span><span ttm:role='x-translation' xml:lang='ko'>번역</span><span ttm:role='x-roman'>roman</span></p>"),5000);
        eq("원문",l.get(0).text,"inline auxiliaries excluded from original");eq("번역",l.get(0).translation,"inline translation");eq("roman",l.get(0).romanization,"inline phonetic");

        l=LyricParser.parse(tt("","<p begin='1s' end='4s'><span tts:ruby='container'><span tts:ruby='base'>漢字</span><span tts:ruby='text' begin='1.5s' end='3s'>かんじ</span></span></p>"),5000);
        eq("漢字",l.get(0).text,"ruby base kept without phonetic duplication");eq(1500L,l.get(0).words.get(0).start,"ruby uses phonetic timing start");eq(3000L,l.get(0).words.get(0).end,"ruby timing end");
        l=LyricParser.parse(tt("","<p begin='1s' end='4s'>Oh, <span begin='2s' end='4s'>world</span>!</p>"),5000);
        StringBuilder joined=new StringBuilder();for(LyricParser.Word w:l.get(0).words)joined.append(w.text);
        eq(l.get(0).text,joined.toString(),"TTML text outside spans not lost by word renderer");
        l=LyricParser.parse(tt("","<p begin='1s'><span begin='1s'>first</span></p><p begin='5s' end='7s'>next</p>"),8000);
        eq(2000L,l.get(0).end,"missing last syllable end uses source one-second fallback");ok(!l.get(1).realTiming,"plain TTML paragraph remains line mode");
        eq(1234L,LyricParser.parseTime("00:01.234"),"TTML clock time");eq(1234L,LyricParser.parseTime("1234ms"),"TTML ms extension");eq(3600000L,LyricParser.parseTime("1h"),"TTML hours extension");

        l=LyricParser.parse("[1000,2000]alpha(1000,500) beta(1500,1000)",5000);
        eq("alpha beta",l.get(0).text,"decoded QRC text");eq(1000L,l.get(0).words.get(0).start,"QRC word start is absolute");eq(2500L,l.get(0).end,"QRC last syllable end");ok(l.get(0).realTiming,"QRC real flag");
        l=LyricParser.parse("<QrcInfos><LyricInfo><Lyric_1 LyricContent='[1000,2000]alpha(1000,500) beta(1500,1000)' /></LyricInfo></QrcInfos>",5000);
        eq("alpha beta",l.get(0).text,"decoded QRC XML LyricContent");
        String krc="[1000,2000]<100,500,0>alpha <700,800,0>beta";
        l=LyricParser.parse(krc,5000);eq(1100L,l.get(0).words.get(0).start,"KRC relative word start");eq(1700L,l.get(0).words.get(1).start,"KRC second relative word start");eq(2500L,l.get(0).words.get(1).end,"KRC duration");
        eq(krc,LyricParser.decodeBytes(krcContainer(krc)),"binary KRC without BOM keeps first bracket");eq(krc,LyricParser.decodeBytes(krcContainer("\uFEFF"+krc)),"binary KRC removes actual BOM");
        eq("alpha beta",LyricParser.parseBytes(krcContainer(krc),5000).get(0).text,"binary KRC parse entry point");
        String language=Base64.getEncoder().encodeToString("{\"content\":[{\"type\":1,\"lyricContent\":[[\"번역\"]]}]}".getBytes(StandardCharsets.UTF_8));
        l=LyricParser.parse("[language:"+language+"]\n"+krc,5000);eq("번역",l.get(0).translation,"KRC embedded language metadata");
        l=LyricParser.parse("[1000,2000](1000,500,0)alpha (1500,1000,0)beta",5000);
        eq(1000L,l.get(0).words.get(0).start,"YRC timestamps are absolute");eq(2500L,l.get(0).words.get(1).end,"YRC word end");ok(l.get(0).realTiming,"YRC real flag");
        l=LyricParser.parse("[1000,3000](1000,1000,0)너를 (다시) (2000,1000,0)불러",5000);
        eq("너를 (다시) 불러",l.get(0).text,"YRC literal parentheses in Korean backing vocals retained");
        eq(2,l.get(0).words.size(),"literal parentheses do not invent numeric word tags");
        eq(2000L,l.get(0).words.get(1).start,"Korean syllable timestamp survives parentheses");

        l=LyricParser.parse("[00:01.000]one\n[00:02.000]two",5000);
        LyricParser.applyTranslation(l,"[00:01.040]번역\n[00:02.051]out of tolerance","[00:00.950]roman");
        eq("번역",l.get(0).translation,"translation within 50ms");eq("roman",l.get(0).romanization,"phonetic at -50ms boundary");eq("",l.get(1).translation,"translation beyond 50ms ignored");
        l=LyricParser.parse("static first\nstatic second",0);eq(-1L,l.get(0).start,"plain text has no fake synchronization");ok(!l.get(0).realTiming,"plain text flag");
        byte[] utf16="[00:01.00]한글".getBytes(StandardCharsets.UTF_16LE),bom=new byte[utf16.length+2];bom[0]=(byte)255;bom[1]=(byte)254;System.arraycopy(utf16,0,bom,2,utf16.length);
        eq("한글",LyricParser.parse(LyricParser.decodeBytes(bom),3000).get(0).text,"UTF16 BOM import");

        rejects(()->LyricParser.parse("<!DOCTYPE tt [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><tt><body><p>&xxe;</p></body></tt>",1000),"XXE document rejected");
        rejects(()->LyricParser.parse("<tt><body><p>broken",1000),"malformed XML rejected");
        rejects(()->LyricParser.parse(repeat("ab",64),1000),"encrypted QRC hex explicitly unsupported");
        rejects(()->LyricParser.decodeBytes(repeat("ab",64).getBytes(StandardCharsets.UTF_8)),"encrypted QRC rejected at import");
        rejects(()->LyricParser.parseTime("NaN"),"non-finite timestamps rejected");
        rejects(()->LyricParser.parseTime("1e100s"),"overflow timestamp rejected");
        rejects(()->LyricParser.parse(repeat("x",2*1024*1024+1),0),"oversize raw input rejected");
        rejects(()->LyricParser.parse(repeat("[00:01.00]row\n",10001),1000),"too many timed rows rejected before merge");
        rejects(()->LyricParser.decodeBytes(krcContainer(repeat("x",2*1024*1024+1))),"KRC decompression bound");
        rejects(()->LyricParser.parse(tt("","<p begin='1s'>"+repeat("<span>",70)+"deep"+repeat("</span>",70)+"</p>"),3000),"deep XML bounded before recursive processing");
        eq(0,LyricParser.parse("<tt xmlns='http://www.w3.org/ns/ttml' timing='None'><body><p>untimed</p></body></tt>",0).size(),"Apple untimed TTML skipped as source");
        System.out.println("ParserTests: "+assertions+" assertions passed");
    }
}
