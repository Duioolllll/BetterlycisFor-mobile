package io.betterlyrics.mobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.InflaterInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Portable lyric model, based on the inspected v1.3.527.0 parser contracts. */
public final class LyricParser {
    private static final int MAX_SIZE=2*1024*1024, MAX_LINES=10000, MAX_WORDS=100000;
    private static final long MAX_TIME=30L*24*60*60*1000;
    private static final String META="http://www.w3.org/ns/ttml#metadata";
    private static final String XML="http://www.w3.org/XML/1998/namespace";
    private static final String STYLE="http://www.w3.org/ns/ttml#styling";
    private static final Pattern TOKEN=Pattern.compile("([\\[<])(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?([\\]>])");
    private static final Pattern OFFSET=Pattern.compile("(?im)^\\[offset:([+-]?\\d+)\\]");
    private static final Pattern TIMED_LINE=Pattern.compile("^\\[(\\d+),(\\d+)\\](.*)$");
    private static final Pattern QRC_WORD=Pattern.compile("(.*?)\\((\\d+),(\\d+)\\)");
    private static final Pattern KRC_WORD=Pattern.compile("<(\\d+),(\\d+),\\d+>([^<]*)");
    private static final Pattern YRC_WORD=Pattern.compile("\\((\\d+),(\\d+),\\d+\\)(.*?)(?=\\(\\d+,\\d+,\\d+\\)|$)");
    private static final Pattern UNSAFE_XML=Pattern.compile("<!\\s*(?:DOCTYPE|ENTITY)",Pattern.CASE_INSENSITIVE);
    private static final byte[] KRC_KEY={64,71,97,119,94,50,116,71,81,54,49,45,(byte)206,(byte)210,110,105};

    public static final class Word {
        public long start,end; public String text;
        public Word(long s,long e,String t){start=s;end=e;text=t;}
    }
    public static final class Line {
        public long start,end; public String text,translation="",romanization="",agent="";
        public boolean bg=false,realTiming=false;
        public List<Word> words=new ArrayList<>();
        private int coveredChars=0;
        /** All embedded language tracks are retained; convenience strings prefer Korean, then default. */
        public Map<String,String> translations=new LinkedHashMap<>(),romanizations=new LinkedHashMap<>();
        public Line(long s,long e,String t){start=s;end=e;text=t;}
    }
    private static final class Mark {
        long time; int start,end; boolean angle;
        Mark(Matcher m,long offset){start=m.start();end=m.end();angle=m.group(1).equals("<");
            String f=m.group(4);time=Long.parseLong(m.group(2))*60000+Long.parseLong(m.group(3))*1000+(f==null?0:Long.parseLong((f+"000").substring(0,3)))+offset;}
    }
    private static final class HeadTrack {
        String key,lang; boolean roman; Element text;
        HeadTrack(String k,String l,boolean r,Element t){key=k;lang=l;roman=r;text=t;}
    }

    public static List<Line> parseBytes(byte[] bytes,long duration)throws Exception {
        return parse(decodeBytes(bytes),duration);
    }
    public static String decodeBytes(byte[] bytes)throws Exception {
        if(bytes==null||bytes.length>MAX_SIZE)throw new IllegalArgumentException("가사 파일은 2MB 이하로 선택해 주세요.");
        if(bytes.length>=4&&bytes[0]=='k'&&bytes[1]=='r'&&bytes[2]=='c'&&bytes[3]=='1')return decodeKrc(bytes);
        String text;
        if(bytes.length>=2&&bytes[0]==(byte)0xFF&&bytes[1]==(byte)0xFE)text=new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16LE);
        else if(bytes.length>=2&&bytes[0]==(byte)0xFE&&bytes[1]==(byte)0xFF)text=new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16BE);
        else try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}
        catch(java.nio.charset.CharacterCodingException ex){throw new IllegalArgumentException("이 파일의 인코딩 또는 암호화 형식은 지원하지 않습니다. UTF-8 가사 파일을 선택해 주세요.");}
        if(text.trim().matches("(?s)[0-9a-fA-F]{64,}"))throw new IllegalArgumentException("암호화된 QRC는 아직 지원하지 않습니다. 복호화된 가사 파일을 선택해 주세요.");
        return text;
    }
    /** Decodes a local standard krc1 container; no network or proprietary runtime is involved. */
    public static String decodeKrc(byte[] bytes)throws IOException {
        if(bytes==null||bytes.length<5||bytes.length>MAX_SIZE||bytes[0]!='k'||bytes[1]!='r'||bytes[2]!='c'||bytes[3]!='1')
            throw new IllegalArgumentException("올바른 KRC 파일이 아닙니다.");
        byte[] data=Arrays.copyOfRange(bytes,4,bytes.length);
        for(int i=0;i<data.length;i++)data[i]^=KRC_KEY[i%KRC_KEY.length];
        try(InflaterInputStream in=new InflaterInputStream(new ByteArrayInputStream(data));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[4096];int n;
            while((n=in.read(buf))!=-1){if(out.size()+n>MAX_SIZE)throw new IOException("압축 해제한 가사가 너무 큽니다.");out.write(buf,0,n);}
            String text=new String(out.toByteArray(),StandardCharsets.UTF_8);
            return text.startsWith("\uFEFF")?text.substring(1):text;
        }
    }
    public static List<Line> parse(String raw,long duration)throws Exception {
        if(raw==null||raw.length()>MAX_SIZE)throw new IllegalArgumentException("가사 파일은 2MB 이하로 선택해 주세요.");
        raw=raw.startsWith("\uFEFF")?raw.substring(1):raw;
        String trimmed=raw.trim();if(trimmed.isEmpty())return new ArrayList<>();
        if(trimmed.matches("(?s)[0-9a-fA-F]{64,}"))throw new IllegalArgumentException("암호화된 QRC는 아직 지원하지 않습니다. 복호화된 QRC·LRC·TTML 파일을 선택해 주세요.");
        if(trimmed.startsWith("<")){
            Document doc=safeXml(raw);String name=local(doc.getDocumentElement());
            if(name.equals("tt"))return parseTtml(doc,duration);
            NodeList all=doc.getElementsByTagName("*");
            for(int i=0;i<all.getLength();i++){
                Element e=(Element)all.item(i);
                if(e.hasAttribute("LyricContent"))return parse(e.getAttribute("LyricContent"),duration);
            }
            throw new IllegalArgumentException("복호화된 LyricContent가 없는 QRC/XML 파일입니다.");
        }
        for(String row:raw.split("\\r?\\n")){
            Matcher m=TIMED_LINE.matcher(row.trim());if(!m.matches())continue;
            String body=m.group(3);
            if(body.matches("(?s).*<\\d+,\\d+,\\d+>.*"))return parseNumeric(raw,duration,'k');
            if(body.matches("(?s).*\\(\\d+,\\d+,\\d+\\).*"))return parseNumeric(raw,duration,'y');
            if(body.matches("(?s).*\\(\\d+,\\d+\\).*"))return parseNumeric(raw,duration,'q');
        }
        return parseLrc(raw,duration);
    }
    private static Document safeXml(String xml)throws Exception {
        if(UNSAFE_XML.matcher(xml).find())throw new IllegalArgumentException("외부 엔터티/DOCTYPE가 포함된 XML은 열 수 없습니다.");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setExpandEntityReferences(false);
        // Android's XML implementation does not support every desktop feature flag. The explicit
        // DOCTYPE/entity rejection above remains mandatory even when one feature is unavailable.
        for(String feature:new String[]{"http://apache.org/xml/features/disallow-doctype-decl","http://xml.org/sax/features/external-general-entities","http://xml.org/sax/features/external-parameter-entities"})
            try{f.setFeature(feature,feature.endsWith("disallow-doctype-decl"));}catch(Exception ignored){}
        javax.xml.parsers.DocumentBuilder builder=f.newDocumentBuilder();
        builder.setEntityResolver((publicId,systemId)->{throw new SAXException("External entities are disabled");});
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler(){
            @Override public void error(org.xml.sax.SAXParseException e)throws SAXException{throw e;}
            @Override public void fatalError(org.xml.sax.SAXParseException e)throws SAXException{throw e;}
        });
        Document doc=builder.parse(new InputSource(new StringReader(xml)));
        Deque<Node> nodes=new ArrayDeque<>();Deque<Integer> depths=new ArrayDeque<>();nodes.push(doc.getDocumentElement());depths.push(0);int count=0;
        while(!nodes.isEmpty()){
            Node node=nodes.pop();int depth=depths.pop();if(depth>64||++count>MAX_WORDS)throw new IllegalArgumentException("XML 가사 구조가 너무 복잡합니다.");
            for(Node child=node.getFirstChild();child!=null;child=child.getNextSibling()){nodes.push(child);depths.push(depth+1);}
        }
        return doc;
    }
    private static long offset(String raw){Matcher m=OFFSET.matcher(raw);return m.find()?bounded(Long.parseLong(m.group(1))):0;}
    private static List<Line> parseLrc(String raw,long duration){
        List<Line> rows=new ArrayList<>();long offset=offset(raw);boolean anyTimed=false;
        for(String row:raw.split("\\r?\\n")){
            Matcher m=TOKEN.matcher(row);List<Mark> marks=new ArrayList<>();boolean angle=false;
            while(m.find()){Mark mark=new Mark(m,offset);marks.add(mark);angle|=mark.angle;}
            if(marks.isEmpty()||!row.substring(0,marks.get(0).start).trim().isEmpty())continue;
            anyTimed=true;boolean onlyLastText=true;
            for(int i=0;i+1<marks.size();i++)if(!row.substring(marks.get(i).end,marks.get(i+1).start).trim().isEmpty())onlyLastText=false;
            if(!angle&&onlyLastText){
                String text=row.substring(marks.get(marks.size()-1).end).trim();if(text.equals("//"))text="";
                for(Mark mark:marks)rows.add(new Line(Math.max(0,mark.time),-1,text));
            }else{
                Line line=new Line(Math.max(0,marks.get(0).time),-1,"");StringBuilder text=new StringBuilder();
                for(int i=0;i<marks.size();i++){
                    Mark mark=marks.get(i);String piece=row.substring(mark.end,i+1<marks.size()?marks.get(i+1).start:row.length());
                    text.append(piece);
                    if(!piece.isEmpty())line.words.add(new Word(Math.max(0,mark.time),i+1<marks.size()?Math.max(0,marks.get(i+1).time):-1,piece));
                }
                line.text=text.toString().trim();line.realTiming=!line.words.isEmpty();trimWordEdges(line);rows.add(line);
            }
            checkCount(rows);
        }
        if(!anyTimed){
            for(String row:raw.split("\\r?\\n"))if(!row.trim().isEmpty()&&!row.trim().matches("\\[[a-zA-Z]+:.*")){rows.add(new Line(-1,-1,row.trim()));checkCount(rows);}
        }else{
            // Repeated LRC timestamps represent separate language tracks in the original parser.
            Map<Long,Line> first=new LinkedHashMap<>();List<Line> merged=new ArrayList<>();
            for(Line line:rows){Line prior=first.get(line.start);
                if(prior==null){first.put(line.start,line);merged.add(line);}
                else if(!prior.text.equals(line.text)){
                    if(prior.translation.isEmpty())prior.translation=line.text;else if(prior.romanization.isEmpty())prior.romanization=line.text;
                }
            }rows=merged;
        }
        return finish(rows,duration);
    }
    public static List<Line> parseYrc(String raw,long duration){return parseNumeric(raw,duration,'y');}
    private static List<Line> parseNumeric(String raw,long duration,char format){
        if(raw==null||raw.length()>MAX_SIZE)throw new IllegalArgumentException("가사 파일은 2MB 이하로 선택해 주세요.");
        List<Line> lines=new ArrayList<>();long offset=offset(raw);
        Pattern wordPattern=format=='k'?KRC_WORD:format=='y'?YRC_WORD:QRC_WORD;
        for(String row:raw.split("\\r?\\n")){
            Matcher lm=TIMED_LINE.matcher(row.trim());if(!lm.matches())continue;
            long start=bounded(Long.parseLong(lm.group(1)))+offset,end=start+bounded(Long.parseLong(lm.group(2)));
            Line line=new Line(Math.max(0,start),end,"");Matcher wm=wordPattern.matcher(lm.group(3));StringBuilder text=new StringBuilder();
            while(wm.find()){
                String piece=format=='q'?wm.group(1):wm.group(3);
                long begin=Long.parseLong(format=='q'?wm.group(2):wm.group(1));
                long len=Long.parseLong(format=='q'?wm.group(3):wm.group(2));
                begin=bounded(begin)+(format=='k'?start:offset);long wordEnd=begin+bounded(len);
                text.append(piece);line.words.add(new Word(Math.max(0,begin),wordEnd,piece));
                if(line.words.size()>MAX_WORDS)throw new IllegalArgumentException("가사 음절 수가 너무 많습니다.");
            }
            line.text=text.toString().trim();line.realTiming=!line.words.isEmpty();trimWordEdges(line);
            if(!line.words.isEmpty()&&format!='y'){line.start=line.words.get(0).start;line.end=line.words.get(line.words.size()-1).end;}
            if(!line.text.isEmpty()){lines.add(line);checkCount(lines);}
        }
        if(format=='k')attachKrcTranslations(lines,raw);
        return finish(lines,duration);
    }
    public static long parseTime(String t){
        t=t.trim();if(t.isEmpty())return -1;
        double multiplier=1000;
        if(t.endsWith("ms")){multiplier=1;t=t.substring(0,t.length()-2);}
        else if(t.endsWith("s"))t=t.substring(0,t.length()-1);
        else if(t.endsWith("m")){multiplier=60000;t=t.substring(0,t.length()-1);}
        else if(t.endsWith("h")){multiplier=3600000;t=t.substring(0,t.length()-1);}
        String[] parts=t.split(":");if(parts.length>3)throw new IllegalArgumentException("지원하지 않는 시간 형식입니다.");
        double seconds=0;for(String part:parts)seconds=seconds*60+Double.parseDouble(part);
        double result=seconds*multiplier;if(!Double.isFinite(result)||result<0||result>MAX_TIME)throw new IllegalArgumentException("잘못된 가사 시간입니다.");
        return Math.round(result);
    }
    private static long bounded(long t){if(t>MAX_TIME||t< -MAX_TIME)throw new IllegalArgumentException("잘못된 가사 시간입니다.");return t;}
    private static String local(Node n){return n.getLocalName()!=null?n.getLocalName():n.getNodeName().replaceFirst("^.*:","");}
    private static String attr(Element e,String name){
        if(e.hasAttribute(name))return e.getAttribute(name);
        NamedNodeMap attrs=e.getAttributes();for(int i=0;i<attrs.getLength();i++)if(local(attrs.item(i)).equals(name))return attrs.item(i).getNodeValue();
        return "";
    }
    private static String role(Element e){String s=e.getAttributeNS(META,"role");return s.isEmpty()?attr(e,"role"):s;}
    private static String language(Element e){String s=e.getAttributeNS(XML,"lang");return s.isEmpty()?attr(e,"lang"):s;}
    private static boolean auxiliary(Element e){String r=role(e);return r.equals("x-bg")||r.equals("x-translation")||r.equals("x-roman");}
    private static List<Element> elements(Element root,String name){
        List<Element> out=new ArrayList<>();NodeList all=root.getElementsByTagName("*");
        for(int i=0;i<all.getLength();i++){Element e=(Element)all.item(i);if(local(e).equals(name))out.add(e);}return out;
    }
    private static List<Line> parseTtml(Document doc,long duration){
        Element root=doc.getDocumentElement();if(attr(root,"timing").equals("None"))return new ArrayList<>();
        List<HeadTrack> headTracks=new ArrayList<>();List<Element> heads=elements(root,"head");
        if(!heads.isEmpty())for(Element group:elements(heads.get(0),"translation"))
            for(Element text:elements(group,"text"))if(!attr(text,"for").isEmpty())headTracks.add(new HeadTrack(attr(text,"for"),language(group),false,text));
        if(!heads.isEmpty())for(Element group:elements(heads.get(0),"transliteration"))
            for(Element text:elements(group,"text"))if(!attr(text,"for").isEmpty())headTracks.add(new HeadTrack(attr(text,"for"),language(group),true,text));
        List<Line> out=new ArrayList<>();List<Element> bodies=elements(root,"body");if(bodies.isEmpty())return out;
        for(Element p:elements(bodies.get(0),"p")){
            String agent=attr(p,"agent");Line main=segment(p,agent,false,0,-1);List<Line> group=new ArrayList<>();
            if(!main.text.isEmpty()){group.add(main);out.add(main);}
            collectBackground(p,agent,main.start,main.end,group);
            for(Line line:group)if(line!=main)out.add(line);
            String key=attr(p,"key");
            for(HeadTrack track:headTracks)if(!key.isEmpty()&&track.key.equals(key)){
                Line value=segment(track.text,agent,false,main.start,main.end);putAux(main,track.lang,value.text,track.roman);
                List<Line> bgTracks=new ArrayList<>();collectBackground(track.text,agent,main.start,main.end,bgTracks);
                for(Line bg:bgTracks)for(Line original:group)if(original.bg&&Math.abs(bg.start-original.start)<=50){putAux(original,track.lang,bg.text,track.roman);break;}
            }
            checkCount(out);
        }
        return finish(out,duration); // Deliberately never merges equal-time TTML voices.
    }
    private static void collectBackground(Element container,String agent,long start,long end,List<Line> out){
        for(Node n=container.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element){Element child=(Element)n;
            if(role(child).equals("x-bg")){
                Line bg=segment(child,attr(child,"agent").isEmpty()?agent:attr(child,"agent"),true,start,end);
                if(!bg.text.isEmpty()){out.add(bg);checkCount(out);}
            }else if(!auxiliary(child))collectBackground(child,agent,start,end,out);
        }
    }
    private static Line segment(Element container,String agent,boolean bg,long fallbackStart,long fallbackEnd){
        long start=container.hasAttribute("begin")?parseTime(container.getAttribute("begin")):fallbackStart;
        long end=container.hasAttribute("end")?parseTime(container.getAttribute("end")):fallbackEnd;
        if(end<0&&container.hasAttribute("dur"))end=start+parseTime(container.getAttribute("dur"));
        Line line=new Line(start,end,"");line.agent=agent;line.bg=bg;StringBuilder text=new StringBuilder();
        appendPrimary(container,line,text,start,end);line.text=text.toString().trim();trimWordEdges(line);
        line.realTiming=!line.words.isEmpty();
        if(!container.hasAttribute("begin")&&!line.words.isEmpty())line.start=line.words.get(0).start;
        if(!container.hasAttribute("end")&&!container.hasAttribute("dur")&&!line.words.isEmpty())line.end=line.words.get(line.words.size()-1).end;
        for(Node n=container.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element){Element child=(Element)n;String r=role(child);
            if(r.equals("x-translation")||r.equals("x-roman"))putAux(line,language(child),child.getTextContent().trim(),r.equals("x-roman"));
        }
        return line;
    }
    private static void appendPrimary(Element container,Line line,StringBuilder text,long inheritedStart,long inheritedEnd){
        for(Node n=container.getFirstChild();n!=null;n=n.getNextSibling()){
            if(n.getNodeType()==Node.TEXT_NODE){
                String value=n.getNodeValue();if(value.indexOf('\n')>=0||value.indexOf('\r')>=0)value=value.trim();
                text.append(value);if(!line.words.isEmpty()){line.words.get(line.words.size()-1).text+=value;line.coveredChars+=value.length();}continue;
            }
            if(!(n instanceof Element))continue;Element e=(Element)n;if(auxiliary(e))continue;
            if(local(e).equals("br")){text.append(' ');if(!line.words.isEmpty()){line.words.get(line.words.size()-1).text+=" ";line.coveredChars++;}continue;}
            long start=e.hasAttribute("begin")?parseTime(e.getAttribute("begin")):inheritedStart;
            long end=e.hasAttribute("end")?parseTime(e.getAttribute("end")):inheritedEnd;
            if(e.hasAttribute("dur")&&!e.hasAttribute("end"))end=start+parseTime(e.getAttribute("dur"));
            String ruby=e.getAttributeNS(STYLE,"ruby");if(ruby.isEmpty())ruby=attr(e,"ruby");
            if(ruby.equals("container")){
                String base="";List<Element> all=elements(e,"span");List<Element> phonetic=new ArrayList<>();
                for(Element item:all){String r=attr(item,"ruby");if(r.equals("base"))base+=item.getTextContent();if(r.equals("text"))phonetic.add(item);}
                if(!phonetic.isEmpty()){
                    if(phonetic.get(0).hasAttribute("begin"))start=parseTime(phonetic.get(0).getAttribute("begin"));
                    Element last=phonetic.get(phonetic.size()-1);if(last.hasAttribute("end"))end=parseTime(last.getAttribute("end"));
                }
                if(!base.isEmpty())addWord(line,text,base,start,end);
            }else{
                boolean nested=false;
                for(Node c=e.getFirstChild();c!=null;c=c.getNextSibling())if(c instanceof Element&&!auxiliary((Element)c)){nested=true;break;}
                if(nested)appendPrimary(e,line,text,start,end);
                else{String value=plainPrimary(e);if(!value.isEmpty())addWord(line,text,value,start,end);}
            }
            if(line.words.size()>MAX_WORDS)throw new IllegalArgumentException("가사 음절 수가 너무 많습니다.");
        }
    }
    private static void addWord(Line line,StringBuilder text,String value,long start,long end){
        if(text.length()>line.coveredChars)line.words.add(new Word(line.start,Math.max(line.start+1,start),text.substring(line.coveredChars)));
        text.append(value);line.words.add(new Word(start,end,value));line.coveredChars=text.length();
    }
    private static String plainPrimary(Element e){StringBuilder s=new StringBuilder();
        for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling()){
            if(n.getNodeType()==Node.TEXT_NODE)s.append(n.getNodeValue());
            else if(n instanceof Element&&!auxiliary((Element)n))s.append(plainPrimary((Element)n));
        }return s.toString();
    }
    private static void putAux(Line line,String lang,String text,boolean roman){
        if(text==null||text.isEmpty())return;Map<String,String> map=roman?line.romanizations:line.translations;
        map.put(lang==null?"":lang,text);String selected=select(map,"ko");if(roman)line.romanization=selected;else line.translation=selected;
    }
    private static String select(Map<String,String> map,String target){
        for(Map.Entry<String,String> e:map.entrySet())if(e.getKey().equals(target)||e.getKey().startsWith(target+"-"))return e.getValue();
        if(map.containsKey(""))return map.get("");return map.isEmpty()?"":map.values().iterator().next();
    }
    public static void applyLanguage(List<Line> lines,String target){for(Line l:lines){
        if(!l.translations.isEmpty())l.translation=select(l.translations,target);
        if(!l.romanizations.isEmpty())l.romanization=select(l.romanizations,target);
    }}
    /** Matches intrinsic auxiliary LRC to original lines within the original source's 50ms tolerance. */
    public static void applyTranslation(List<Line> lines,String translationRaw,String romanRaw)throws Exception {
        long duration=0;for(Line l:lines)duration=Math.max(duration,l.end);
        List<Line> translations=translationRaw==null||translationRaw.trim().isEmpty()?Collections.emptyList():parse(translationRaw,duration);
        List<Line> romans=romanRaw==null||romanRaw.trim().isEmpty()?Collections.emptyList():parse(romanRaw,duration);
        for(Line line:lines){
            for(Line t:translations)if(t.start>=0&&Math.abs(t.start-line.start)<=50){line.translation=t.text;break;}
            for(Line t:romans)if(t.start>=0&&Math.abs(t.start-line.start)<=50){line.romanization=t.text;break;}
        }
    }
    private static void trimWordEdges(Line line){
        if(line.words.isEmpty())return;
        Word first=line.words.get(0),last=line.words.get(line.words.size()-1);first.text=first.text.replaceFirst("^\\s+","");last.text=last.text.replaceFirst("\\s+$","");
    }
    private static void checkCount(List<Line> lines){if(lines.size()>MAX_LINES)throw new IllegalArgumentException("가사 줄 수가 너무 많습니다.");}
    private static List<Line> finish(List<Line> lines,long duration){
        duration=Math.max(0,Math.min(MAX_TIME,duration));checkCount(lines);lines.sort(Comparator.comparingLong(l->l.start));int wordCount=0;
        for(int i=0;i<lines.size();i++){
            Line l=lines.get(i);if(l.start<0)continue;bounded(l.start);if(l.end>MAX_TIME)bounded(l.end);
            long next=duration>l.start?duration:l.start+4000;
            for(int j=i+1;j<lines.size();j++)if(lines.get(j).start>l.start){next=lines.get(j).start;break;}
            if(!l.realTiming&&l.words.isEmpty()&&!l.text.isEmpty()){
                long estimatedEnd=l.end>l.start?l.end:next;int count=l.text.codePointCount(0,l.text.length());
                for(int at=0,index=0;at<l.text.length();index++){
                    int cp=l.text.codePointAt(at);String value=new String(Character.toChars(cp));
                    long s=l.start+(estimatedEnd-l.start)*index/count,e=l.start+(estimatedEnd-l.start)*(index+1)/count;
                    l.words.add(new Word(s,Math.max(s+1,e),value));at+=Character.charCount(cp);
                }
            }
            for(int w=0;w<l.words.size();w++){
                Word word=l.words.get(w);bounded(word.start);bounded(word.end);
                if(word.end<word.start)word.end=w+1<l.words.size()?l.words.get(w+1).start:(word.start>=next?word.start+1000:Math.min(word.start+1000,next));
                word.end=Math.max(word.start+1,word.end);
            }
            if(l.end<l.start)l.end=l.words.isEmpty()?next:l.words.get(l.words.size()-1).end;
            l.end=Math.max(l.start+1,l.end);wordCount+=l.words.size();if(wordCount>MAX_WORDS)throw new IllegalArgumentException("가사 음절 수가 너무 많습니다.");
        }return lines;
    }
    @SuppressWarnings("unchecked")
    private static void attachKrcTranslations(List<Line> lines,String raw){
        Matcher m=Pattern.compile("\\[language:([^]\\r\\n]+)\\]").matcher(raw);if(!m.find())return;
        try{
            String json=new String(Base64.getDecoder().decode(m.group(1)),StandardCharsets.UTF_8);
            Object parsed=new JsonReader(json).read();if(!(parsed instanceof Map))return;
            Object content=((Map<String,Object>)parsed).get("content");if(!(content instanceof List))return;
            for(Object item:(List<?>)content)if(item instanceof Map){Map<String,Object> obj=(Map<String,Object>)item;
                if(!(obj.get("type") instanceof Number)||((Number)obj.get("type")).intValue()!=1)continue;
                Object value=obj.get("lyricContent");if(!(value instanceof List))continue;List<?> rows=(List<?>)value;
                for(int i=0;i<Math.min(rows.size(),lines.size());i++){Object row=rows.get(i);if(row instanceof List&&!((List<?>)row).isEmpty()){
                    Object text=((List<?>)row).get(0);if(text instanceof String&&!text.equals("//"))lines.get(i).translation=(String)text;
                }}break;
            }
        }catch(Exception ignored){} // Optional translation metadata cannot invalidate usable original lyrics.
    }
    /** Small bounded JSON reader for optional KRC language metadata; avoids Android-only dependencies. */
    private static final class JsonReader {
        final String s;int at=0,depth=0;JsonReader(String text){if(text.length()>MAX_SIZE)throw new IllegalArgumentException();s=text;}
        Object read(){space();if(++depth>32)throw new IllegalArgumentException();Object value;
            char c=s.charAt(at);
            if(c=='{'){Map<String,Object> map=new LinkedHashMap<>();at++;space();if(take('}'))value=map;else{do{space();String key=string();space();expect(':');map.put(key,read());space();}while(take(','));expect('}');value=map;}}
            else if(c=='['){List<Object> list=new ArrayList<>();at++;space();if(take(']'))value=list;else{do{list.add(read());space();if(list.size()>MAX_LINES)throw new IllegalArgumentException();}while(take(','));expect(']');value=list;}}
            else if(c=='"')value=string();
            else{int begin=at;while(at<s.length()&&",]} \t\r\n".indexOf(s.charAt(at))<0)at++;String token=s.substring(begin,at);value=token.equals("null")?null:token.equals("true")?Boolean.TRUE:token.equals("false")?Boolean.FALSE:Double.valueOf(token);}
            depth--;return value;
        }
        String string(){expect('"');StringBuilder out=new StringBuilder();while(at<s.length()){
            char c=s.charAt(at++);if(c=='"')return out.toString();if(c=='\\'){char e=s.charAt(at++);
                if(e=='u'){c=(char)Integer.parseInt(s.substring(at,at+4),16);at+=4;}
                else c=e=='n'?'\n':e=='r'?'\r':e=='t'?'\t':e=='b'?'\b':e=='f'?'\f':e;
            }out.append(c);
        }throw new IllegalArgumentException();}
        void space(){while(at<s.length()&&Character.isWhitespace(s.charAt(at)))at++;}
        boolean take(char c){space();if(at<s.length()&&s.charAt(at)==c){at++;return true;}return false;}
        void expect(char c){if(!take(c))throw new IllegalArgumentException();}
    }
}
