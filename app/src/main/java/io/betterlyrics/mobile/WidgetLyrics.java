package io.betterlyrics.mobile;

import java.util.List;

/** Selects a foreground line and the next point at which the widget can change. */
public final class WidgetLyrics {
    private WidgetLyrics() {}
    public static final class Selection {
        public final String text;
        public final long nextChange;
        Selection(String text, long nextChange){this.text=text;this.nextChange=nextChange;}
    }
    public static Selection select(List<LyricParser.Line> lines,long position,boolean positionKnown){
        if(lines==null||lines.isEmpty())return new Selection("",-1);
        boolean timed=false;String plain="";LyricParser.Line active=null;long next=Long.MAX_VALUE;
        for(LyricParser.Line line:lines){
            if(line==null||line.bg)continue;
            String text=line.text==null?"":line.text.trim();
            if(line.start<0){if(plain.isEmpty()&&!text.isEmpty())plain=text;continue;}
            timed=true;
            if(!positionKnown)continue;
            if(line.start>position)next=Math.min(next,line.start);
            if(line.end>position)next=Math.min(next,line.end);
            if(line.start<=position&&(line.end<0||position<line.end)&&(active==null||line.start>=active.start))active=line;
        }
        if(!timed)return new Selection(plain,-1);
        if(!positionKnown)return new Selection("",-1);
        String text=active==null||active.text==null||active.text.trim().isEmpty()?"♪":active.text.trim();
        return new Selection(text,next==Long.MAX_VALUE?-1:next);
    }
    public static long delay(long position,long nextChange,float speed){
        if(nextChange<0||speed<=0||Float.isNaN(speed)||Float.isInfinite(speed))return -1;
        double millis=Math.max(0,nextChange-position)/(double)speed;
        return (long)Math.max(150,Math.min(30000,Math.ceil(millis)+30));
    }
}
