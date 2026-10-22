package io.betterlyrics.mobile;

import java.util.*;
import java.util.regex.Pattern;

/** Display policy only: preserve source text/timestamps, never invent missing swear words. */
public final class LyricTextPolicy {
    private static final Pattern MASK=Pattern.compile("[\\p{L}\\p{N}]+[*＊]+[\\p{L}\\p{N}]+|[*＊]{2,}");
    private static final Pattern CREDIT=Pattern.compile("^[\\s\\[【(（-]*(?:作\\s*[词詞曲]|词曲|詞曲|编曲|編曲|制作|製作|中文(?:翻译|翻譯)?|翻译|翻譯|译者|譯者|歌词(?:贡献者)?|歌詞(?:貢獻者)?|录音|錄音|混音|母带|母帶|监制|監製|出品|发行|發行|策划|策劃|和声|和聲|编写|編寫|贡献|貢獻|纯音乐|純音樂)(?:\\s*[:：/／=\\]-]|\\s|$).*");
    private static final Pattern CLEAN_EDITION=Pattern.compile("(?iu)(?:[\\[(（]|\\s[-–—]\\s)\\s*(?:clean|censored|edited)(?:\\s+(?:version|ver\\.?|edit))?(?:\\s*[\\])）]|$)");
    private LyricTextPolicy(){}
    private static boolean script(String text,Character.UnicodeScript script){
        if(text==null)return false;
        for(int at=0;at<text.length();){int cp=text.codePointAt(at);if(Character.UnicodeScript.of(cp)==script)return true;at+=Character.charCount(cp);}return false;
    }
    public static boolean chineseText(String text){
        return script(text,Character.UnicodeScript.HAN)&&!script(text,Character.UnicodeScript.HANGUL)&&!script(text,Character.UnicodeScript.HIRAGANA)&&!script(text,Character.UnicodeScript.KATAKANA);
    }
    public static boolean chineseLanguage(String language){String s=language==null?"":language.toLowerCase(Locale.ROOT).replace('_','-');return s.equals("zh")||s.startsWith("zh-")||s.equals("cmn")||s.startsWith("cmn-")||s.equals("yue")||s.startsWith("yue-");}
    public static String auxiliary(String text){return text==null||chineseText(text)?"":text;}
    public static boolean chineseCredit(String text){return text!=null&&CREDIT.matcher(text.trim()).matches();}
    public static boolean cleanEdition(String title){return title!=null&&CLEAN_EDITION.matcher(title).find();}
    public static int maskCount(String text){if(text==null)return 0;java.util.regex.Matcher m=MASK.matcher(text);int count=0;while(m.find())count++;return count;}
    public static int maskCount(List<LyricParser.Line> lines){int count=0;for(LyricParser.Line line:lines)count+=maskCount(line.text);return count;}
    private static String select(Map<String,String> values,String fallback){
        for(String target:new String[]{"ko","en","ja"})for(Map.Entry<String,String> entry:values.entrySet()){
            String language=entry.getKey().toLowerCase(Locale.ROOT).replace('_','-');
            if((language.equals(target)||language.startsWith(target+"-"))&&(target.equals("ja")||!chineseText(entry.getValue())))return entry.getValue();
        }
        String clean=auxiliary(fallback);if(!clean.isEmpty()){
            boolean explicitlyChinese=false;for(Map.Entry<String,String> entry:values.entrySet())if(entry.getValue().equals(clean)&&chineseLanguage(entry.getKey()))explicitlyChinese=true;
            if(!explicitlyChinese)return clean;
        }
        for(Map.Entry<String,String> entry:values.entrySet())if(!chineseLanguage(entry.getKey())&&!chineseText(entry.getValue()))return entry.getValue();
        return "";
    }
    public static void apply(List<LyricParser.Line> lines){
        for(Iterator<LyricParser.Line> it=lines.iterator();it.hasNext();){
            LyricParser.Line line=it.next();
            // Duplicate-timestamp multilingual LRC can put the Chinese translation first.
            if((chineseText(line.text)||chineseCredit(line.text))&&!line.translation.isEmpty()&&!chineseText(line.translation)&&!chineseCredit(line.translation)){
                line.text=line.translation;line.translation="";line.words.clear();line.realTiming=false;
            }
            if(chineseCredit(line.text)){it.remove();continue;}
            line.translation=select(line.translations,line.translation);
            line.romanization=select(line.romanizations,line.romanization);
            if(line.translation.equals(line.text))line.translation="";
            if(line.romanization.equals(line.text))line.romanization="";
        }
    }
}
