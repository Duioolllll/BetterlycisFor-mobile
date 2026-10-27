import io.betterlyrics.mobile.UiAppearance;
import org.json.JSONObject;
public class AppearanceCheck {
 public static void main(String[] args)throws Exception {
  JSONObject d=UiAppearance.parse("broken");
  assert d.getString("preset").equals("original");assert d.getInt("focusPosition")==50;
  JSONObject a=UiAppearance.parse("{\"weight\":9999,\"blur\":-4,\"opacity\":\"75\",\"layout\":\"<script>\",\"showCover\":\"false\",\"showInfo\":false,\"textColor\":\"#AaBbcC\",\"css\":\"bad\"}");
  assert a.getInt("weight")==900;assert a.getInt("blur")==0;assert a.getInt("opacity")==42;
  assert a.getString("layout").equals("auto");assert a.getBoolean("showCover");assert !a.getBoolean("showInfo");
  assert a.getString("textColor").equals("#aabbcc");assert !a.has("css");
  JSONObject round=UiAppearance.parse(a.toString());assert round.similar(a);
  assert UiAppearance.parse("{\"textColor\":\"url(x)\"}").getString("textColor").equals("#ffffff");
  assert UiAppearance.parse("null").similar(d);assert UiAppearance.parse("[]").similar(d);
  for(String font:new String[]{"nanum-gothic","nanum-myeongjo","jua","nanum-pen"})assert UiAppearance.parse("{\"font\":\""+font+"\"}").getString("font").equals(font);
  assert UiAppearance.parse("{\"font\":\"url(bad)\"}").getString("font").equals("system");
  System.out.println("AppearanceCheck: 19 assertions passed");
 }
}
