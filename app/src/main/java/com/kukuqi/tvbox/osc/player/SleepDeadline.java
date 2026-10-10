package com.kukuqi.tvbox.osc.player;

/** Monotonic deadline, independent of screen rotation, episode changes and wall-clock changes. */
public final class SleepDeadline {
    private long deadline;
    public void set(long now, long duration) { deadline = duration > 0 ? now + duration : 0; }
    public void cancel() { deadline = 0; }
    public boolean isScheduled() { return deadline != 0; }
    public long remaining(long now) { return deadline == 0 ? 0 : Math.max(0, deadline - now); }
    public boolean consumeIfExpired(long now) {
        if (deadline == 0 || now < deadline) return false;
        deadline = 0; return true;
    }
    public void extend(long now, long duration) { set(now, remaining(now) + duration); }
}
