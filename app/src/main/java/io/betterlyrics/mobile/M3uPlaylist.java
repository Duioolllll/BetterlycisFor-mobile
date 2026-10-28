package io.betterlyrics.mobile;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/** Parses local M3U files and matches their paths against a user-selected document tree. */
public final class M3uPlaylist {
    public static final int MAX_ENTRIES = 5000;

    public static final class Entry {
        public final String path, title;
        public final long durationMs;
        Entry(String path, String title, long durationMs) {
            this.path=path; this.title=title; this.durationMs=durationMs;
        }
    }

    public static final class Document {
        public final String path, uri;
        public Document(String path, String uri) { this.path=path; this.uri=uri; }
    }

    public static final class Result {
        public final List<Entry> entries=new ArrayList<>();
        public int unsupported;
    }

    private M3uPlaylist() {}

    public static String decode(byte[] bytes) {
        if(bytes.length>=2&&bytes[0]==(byte)0xff&&bytes[1]==(byte)0xfe)return new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16LE);
        if(bytes.length>=2&&bytes[0]==(byte)0xfe&&bytes[1]==(byte)0xff)return new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16BE);
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(Exception ignored){
            try{return Charset.forName("MS949").newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
            catch(Exception error){throw new IllegalArgumentException("지원하지 않는 재생목록 인코딩입니다.");}
        }
    }

    public static Result parse(String input) {
        Result result=new Result();
        String title=""; long duration=-1;
        for(String original:input.split("\\r\\n|\\n|\\r")) {
            String line=original.trim();
            if(line.startsWith("\ufeff"))line=line.substring(1).trim();
            if(line.isEmpty())continue;
            if(line.regionMatches(true,0,"#EXTINF:",0,8)) {
                int comma=line.indexOf(',',8);
                title=comma<0?"":line.substring(comma+1).trim();
                try { double seconds=Double.parseDouble(line.substring(8,comma<0?line.length():comma).trim());
                    duration=Double.isFinite(seconds)&&seconds>=0&&seconds<=86400?(long)(seconds*1000):-1;
                } catch(NumberFormatException ignored) { duration=-1; }
                continue;
            }
            if(line.startsWith("#"))continue;
            if(result.entries.size()+result.unsupported>=MAX_ENTRIES)throw new IllegalArgumentException("재생목록은 최대 5,000곡까지 불러올 수 있습니다.");
            String path=localPath(line);
            if(path.isEmpty())result.unsupported++;
            else result.entries.add(new Entry(path,title,duration));
            title="";duration=-1;
        }
        return result;
    }

    private static String localPath(String value) {
        String text=value.trim();
        if(text.regionMatches(true,0,"file:",0,5)) {
            try { URI uri=new URI(text.replace(" ","%20")); text=uri.getRawPath(); }
            catch(Exception error) { return ""; }
            if(text==null)return "";
        } else if(text.matches("(?i)^[a-z][a-z0-9+.-]*:.*")&&!text.matches("(?i)^[a-z]:[\\\\/].*")) {
            return "";
        }
        return text.replace('\\','/');
    }

    private static List<String> parts(String value, boolean encoded) {
        String path=value.replace('\\','/');
        // Literal plus signs in a path are not URL-encoded spaces.
        if(encoded&&path.matches(".*%[0-9a-fA-F]{2}.*"))try { path=URLDecoder.decode(path.replace("+","%2B"),StandardCharsets.UTF_8.name()); } catch(Exception ignored) {}
        List<String> parts=new ArrayList<>();
        for(String segment:path.split("/")) {
            if(segment.isEmpty()||segment.equals(".")||segment.matches("(?i)^[a-z]:$"))continue;
            if(segment.equals("..")){if(!parts.isEmpty())parts.remove(parts.size()-1);continue;}
            parts.add(Normalizer.normalize(segment,Normalizer.Form.NFC).toLowerCase(Locale.ROOT));
        }
        return parts;
    }

    /** Indexes suffixes once, so large playlists don't scan the tree for every song. */
    public static final class Matcher {
        private final Map<String,Document> suffixes=new HashMap<>();
        public Matcher(List<Document> documents) {
            for(Document doc:documents) {
                List<String> names=parts(doc.path,false);String suffix="";
                for(int i=names.size()-1;i>=0;i--) {
                    suffix=names.get(i)+(suffix.isEmpty()?"":"/"+suffix);
                    if(!suffixes.containsKey(suffix))suffixes.put(suffix,doc);
                    else {Document prior=suffixes.get(suffix);if(prior!=null&&!prior.uri.equals(doc.uri))suffixes.put(suffix,null);}
                }
            }
        }
        /** Returns the unique best suffix match; ambiguous names are left unmatched. */
        public Document match(Entry entry) {
            List<String> names=parts(entry.path,true);String suffix="";Document best=null;
            for(int i=names.size()-1;i>=0;i--) {
                suffix=names.get(i)+(suffix.isEmpty()?"":"/"+suffix);
                if(suffixes.containsKey(suffix))best=suffixes.get(suffix);
            }
            return best;
        }
    }

    public static Document match(Entry entry, List<Document> documents) {return new Matcher(documents).match(entry);}
}
