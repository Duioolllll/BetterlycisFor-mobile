import io.betterlyrics.mobile.ListeningLedger;
import org.json.*;

public final class ListeningLedgerTests {
    static int checks;
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static long total(ListeningLedger ledger){return ledger.snapshot().optLong("total");}
    public static void main(String[] args){
        ListeningLedger ledger=new ListeningLedger("");
        ledger.observe("First","Artist","Album",true,1000);ledger.advance(6000);ok(total(ledger)==5000,"playing time accumulates");
        ledger.observe("First","Artist","Album",false,9000);ledger.advance(19000);ok(total(ledger)==8000,"paused time excluded");
        ledger.observe("First","Artist","Album",true,20000);ledger.observe("Second","Artist","Album",true,25000);ledger.advance(30000);
        JSONObject saved=ledger.snapshot();ok(saved.optLong("total")==18000&&saved.optJSONArray("items").length()==2,"track change accounts time to correct songs");
        ListeningLedger restored=new ListeningLedger(saved.toString());restored.advance(50000);ok(total(restored)==18000,"restart never invents background playback");
        restored.observe("Second","Artist","Album",true,50000);restored.advance(110000);ok(total(restored)==48000,"long scheduling gaps are conservatively capped");
        restored.clear(110000);restored.advance(112000);ok(total(restored)==2000&&restored.snapshot().optJSONArray("items").length()==1,"clear resets history while current playback continues");
        restored.observe("","","",true,113000);restored.advance(120000);ok(total(restored)==3000,"unidentified track is not recorded");
        ok(new ListeningLedger("invalid").snapshot().optJSONArray("items").length()==0,"corrupt storage is safely ignored");
        System.out.println("ListeningLedgerTests: "+checks+" checks passed");
    }
}
