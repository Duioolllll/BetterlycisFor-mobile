import io.betterlyrics.mobile.SpotifyProtocol;

public final class SpotifyProtocolTests {
    private static int checks;
    private static void ok(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    private static void rejected(String target,String state){try{SpotifyProtocol.callback(target,state);throw new AssertionError("unsafe callback accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        String verifier="dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        ok(SpotifyProtocol.challenge(verifier).equals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"),"RFC 7636 S256 vector");
        String one=SpotifyProtocol.random(),two=SpotifyProtocol.random();ok(one.length()>=43&&!one.equals(two)&&one.matches("[A-Za-z0-9_-]+"),"random URL-safe verifiers");
        String url=SpotifyProtocol.authorize("client","http://127.0.0.1:1234/callback","state",verifier);
        ok(url.startsWith("https://accounts.spotify.com/authorize?")&&url.contains("code_challenge_method=S256")&&url.contains("playlist-read-private"),"official PKCE authorization URL");
        ok(SpotifyProtocol.callback("/callback?code=a%2Bb&state=expected","expected").code.equals("a+b"),"encoded authorization code");
        ok(SpotifyProtocol.callback("/callback?error=access_denied&state=expected","expected").error.equals("access_denied"),"user denial");
        rejected("/callback?code=abc&state=wrong","expected");
        rejected("/callback?code=abc&state=expected&state=expected","expected");
        rejected("/other?code=abc&state=expected","expected");
        rejected("/callback?code=abc&error=access_denied&state=expected","expected");
        System.out.println("SpotifyProtocolTests: "+checks+" checks passed");
    }
}
