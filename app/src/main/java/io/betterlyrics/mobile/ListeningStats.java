package io.betterlyrics.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONObject;

/** Records observed playback locally while this process can receive session updates. */
final class ListeningStats {
    private final SharedPreferences prefs;private final ListeningLedger ledger;private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){ledger.advance(SystemClock.elapsedRealtime());save();if(ledger.playing())main.postDelayed(this,15000);}};
    ListeningStats(Context context){prefs=context.getSharedPreferences("listening_stats",0);ledger=new ListeningLedger(prefs.getString("ledger",""));}
    void observe(LyricsRepository.Song song,boolean playing){
        boolean changed=ledger.observe(song.title,song.artist,song.album,playing,SystemClock.elapsedRealtime());
        if(changed){save();main.removeCallbacks(tick);if(ledger.playing())main.postDelayed(tick,15000);}
    }
    JSONObject snapshot(){ledger.advance(SystemClock.elapsedRealtime());save();return ledger.snapshot();}
    JSONObject clear(){ledger.clear(SystemClock.elapsedRealtime());save();return ledger.snapshot();}
    private void save(){prefs.edit().putString("ledger",ledger.snapshot().toString()).apply();}
}
