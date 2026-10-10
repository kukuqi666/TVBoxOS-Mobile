package com.kukuqi.tvbox.osc.player;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.TextView;
import com.blankj.utilcode.util.ToastUtils;
import com.kukuqi.tvbox.osc.ui.dialog.SleepTimerDialog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** One timer across all local, live and VOD players, including the background service. */
public final class SleepTimer {
    private static final SleepTimer INSTANCE = new SleepTimer();
    public static SleepTimer get() { return INSTANCE; }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SleepDeadline deadline = new SleepDeadline();
    private final Set<MyVideoView> players = Collections.newSetFromMap(new WeakHashMap<>());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            checkExpired();
            if (deadline.isScheduled()) handler.postDelayed(this, 1000);
        }
    };
    private SleepTimer() {}
    public void register(MyVideoView player) { players.add(player); checkExpired(); }
    public void unregister(MyVideoView player) { players.remove(player); }
    public long remaining() { checkExpired(); return deadline.remaining(SystemClock.elapsedRealtime()); }
    public void setMinutes(int minutes) {
        deadline.set(SystemClock.elapsedRealtime(), minutes * 60_000L);
        handler.removeCallbacks(tick); handler.post(tick);
    }
    public void extend() {
        checkExpired(); deadline.extend(SystemClock.elapsedRealtime(), 5 * 60_000L);
        handler.removeCallbacks(tick); handler.post(tick);
    }
    public void cancel() { deadline.cancel(); handler.removeCallbacks(tick); }
    public void checkExpired() {
        if (!deadline.consumeIfExpired(SystemClock.elapsedRealtime())) return;
        for (MyVideoView player : new ArrayList<>(players)) player.pauseForSleepTimer();
        ToastUtils.showShort("定时已结束，播放已暂停");
    }
    public String description() {
        long seconds = (remaining() + 999) / 1000;
        return seconds == 0 ? "定时停止 · 未开启" : "定时停止 · 剩余 " + seconds / 60 + "分" + seconds % 60 + "秒";
    }
    public static void bind(TextView view) {
        view.setOnClickListener(v -> SleepTimerDialog.show(v.getContext()));
        Runnable update = new Runnable() {
            @Override public void run() {
                view.setText(get().description());
                if (view.isAttachedToWindow()) view.postDelayed(this, 1000);
            }
        };
        view.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(android.view.View v) { view.removeCallbacks(update); view.post(update); }
            @Override public void onViewDetachedFromWindow(android.view.View v) { view.removeCallbacks(update); }
        });
        view.post(update);
    }
}
