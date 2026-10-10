package com.kukuqi.tvbox.osc.player;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoView;

public class MyVideoView extends VideoView {
    private boolean sleepPaused;
    private boolean refreshDanmaku, hasDanmakuContext;
    private com.google.gson.JsonElement danmakuSource;
    private String danmakuName = "", danmakuEpisode = "";
    private com.kukuqi.tvbox.osc.danmaku.DanmakuView danmakuView;
    private com.kukuqi.tvbox.osc.danmaku.DanmakuController danmaku;
    private void initDanmaku(Context context) {
        danmakuView = new com.kukuqi.tvbox.osc.danmaku.DanmakuView(context, this);
        danmaku = new com.kukuqi.tvbox.osc.danmaku.DanmakuController(danmakuView);
        mPlayerContainer.addView(danmakuView, new android.widget.FrameLayout.LayoutParams(-1, -1));
    }
    public void setDanmakuContext(com.google.gson.JsonElement source, String name, String episode) {
        danmakuSource = source; danmakuName = name; danmakuEpisode = episode; hasDanmakuContext = true; refreshDanmaku = false;
        danmaku.load(source, name, episode);
    }
    public String getDanmakuStatus() { return danmaku.status(); }
    public int getDanmakuCount() { return danmakuView.count(); }

    @Override public void start() {
        sleepPaused = false; // An explicit press on Play permits playback after the timer.
        SleepTimer.get().register(this);
        if (sleepPaused) return;
        super.start();
        if (refreshDanmaku && hasDanmakuContext) { refreshDanmaku = false; danmaku.load(danmakuSource, danmakuName, danmakuEpisode); }
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
        if (danmakuView != null) danmakuView.invalidate();
        // A player finishing preparation/buffering must not restart after expiration.
        if (sleepPaused && (state == STATE_PLAYING || state == STATE_BUFFERED || state == STATE_PREPARED)) post(this::pause);
    }

    @Override public void release() {
        if (danmaku != null) { danmaku.cancel(); refreshDanmaku = true; }
        SleepTimer.get().unregister(this);
        super.release();
    }
    public MyVideoView(@NonNull Context context) {
        this(context, null);
    }

    public MyVideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MyVideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initDanmaku(context);
    }

    public AbstractPlayer getMediaPlayer() {
        return mMediaPlayer;
    }

}
