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

/** Small, independently testable OAuth PKCE and callback primitives. */
public final class SpotifyProtocol {
    private SpotifyProtocol(){}
    public static String random(){byte[] bytes=new byte[48];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public static String challenge(String verifier){try{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));}catch(Exception e){throw new IllegalStateException(e);}}
    public static String encode(String value){try{return URLEncoder.encode(value,"UTF-8");}catch(Exception e){throw new IllegalStateException(e);}}
    public static String authorize(String clientId,String redirect,String state,String verifier){
        return "https://accounts.spotify.com/authorize?response_type=code&client_id="+encode(clientId)+"&redirect_uri="+encode(redirect)+"&scope="+encode("playlist-read-private playlist-read-collaborative")+"&state="+encode(state)+"&code_challenge_method=S256&code_challenge="+encode(challenge(verifier));
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
}
