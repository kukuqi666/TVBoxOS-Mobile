package com.kukuqi.tvbox.osc.player;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Rational;
import com.kukuqi.tvbox.osc.constant.IntentKey;
import com.kukuqi.tvbox.osc.service.PlayService;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.kukuqi.tvbox.osc.util.Utils;
import com.orhanobut.hawk.Hawk;
import java.util.Arrays;
import java.util.function.Supplier;

/** Handles the same background setting for VOD, live and local playback. */
public final class PlaybackBackground {
    private final Activity activity;
    private final Supplier<MyVideoView> player;
    private final Supplier<String> title;
    private final Runnable previous, next;
    private boolean leaving, registered;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            MyVideoView view = player.get();
            if (view == null) return;
            int action = intent.getIntExtra("action", -1);
            if (action == IntentKey.BROADCAST_ACTION_PREV) previous.run();
            else if (action == IntentKey.BROADCAST_ACTION_NEXT) next.run();
            else if (action == IntentKey.BROADCAST_ACTION_PLAYPAUSE) { if (view.isPlaying()) view.pause(); else view.start(); }
            else if (action == IntentKey.BROADCAST_ACTION_CLOSE) { PlayService.stop(view); activity.finish(); }
        }
    };
    public PlaybackBackground(Activity activity, Supplier<MyVideoView> player, Supplier<String> title, Runnable previous, Runnable next) {
        this.activity = activity; this.player = player; this.title = title; this.previous = previous; this.next = next;
    }
    public void onUserLeaveHint() {
        MyVideoView view = player.get();
        if (view == null || !view.isPlaying()) return;
        int mode = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0);
        leaving = mode == 1;
        if (mode == 2 && Utils.supportsPiPMode()) {
            leaving = true;
            int[] size = view.getVideoSize();
            double ratio = size[0] > 0 && size[1] > 0 ? (double) size[0] / size[1] : 16.0 / 9;
            ratio = Math.max(1.0 / 2.39, Math.min(2.39, ratio));
            try {
                register();
                leaving = activity.enterPictureInPictureMode(new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational((int) (ratio * 10000), 10000))
                        .setActions(Arrays.asList(action(android.R.drawable.ic_media_previous, IntentKey.BROADCAST_ACTION_PREV, "上一项"),
                                action(android.R.drawable.ic_media_play, IntentKey.BROADCAST_ACTION_PLAYPAUSE, "播放/暂停"),
                                action(android.R.drawable.ic_media_next, IntentKey.BROADCAST_ACTION_NEXT, "下一项"))).build());
            } catch (RuntimeException e) { leaving = false; unregister(); }
        }
    }
    public boolean keepsPlaying() {
        return leaving || (Build.VERSION.SDK_INT >= 26 && activity.isInPictureInPictureMode());
    }
    public void onPause() {
        MyVideoView view = player.get();
        if (view == null) return;
        if (keepsPlaying()) {
            if (Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0) == 1) {
                register(); PlayService.start(view, title.get(), new Intent(activity, activity.getClass()).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
        } else view.pause();
    }
    public void onResume() {
        leaving = false;
        MyVideoView view = player.get();
        PlayService.stop(view);
        if (Build.VERSION.SDK_INT < 26 || !activity.isInPictureInPictureMode()) unregister();
    }
    public void onStop() {
        // Closing PiP stops the activity; a full-screen return goes through onResume.
        if (leaving && Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0) == 2
                && Build.VERSION.SDK_INT >= 26 && !activity.isInPictureInPictureMode()) {
            MyVideoView view = player.get(); if (view != null) view.pause();
            leaving = false; activity.finish();
        }
    }
    public void onDestroy() { PlayService.stop(player.get()); unregister(); }
    private RemoteAction action(int icon, int code, String label) {
        return new RemoteAction(Icon.createWithResource(activity, icon), label, label, PlayService.getPendingIntent(code));
    }
    private void register() {
        if (registered) return;
        IntentFilter filter = new IntentFilter(IntentKey.BROADCAST_ACTION);
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else activity.registerReceiver(receiver, filter);
        registered = true;
    }
    private void unregister() { if (registered) { activity.unregisterReceiver(receiver); registered = false; } }
}
