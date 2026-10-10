package com.kukuqi.tvbox.osc.player;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoView;

public class MyVideoView extends VideoView {
    private boolean sleepPaused;

    @Override public void start() {
        sleepPaused = false; // An explicit press on Play permits playback after the timer.
        SleepTimer.get().register(this);
        if (sleepPaused) return;
        super.start();
    }

    @Override public void resume() {
        SleepTimer.get().checkExpired();
        if (!sleepPaused) super.resume();
    }

    public void pauseForSleepTimer() {
        sleepPaused = true;
        pause();
    }

    @Override protected void setPlayState(int state) {
        super.setPlayState(state);
        // A player finishing preparation/buffering must not restart after expiration.
        if (sleepPaused && (state == STATE_PLAYING || state == STATE_BUFFERED || state == STATE_PREPARED)) post(this::pause);
    }

    @Override public void release() {
        SleepTimer.get().unregister(this);
        super.release();
    }
    public MyVideoView(@NonNull Context context) {
        super(context, null);
    }

    public MyVideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs, 0);
    }

    public MyVideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AbstractPlayer getMediaPlayer() {
        return mMediaPlayer;
    }

}
