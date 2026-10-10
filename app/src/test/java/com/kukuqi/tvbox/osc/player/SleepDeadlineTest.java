package com.kukuqi.tvbox.osc.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class SleepDeadlineTest {
    @Test public void expirationOccursOnceAtTheBoundary() {
        SleepDeadline timer = new SleepDeadline();
        timer.set(10_000, 60_000);
        assertEquals(1, timer.remaining(69_999));
        assertFalse(timer.consumeIfExpired(69_999));
        assertTrue(timer.consumeIfExpired(70_000));
        assertFalse(timer.consumeIfExpired(70_001));
        assertFalse(timer.isScheduled());
    }
    @Test public void changingTimerReplacesTheOriginalDeadline() {
        SleepDeadline timer = new SleepDeadline();
        timer.set(100, 60_000); timer.set(200, 120_000);
        assertFalse(timer.consumeIfExpired(60_100));
        assertEquals(60_100, timer.remaining(60_100));
        assertTrue(timer.consumeIfExpired(120_200));
    }
    @Test public void extendAndCancelDoNotRetainAnOldDeadline() {
        SleepDeadline timer = new SleepDeadline();
        timer.set(100, 60_000); timer.extend(30_100, 300_000);
        assertEquals(330_000, timer.remaining(30_100));
        timer.cancel();
        assertEquals(0, timer.remaining(400_000));
        assertFalse(timer.consumeIfExpired(400_000));
        timer.extend(500_000, 300_000);
        assertEquals(300_000, timer.remaining(500_000));
    }
}
