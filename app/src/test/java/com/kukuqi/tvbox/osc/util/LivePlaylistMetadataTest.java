package com.kukuqi.tvbox.osc.util;

import com.kukuqi.tvbox.osc.util.live.TxtSubscribe;
import com.google.gson.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LivePlaylistMetadataTest {
    @Test public void quotedAttributesAndExtgrpKeepGuideAttachedToCorrectChannel() {
        String text = "#EXTM3U url-tvg='../guide.xml.gz'\n"
                + "#EXTINF:-1 tvg-id='news-1' tvg-name='新闻,高清' group-title='原分类',频道,高清\n"
                + "#EXTGRP:新闻\nhttps://example.test/video\n"
                + "#EXTINF:-1 tvg-id=other group-title=地方,另一个台\nhttps://example.test/other\n";
        LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> parsed = new LinkedHashMap<>();
        TxtSubscribe.parse(parsed, text); JsonArray groups = TxtSubscribe.live2JsonArray(parsed);
        LivePlaylistMetadata.apply(groups, text, "https://example.test/config/live.m3u");
        assertEquals("新闻", groups.get(0).getAsJsonObject().get("group").getAsString());
        assertEquals("https://example.test/guide.xml.gz", groups.get(0).getAsJsonObject().get("epg").getAsString());
        JsonObject channel = groups.get(0).getAsJsonObject().getAsJsonArray("channels").get(0).getAsJsonObject();
        assertEquals("频道,高清", channel.get("name").getAsString());
        assertEquals("news-1", channel.get("tvg-id").getAsString());
        assertEquals("新闻,高清", channel.get("tvg-name").getAsString());
        assertEquals("other", groups.get(1).getAsJsonObject().getAsJsonArray("channels").get(0).getAsJsonObject().get("tvg-id").getAsString());
    }
    @Test public void relativeTemplateIsResolvedWithoutLosingPlaceholders() {
        assertEquals("https://example.test/epg?ch={name}&date={date}", SourceDescriptor.resolve("https://example.test/config/main.json", "../epg?ch={name}&date={date}"));
    }
}
