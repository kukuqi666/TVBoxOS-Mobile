package com.kukuqi.tvbox.osc.util;

import com.kukuqi.tvbox.osc.bean.LiveChannelGroup;
import com.kukuqi.tvbox.osc.bean.LiveChannelItem;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LiveChannelBrowserTest {
    @Test public void searchKeepsOriginalIndicesAcrossGroupsAndDuplicateNames() {
        List<LiveChannelGroup> groups = Arrays.asList(group("新闻", "", "一台", "同名"), group("地方", "", "同名", "目标台"));
        List<LiveChannelBrowser.Channel> found = LiveChannelBrowser.filter(groups, " 目标 ", false, Collections.emptySet(), Collections.emptySet());
        assertEquals(1, found.size()); assertEquals(1, found.get(0).groupIndex); assertEquals(1, found.get(0).channelIndex);
        assertSame(groups.get(1).getLiveChannels().get(1), found.get(0).item);
        assertEquals(2, LiveChannelBrowser.filter(groups, "同名", false, Collections.emptySet(), Collections.emptySet()).size());
        assertEquals(2, LiveChannelBrowser.filter(groups, "地方", false, Collections.emptySet(), Collections.emptySet()).size());
    }
    @Test public void lockedGroupsCannotLeakThroughSearchOrFavorites() {
        List<LiveChannelGroup> groups = Arrays.asList(group("新闻", "", "一台"), group("加密", "123", "目标台"));
        Set<String> saved = Collections.singleton(LiveFavorites.nameKey("目标台"));
        assertTrue(LiveChannelBrowser.filter(groups, "目标", false, saved, Collections.emptySet()).isEmpty());
        assertTrue(LiveChannelBrowser.filter(groups, "", true, saved, Collections.emptySet()).isEmpty());
        assertEquals(1, LiveChannelBrowser.filter(groups, "", true, saved, Collections.singleton(1)).size());
    }
    @Test public void favoriteMatchingSurvivesCaseAndSurroundingWhitespace() {
        List<LiveChannelGroup> groups = Collections.singletonList(group("新闻", "", " CCTV-1 ", "CCTV-2"));
        assertEquals(1, LiveChannelBrowser.filter(groups, "", true, Collections.singleton(LiveFavorites.nameKey("cctv-1")), Collections.emptySet()).size());
    }
    private LiveChannelGroup group(String name, String password, String... names) {
        LiveChannelGroup group = new LiveChannelGroup(); group.setGroupName(name); group.setGroupPassword(password);
        ArrayList<LiveChannelItem> channels = new ArrayList<>();
        for (String n : names) { LiveChannelItem item = new LiveChannelItem(); item.setChannelName(n); channels.add(item); }
        group.setLiveChannels(channels); return group;
    }
}
