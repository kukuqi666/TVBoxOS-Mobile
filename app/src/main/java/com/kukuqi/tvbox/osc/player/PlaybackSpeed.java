package com.kukuqi.tvbox.osc.player;

import java.util.Locale;

public final class PlaybackSpeed {
    public static final float MIN = 0.1f, MAX = 5f;
    private PlaybackSpeed() {}
    public static float clamp(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 1f;
        return Math.max(MIN, Math.min(MAX, value));
    }
    public static String format(float value) {
        value = clamp(value);
        return String.format(Locale.ROOT, Math.abs(value * 10 - Math.round(value * 10)) < 0.001f ? "%.1fx" : "%.2fx", value);
    }
}
