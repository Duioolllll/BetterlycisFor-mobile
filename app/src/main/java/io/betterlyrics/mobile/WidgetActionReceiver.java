package io.betterlyrics.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.PlaybackState;

/** Private receiver; the launcher invokes only the immutable widget PendingIntents. */
public final class WidgetActionReceiver extends BroadcastReceiver {
    static final String TOGGLE="io.betterlyrics.mobile.widget.TOGGLE";
    static final String PREVIOUS="io.betterlyrics.mobile.widget.PREVIOUS";
    static final String NEXT="io.betterlyrics.mobile.widget.NEXT";
    @Override public void onReceive(Context context,Intent intent){
        AppState state=AppState.get(context);MediaController controller=state.controller;
        if(controller==null){LyricsListener.refresh(context);return;}
        PlaybackState playback=controller.getPlaybackState();if(playback==null)return;
        long actions=playback.getActions();MediaController.TransportControls controls=controller.getTransportControls();String action=intent.getAction();
        if(TOGGLE.equals(action)){
            if(playback.getState()==PlaybackState.STATE_PLAYING){if((actions&(PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE))!=0)controls.pause();}
            else if((actions&(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_PAUSE))!=0)controls.play();
        }else if(PREVIOUS.equals(action)&&(actions&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0)controls.skipToPrevious();
        else if(NEXT.equals(action)&&(actions&PlaybackState.ACTION_SKIP_TO_NEXT)!=0)controls.skipToNext();
    }
}
