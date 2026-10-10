package com.kukuqi.tvbox.osc.util;

import com.kukuqi.tvbox.osc.bean.LiveChannelGroup;
import com.kukuqi.tvbox.osc.bean.LiveChannelItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Filtered positions must never replace the indices in the original playlist. */
public final class LiveChannelBrowser {
    public static final class Channel {
        public final int groupIndex, channelIndex;
        public final String groupName;
        public final LiveChannelItem item;
        Channel(int group, int channel, String name, LiveChannelItem item) {
            groupIndex = group; channelIndex = channel; groupName = name; this.item = item;
        }
    }

    public static List<Channel> filter(List<LiveChannelGroup> groups, String query,
            boolean favoritesOnly, Set<String> favorites, Set<Integer> unlocked) {
        List<Channel> result = new ArrayList<>();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (int g = 0; g < groups.size(); g++) {
            LiveChannelGroup group = groups.get(g);
            String password = group.getGroupPassword();
            if (password != null && !password.isEmpty() && !unlocked.contains(g)) continue;
            if (group.getLiveChannels() == null) continue;
            for (int c = 0; c < group.getLiveChannels().size(); c++) {
                LiveChannelItem item = group.getLiveChannels().get(c);
                String name = item.getChannelName() == null ? "" : item.getChannelName();
                if (favoritesOnly && !favorites.contains(LiveFavorites.nameKey(name))) continue;
                String searchable = (name + " " + group.getGroupName() + " " + item.getChannelNum()).toLowerCase(Locale.ROOT);
                if (searchable.contains(needle)) result.add(new Channel(g, c, group.getGroupName(), item));
            }
        }
        return result;
    }
}
