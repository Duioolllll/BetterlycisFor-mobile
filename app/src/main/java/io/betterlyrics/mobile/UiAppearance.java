package io.betterlyrics.mobile;

import org.json.JSONObject;

/** Bounded visual preferences. The bridge cannot persist arbitrary styling or code. */
public final class UiAppearance {
    private UiAppearance() {}
    public static JSONObject sanitize(JSONObject input) {
        JSONObject in=input==null?new JSONObject():input,out=new JSONObject();
        try {
            out.put("version",2);
            choice(in,out,"preset","original","original","focus","album","custom");
            choice(in,out,"layout","auto","auto","immersive","split","reverse");
            choice(in,out,"align","left","left","center","right");
            choice(in,out,"font","system","system","serif","rounded","nanum-gothic","nanum-myeongjo","jua","nanum-pen");
            choice(in,out,"controls","auto","auto","always","minimal");
            // Before v2 the album preset's always-visible setting survived
            // unrelated customization, so its origin cannot be determined.
            if(in.optInt("version",1)<2&&"always".equals(out.optString("controls")))out.put("controls","auto");
            choice(in,out,"flow","continuous","continuous","line");
            number(in,out,"weight",700,400,900);number(in,out,"lineHeight",135,110,180);
            number(in,out,"lineGap",18,4,40);number(in,out,"opacity",42,15,80);
            number(in,out,"blur",1,0,5);number(in,out,"brightness",85,25,100);
            number(in,out,"coverSize",100,60,130);number(in,out,"lyricWidth",90,60,100);
            number(in,out,"focusPosition",50,30,65);
            for(String key:new String[]{"showCover","showInfo","showTranslation","showRomanization","glow"}) {
                Object value=in.opt(key);out.put(key,value instanceof Boolean?value:!key.equals("showRomanization"));
            }
            Object color=in.opt("textColor");out.put("textColor",color instanceof String&&((String)color).matches("#[0-9a-fA-F]{6}")?((String)color).toLowerCase(java.util.Locale.ROOT):"#ffffff");
        } catch(org.json.JSONException impossible) {throw new IllegalStateException(impossible);}
        return out;
    }
    public static JSONObject parse(String input) {
        try {return sanitize(new JSONObject(input));}catch(Exception invalid){return sanitize(null);}
    }
    private static void choice(JSONObject in,JSONObject out,String key,String fallback,String...allowed)throws org.json.JSONException {
        Object value=in.opt(key);String result=fallback;
        if(value instanceof String)for(String choice:allowed)if(choice.equals(value))result=choice;
        out.put(key,result);
    }
    private static void number(JSONObject in,JSONObject out,String key,double fallback,double min,double max)throws org.json.JSONException {
        Object value=in.opt(key);double result=value instanceof Number?((Number)value).doubleValue():fallback;
        if(Double.isNaN(result)||Double.isInfinite(result))result=fallback;
        out.put(key,Math.max(min,Math.min(max,result)));
    }
}
