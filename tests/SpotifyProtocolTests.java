import io.betterlyrics.mobile.SpotifyProtocol;

public final class SpotifyProtocolTests {
    private static int checks;
    private static void ok(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    private static void rejected(String target,String state){try{SpotifyProtocol.callback(target,state);throw new AssertionError("unsafe callback accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        String verifier="dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        ok(SpotifyProtocol.challenge(verifier).equals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"),"RFC 7636 S256 vector");
        String one=SpotifyProtocol.random(),two=SpotifyProtocol.random();ok(one.length()>=43&&!one.equals(two)&&one.matches("[A-Za-z0-9_-]+"),"random URL-safe verifiers");
        String url=SpotifyProtocol.authorize("client",SpotifyProtocol.REDIRECT_URI,"state",verifier);
        ok(url.startsWith("https://accounts.spotify.com/authorize?")&&url.contains("code_challenge_method=S256")&&url.contains("playlist-read-private")&&url.contains("user-modify-playback-state"),"official PKCE authorization URL includes playback permission");
        ok(SpotifyProtocol.CALLBACK_PORT==43123&&SpotifyProtocol.REDIRECT_URI.equals("http://127.0.0.1:43123/callback")&&url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A43123%2Fcallback"),"authorization URI exactly matches the registered fixed loopback address");
        ok(SpotifyProtocol.callback("/callback?code=a%2Bb&state=expected","expected").code.equals("a+b"),"encoded authorization code");
        ok(SpotifyProtocol.callback("/callback?error=access_denied&state=expected","expected").error.equals("access_denied"),"user denial");
        rejected("/callback?code=abc&state=wrong","expected");
        rejected("/callback?code=abc&state=expected&state=expected","expected");
        rejected("/other?code=abc&state=expected","expected");
        rejected("/callback?code=abc&error=access_denied&state=expected","expected");
        String id="0123456789ABCDEFGHIJKL";
        ok(SpotifyProtocol.hasScope("playlist-read-private user-modify-playback-state",SpotifyProtocol.PLAYBACK_SCOPE)&&!SpotifyProtocol.hasScope("user-modify-playback-state-extra",SpotifyProtocol.PLAYBACK_SCOPE),"playback permission matches a complete granted scope");
        org.json.JSONObject whole=SpotifyProtocol.playPlaylist(id,-1),selected=SpotifyProtocol.playPlaylist(id,51);
        ok(whole.getString("context_uri").equals("spotify:playlist:"+id)&&!whole.has("offset")&&selected.getJSONObject("offset").getInt("position")==51,"whole playlist and selected position use Spotify Connect context");
        try{SpotifyProtocol.playPlaylist("invalid",0);throw new AssertionError("invalid playlist accepted");}catch(IllegalArgumentException expected){checks++;}
        try{SpotifyProtocol.playPlaylist(id,-2);throw new AssertionError("invalid position accepted");}catch(IllegalArgumentException expected){checks++;}
        org.json.JSONObject list=new org.json.JSONObject().put("next","https://untrusted.invalid/").put("items",new org.json.JSONArray().put(new org.json.JSONObject().put("id",id).put("name","목록").put("owner",new org.json.JSONObject().put("display_name","사용자")).put("items",new org.json.JSONObject().put("total",2))).put(new org.json.JSONObject().put("id","invalid")));
        org.json.JSONObject mapped=SpotifyProtocol.playlistsPage(list,50);ok(mapped.getJSONArray("items").length()==1&&mapped.getJSONArray("items").getJSONObject(0).getInt("count")==2&&mapped.getInt("nextOffset")==52&&mapped.getBoolean("more"),"Spotify playlist pages preserve API offsets and filter invalid IDs");
        org.json.JSONObject song=new org.json.JSONObject().put("id",id).put("type","track").put("name","노래").put("artists",new org.json.JSONArray().put(new org.json.JSONObject().put("name","하나")).put(new org.json.JSONObject().put("name","둘")));
        org.json.JSONObject unknown=new org.json.JSONObject().put("id",id).put("type","new-kind").put("name","새 항목");
        org.json.JSONObject trackPage=SpotifyProtocol.tracksPage(new org.json.JSONObject().put("items",new org.json.JSONArray().put(org.json.JSONObject.NULL).put(new org.json.JSONObject().put("item",song)).put(new org.json.JSONObject().put("track",unknown))).put("next",org.json.JSONObject.NULL),50);
        ok(trackPage.getJSONArray("items").length()==2&&trackPage.getJSONArray("items").getJSONObject(0).getString("artist").equals("하나, 둘")&&trackPage.getJSONArray("items").getJSONObject(0).getInt("position")==51&&trackPage.getJSONArray("items").getJSONObject(1).getString("id").isEmpty()&&!trackPage.getBoolean("more"),"Spotify items retain their playlist positions after unavailable entries and pagination");
        org.json.JSONObject capped=SpotifyProtocol.playlistsPage(list,9999);ok(capped.getBoolean("truncated")&&!capped.getBoolean("more"),"Spotify pagination reports the safety limit");
        System.out.println("SpotifyProtocolTests: "+checks+" checks passed");
    }
}
