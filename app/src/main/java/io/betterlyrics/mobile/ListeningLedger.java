package io.betterlyrics.mobile;

import java.util.*;
import org.json.*;

/** Bounded local listening-time totals; elapsed time comes from a monotonic clock. */
public final class ListeningLedger {
    private static final int MAX_TRACKS=500;
    private static final long MAX_GAP=30000,MAX_TOTAL=365L*24*60*60*1000*100;
    private static final class Item {String title,artist,album;long time;Item(String t,String a,String b,long n){title=t;artist=a;album=b;time=n;}}
    private final LinkedHashMap<String,Item> items=new LinkedHashMap<>();
    private String active="";private boolean playing;private long anchor=-1,total;
    private static String clean(String text){if(text==null)return "";text=text.trim();return text.length()>160?text.substring(0,160):text;}
    private static String key(String title,String artist,String album){return (title+'\u0000'+artist+'\u0000'+album).toLowerCase(Locale.ROOT);}
    public ListeningLedger(String stored){
        try{
            JSONObject root=new JSONObject(stored==null||stored.isEmpty()?"{}":stored);total=Math.max(0,Math.min(MAX_TOTAL,root.optLong("total",0)));JSONArray rows=root.optJSONArray("items");
            if(rows!=null)for(int i=0;i<Math.min(MAX_TRACKS,rows.length());i++){
                JSONObject row=rows.optJSONObject(i);if(row==null)continue;String title=clean(row.optString("title","")),artist=clean(row.optString("artist","")),album=clean(row.optString("album",""));
                long time=Math.max(0,Math.min(MAX_TOTAL,row.optLong("time",0)));if(!title.isEmpty()&&time>0)items.put(key(title,artist,album),new Item(title,artist,album,time));
            }
        }catch(JSONException ignored){}
    }
    /** Returns true when a start, pause or track change warrants immediate persistence. */
    public boolean observe(String title,String artist,String album,boolean isPlaying,long now){
        advance(now);title=clean(title);artist=clean(artist);album=clean(album);String next=title.isEmpty()?"":key(title,artist,album);
        boolean changed=!active.equals(next)||playing!=(isPlaying&&!next.isEmpty());active=next;playing=isPlaying&&!next.isEmpty();anchor=now;
        if(playing&&!items.containsKey(active)){
            if(items.size()>=MAX_TRACKS)items.remove(items.keySet().iterator().next());
            items.put(active,new Item(title,artist,album,0));
        }return changed;
    }
    public void advance(long now){
        if(playing&&anchor>=0&&now>=anchor){long elapsed=Math.min(MAX_GAP,now-anchor);Item item=items.get(active);if(item!=null){item.time=Math.min(MAX_TOTAL,item.time+elapsed);total=Math.min(MAX_TOTAL,total+elapsed);}}
        anchor=now;
    }
    public boolean playing(){return playing;}
    public void clear(long now){Item current=items.get(active);items.clear();total=0;anchor=now;if(playing&&current!=null)items.put(active,new Item(current.title,current.artist,current.album,0));}
    public JSONObject snapshot(){
        JSONObject root=new JSONObject();try{
            JSONArray rows=new JSONArray();List<Item> sorted=new ArrayList<>(items.values());sorted.sort((a,b)->Long.compare(b.time,a.time));
            for(Item item:sorted)if(item.time>0)rows.put(new JSONObject().put("title",item.title).put("artist",item.artist).put("album",item.album).put("time",item.time));
            root.put("total",total).put("items",rows);
        }catch(JSONException ignored){}return root;
    }
}
