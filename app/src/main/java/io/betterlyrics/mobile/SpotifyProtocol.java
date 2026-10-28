package io.betterlyrics.mobile;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Small, independently testable OAuth PKCE and callback primitives. */
public final class SpotifyProtocol {
    public static final int MAX_ITEMS=10000;
    public static final int CALLBACK_PORT=43123;
    public static final String REDIRECT_URI="http://127.0.0.1:"+CALLBACK_PORT+"/callback";
    public static final String PLAYBACK_SCOPE="user-modify-playback-state";
    private SpotifyProtocol(){}
    public static String random(){byte[] bytes=new byte[48];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public static String challenge(String verifier){try{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));}catch(Exception e){throw new IllegalStateException(e);}}
    public static String encode(String value){try{return URLEncoder.encode(value,"UTF-8");}catch(Exception e){throw new IllegalStateException(e);}}
    public static boolean validId(String id){return id!=null&&id.matches("[A-Za-z0-9]{22}");}
    public static String authorize(String clientId,String redirect,String state,String verifier){
        return "https://accounts.spotify.com/authorize?response_type=code&client_id="+encode(clientId)+"&redirect_uri="+encode(redirect)+"&scope="+encode("playlist-read-private playlist-read-collaborative "+PLAYBACK_SCOPE)+"&state="+encode(state)+"&code_challenge_method=S256&code_challenge="+encode(challenge(verifier));
    }
    public static boolean hasScope(String scopes,String wanted){
        if(scopes==null||wanted==null||wanted.isEmpty())return false;
        for(String value:scopes.split("\\s+"))if(value.equals(wanted))return true;
        return false;
    }
    public static JSONObject playPlaylist(String id,int position)throws JSONException{
        if(!validId(id)||position < -1||position>=MAX_ITEMS)throw new IllegalArgumentException("invalid playlist position");
        JSONObject result=new JSONObject().put("context_uri","spotify:playlist:"+id);
        if(position>=0)result.put("offset",new JSONObject().put("position",position));
        return result;
    }
    public static final class Callback {public final String code,error;Callback(String code,String error){this.code=code;this.error=error;}}
    public static Callback callback(String target,String expectedState){
        try{
            if(target==null||target.length()>8192||!target.startsWith("/callback?"))throw new IllegalArgumentException("invalid callback");
            URI uri=new URI("http://127.0.0.1"+target);if(!"/callback".equals(uri.getPath())||uri.getRawFragment()!=null)throw new IllegalArgumentException("invalid callback");
            Map<String,String> values=new HashMap<>();
            for(String part:uri.getRawQuery().split("&")){
                int equals=part.indexOf('=');String key=URLDecoder.decode(equals<0?part:part.substring(0,equals),"UTF-8");String value=URLDecoder.decode(equals<0?"":part.substring(equals+1),"UTF-8");
                if(values.put(key,value)!=null)throw new IllegalArgumentException("duplicate parameter");
            }
            String state=values.get("state");
            if(state==null||!MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),expectedState.getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException("invalid state");
            String code=values.get("code"),error=values.get("error");
            if((code==null||code.isEmpty())==(error==null||error.isEmpty())||code!=null&&code.length()>4096)throw new IllegalArgumentException("invalid result");
            return new Callback(code,error);
        }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("invalid callback",e);}
    }
    private static JSONObject page(JSONArray items,JSONObject response,int offset,int count)throws JSONException{
        int next=offset+count;boolean hasNext=count>0&&response.has("next")&&!response.isNull("next");
        return new JSONObject().put("items",items).put("offset",offset).put("nextOffset",next).put("more",hasNext&&next<MAX_ITEMS).put("truncated",hasNext&&next>=MAX_ITEMS);
    }
    /** Maps only the Spotify fields displayed by the app; never follows response URLs. */
    public static JSONObject playlistsPage(JSONObject response,int offset)throws JSONException{
        JSONArray output=new JSONArray(),items=response.optJSONArray("items");if(items==null)items=new JSONArray();
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null)continue;String id=item.optString("id");if(!validId(id))continue;
            JSONObject owner=item.optJSONObject("owner"),tracks=item.optJSONObject("items");if(tracks==null)tracks=item.optJSONObject("tracks");
            output.put(new JSONObject().put("id",id).put("name",item.optString("name","재생목록")).put("owner",owner==null?"":owner.optString("display_name")).put("count",tracks==null?-1:tracks.optInt("total",-1)));
        }
        return page(output,response,offset,items.length());
    }
    public static JSONObject tracksPage(JSONObject response,int offset)throws JSONException{
        JSONArray output=new JSONArray(),items=response.optJSONArray("items");if(items==null)items=new JSONArray();
        for(int i=0;i<items.length();i++){
            JSONObject row=items.optJSONObject(i);if(row==null)continue;JSONObject track=row.optJSONObject("item");if(track==null)track=row.optJSONObject("track");if(track==null)continue;
            String type=track.optString("type","track"),id=track.optString("id");StringBuilder artist=new StringBuilder();JSONArray artists=track.optJSONArray("artists");
            if(artists!=null)for(int j=0;j<artists.length();j++){JSONObject a=artists.optJSONObject(j);String name=a==null?"":a.optString("name");if(!name.isEmpty()){if(artist.length()>0)artist.append(", ");artist.append(name);}}
            output.put(new JSONObject().put("id",validId(id)&&(type.equals("track")||type.equals("episode"))?id:"").put("type",type).put("title",track.optString("name","제목 없음")).put("artist",artist.toString()).put("duration",track.optLong("duration_ms",0)).put("position",offset+i));
        }
        return page(output,response,offset,items.length());
    }
}
