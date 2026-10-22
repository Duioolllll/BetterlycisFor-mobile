package io.betterlyrics.mobile;

import java.util.Locale;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Original weighting with mandatory identity/version checks for automatic selection. */
public final class MatchScore {
    private static final Pattern BRACKETS=Pattern.compile("[（(\\[]([^）)\\]]*)[）)\\]]");
    private static final Pattern VERSION=Pattern.compile("(?iu)(?:\\b(live|remix|instrumental|inst|mr|acoustic|cover|karaoke|acappella|acapella|japanese|jpn|jp|english|eng|chinese|korean|kor|sped|slowed|radio edit|tv size|short ver(?:sion)?)\\b|라이브|리믹스|반주|일본어|영어\\s*버전|한국어\\s*버전|日语|日文|韩语|伴奏)");
    public static String normalized(String value) {
        return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");
    }
    public static boolean hasHangul(String value) { return value!=null&&Pattern.compile("[\\uac00-\\ud7af\\u1100-\\u11ff]").matcher(value).find(); }
    public static List<String> variants(String value,boolean artist) {
        String raw=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).replaceAll("(?iu)\\s*-\\s*Topic$","");
        // Featured guests help a search; they must not change the main song identity.
        raw=raw.replaceAll("(?iu)[(\\[]\\s*(?:feat\\.?|ft\\.?|featuring)\\s+[^)\\]]*[)\\]]","");
        LinkedHashSet<String> out=new LinkedHashSet<>();String base=BRACKETS.matcher(raw).replaceAll("").trim();
        add(out,raw);add(out,base);
        Matcher m=BRACKETS.matcher(raw);
        while(m.find())if(!VERSION.matcher(m.group(1)).find()&&hasHangul(base)!=hasHangul(m.group(1)))add(out,m.group(1));
        if(artist)for(String part:base.split("(?iu)\\s*[,;/&]\\s*|\\s+(?:feat\\.?|ft\\.?|featuring|with|x)\\s+"))add(out,part);
        return new ArrayList<>(out);
    }
    private static void add(Set<String> set,String value){String s=normalized(value);if(!s.isEmpty())set.add(s);}
    public static String searchTitle(String value){
        String s=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC);
        return BRACKETS.matcher(s).replaceAll("").trim();
    }
    public static String searchArtist(String value){return searchTitle(value).replaceFirst("(?iu)\\s*-\\s*Topic$","").split("\\s*[,;/&]\\s*",2)[0].trim();}
    public static double textSimilarity(String a,String b,boolean artist){
        double best=0;for(String x:variants(a,artist))for(String y:variants(b,artist)){
            if(x.equals(y))return 1;
            // A shared syllable in a short title is not evidence of the same recording.
            if(!artist&&Math.min(x.codePointCount(0,x.length()),y.codePointCount(0,y.length()))<=5)continue;
            if(artist&&Math.min(x.codePointCount(0,x.length()),y.codePointCount(0,y.length()))<=3)continue;
            best=Math.max(best,similarity(x,y));
        }return best;
    }
    private static Set<String> versions(String value){
        Set<String> out=new HashSet<>();Matcher brackets=BRACKETS.matcher(value==null?"":value);
        while(brackets.find())versionTags(out,brackets.group(1));
        String[] suffix=(value==null?"":value).split("\\s[-–—]\\s",2);if(suffix.length>1)versionTags(out,suffix[1]);
        return out;
    }
    private static void versionTags(Set<String> out,String text){Matcher m=VERSION.matcher(text);while(m.find()){
        String s=m.group().toLowerCase(Locale.ROOT);
        if(s.equals("라이브"))s="live";else if(s.equals("리믹스"))s="remix";
        else if(s.equals("반주")||s.equals("伴奏")||s.equals("karaoke")||s.equals("inst")||s.equals("mr"))s="instrumental";
        else if(s.equals("일본어")||s.equals("日语")||s.equals("日文")||s.equals("jpn")||s.equals("jp"))s="japanese";
        else if(s.startsWith("영어")||s.equals("eng"))s="english";else if(s.startsWith("한국어")||s.equals("韩语")||s.equals("kor"))s="korean";
        out.add(s);
    }}
    public static boolean sameVersion(String a,String b){return versions(a).equals(versions(b));}
    public static boolean automatic(String title,String artist,long duration,String rt,String ra,long rd){
        if(textSimilarity(title,rt,false)<.86)return false;
        if(!normalized(artist).isEmpty()&&(normalized(ra).isEmpty()||textSimilarity(artist,ra,true)<.72))return false;
        if(!sameVersion(title,rt))return false;
        return duration<=0||rd<=0||Math.abs(duration-rd)<=Math.max(2500,Math.min(5000,Math.round(duration*.02)));
    }
    public static int score(String title,String artist,String album,long duration,String rt,String ra,String ral,long rd) {
        double ds=rd<=0||duration<=0?0:Math.max(0,Math.min(1,1-(Math.abs(duration-rd)/1000.0-1)/9));
        double total=.30*textSimilarity(title,rt,false),weight=.30;
        if(!normalized(artist).isEmpty()){total+=.30*textSimilarity(artist,ra,true);weight+=.30;}
        if(!normalized(album).isEmpty()){total+=.10*textSimilarity(album,ral,true);weight+=.10;}
        if(duration>0&&rd>0){total+=.30*ds;weight+=.30;}
        return (int)Math.round(100*total/weight);
    }
    public static double similarity(String a,String b) {
        a=a==null?"":a.trim().toLowerCase(Locale.ROOT);b=b==null?"":b.trim().toLowerCase(Locale.ROOT);
        if(a.equals(b))return 1;if(a.isEmpty()||b.isEmpty())return 0;
        int window=Math.max(0,Math.max(a.length(),b.length())/2-1),matches=0;boolean[] am=new boolean[a.length()],bm=new boolean[b.length()];
        for(int i=0;i<a.length();i++)for(int j=Math.max(0,i-window);j<Math.min(b.length(),i+window+1);j++)
            if(!bm[j]&&a.charAt(i)==b.charAt(j)){am[i]=true;bm[j]=true;matches++;break;}
        if(matches==0)return 0;
        int k=0,trans=0;for(int i=0;i<a.length();i++)if(am[i]){while(!bm[k])k++;if(a.charAt(i)!=b.charAt(k))trans++;k++;}
        double m=matches,jaro=(m/a.length()+m/b.length()+(m-trans/2.0)/m)/3;
        int prefix=0;while(prefix<Math.min(4,Math.min(a.length(),b.length()))&&a.charAt(prefix)==b.charAt(prefix))prefix++;
        return jaro>.7?jaro+prefix*.1*(1-jaro):jaro;
    }
    public static long position(long base,long updated,long now,float speed,boolean playing,long duration) {
        if(base<0)return 0;
        long p=Math.max(0,base);
        if(playing&&updated>0&&now>=updated)p+=Math.round((now-updated)*(double)speed);
        return duration>0?Math.max(0,Math.min(duration,p)):Math.max(0,p);
    }
}
