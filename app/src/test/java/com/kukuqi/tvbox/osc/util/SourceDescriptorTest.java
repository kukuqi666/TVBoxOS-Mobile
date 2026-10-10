package com.kukuqi.tvbox.osc.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class SourceDescriptorTest {
    @Test public void combinedSourceKeepsAllThreeCapabilities() {
        SourceDescriptor source = new SourceDescriptor("https://example.com/config/main.json",
                "{\"sites\":[{\"key\":\"demo\"}],\"lives\":[{\"name\":\"频道\",\"type\":0,\"url\":\"../live.m3u\"}],\"wallpaper\":\"../wall.jpg\"}");
        assertTrue(source.hasVod()); assertTrue(source.hasLive());
        assertEquals("https://example.com/live.m3u", source.firstPlaylist());
        assertEquals("https://example.com/wall.jpg", source.wallpapers().get(0));
        assertEquals("点播 · 直播 · 壁纸", source.description());
    }
    @Test public void vodOnlySourceDoesNotInventOtherCapabilities() {
        SourceDescriptor source = new SourceDescriptor("https://example.com/vod.json", "{\"sites\":[{}]}");
        assertTrue(source.hasVod()); assertFalse(source.hasLive()); assertTrue(source.wallpapers().isEmpty());
    }
    @Test public void wallpaperAndLiveOnlyConfigsAreValidIndependentSources() {
        SourceDescriptor wall = new SourceDescriptor("https://example.com/wall.json", "{\"wallpaper\":[\"/a.jpg\",{\"url\":\"/b.jpg\"}]}");
        assertFalse(wall.hasVod()); assertEquals(2, wall.wallpapers().size());
        SourceDescriptor live = new SourceDescriptor("https://example.com/live.json", "{\"lives\":[{\"group\":\"新闻\",\"channels\":[{\"name\":\"频道\",\"urls\":[\"https://example.com/stream\"]}]}]}");
        assertFalse(live.hasVod()); assertTrue(live.hasLive()); assertEquals("", live.firstPlaylist());
    }
}
