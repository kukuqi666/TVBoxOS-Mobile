package com.kukuqi.tvbox.osc.player;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackSpeedTest {
    @Test public void invalidNumbersNeverReachThePlayer() {
        assertEquals(1f, PlaybackSpeed.clamp(Float.NaN), 0f);
        assertEquals(1f, PlaybackSpeed.clamp(Float.POSITIVE_INFINITY), 0f);
        assertEquals(0.1f, PlaybackSpeed.clamp(-1), 0f);
        assertEquals(5f, PlaybackSpeed.clamp(99), 0f);
        assertEquals(1.25f, PlaybackSpeed.clamp(1.25f), 0f);
    }
    @Test public void displayDoesNotDependOnTheDeviceDecimalSeparator() {
        java.util.Locale before = java.util.Locale.getDefault();
        try { java.util.Locale.setDefault(java.util.Locale.GERMANY);
            assertEquals("1.7x", PlaybackSpeed.format(1.7f));
            assertEquals("1.25x", PlaybackSpeed.format(1.25f));
        } finally { java.util.Locale.setDefault(before); }
    }
}
