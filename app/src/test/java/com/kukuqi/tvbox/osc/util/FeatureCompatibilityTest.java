package com.kukuqi.tvbox.osc.util;

import com.kukuqi.tvbox.osc.danmaku.DanmakuTimeline;
import java.nio.file.Files;
import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeatureCompatibilityTest {
    @Test public void biliAndDandanShareMediaTimeAndPreserveText() {
        DanmakuTimeline xml = DanmakuTimeline.parse("<i><d p='2.5,5,25,16711680'>字幕 &amp; &#x4e2d;</d><d p='1,1,25,16777215'>前一条</d><d p='-1,1,25,1'>无效</d></i>");
        assertEquals(2, xml.entries.size()); assertEquals(1000, xml.entries.get(0).time);
        assertEquals("字幕 & 中", xml.entries.get(1).text); assertEquals(0xffff0000, xml.entries.get(1).color);
        assertEquals(1, xml.from(1001)); assertEquals(2, xml.from(2501));
        DanmakuTimeline json = DanmakuTimeline.parse("{\"comments\":[{\"p\":\"2.5,5,16711680,0\",\"m\":\"字幕 & 中\"}]}");
        assertEquals(xml.entries.get(1).time, json.entries.get(0).time);
        assertEquals(xml.entries.get(1).mode, json.entries.get(0).mode);
        assertEquals(xml.entries.get(1).text, json.entries.get(0).text);
    }
    @Test public void hotWordsKeepCompleteTitlesAndDeduplicate() {
        assertEquals(java.util.Arrays.asList("庆余年 第二季", "三体"), HotSearch.parse("{\"data\":{\"mapResult\":{\"0\":{\"listInfo\":[{\"title\":\"《庆余年 第二季》\"},{\"title\":\"<b>三体</b>\"},{\"title\":\"三体\"}]}}}}"));
        assertEquals(java.util.Arrays.asList("三体"), HotSearch.parse("{\"subjects\":[{\"title\":\"三体\"}]}"));
        assertTrue(HotSearch.parse("{\"subjects\":[]}").isEmpty());
    }
    @Test public void cleanerDeletesNestedCacheAndKeepsRoot() throws Exception {
        File root = Files.createTempDirectory("tvbox-cache").toFile();
        File nested = new File(root, "nested"); assertTrue(nested.mkdir());
        Files.write(new File(nested, "file").toPath(), new byte[]{1});
        CacheCleaner.clear(root); assertTrue(root.isDirectory()); assertEquals(0, root.list().length);
        assertTrue(root.delete());
    }
}
