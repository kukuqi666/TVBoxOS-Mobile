package com.kukuqi.tvbox.osc;

import android.test.InstrumentationTestCase;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.kukuqi.tvbox.osc.util.SourceLibrary;
import com.kukuqi.tvbox.osc.util.SourceDefaults;
import com.kukuqi.tvbox.osc.bean.Subscription;
import com.orhanobut.hawk.Hawk;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Exercises real persistent Hawk/Gson storage, category isolation and upgrade migration. */
public class SourceLibraryRegressionTest extends InstrumentationTestCase {
    private final Map<String, Object> original = new HashMap<>();
    private final String[] keys = {SourceLibrary.key(SourceLibrary.LIVE), SourceLibrary.key(SourceLibrary.WALLPAPER),
            HawkConfig.LIVE_HISTORY, HawkConfig.WALLPAPER_HISTORY, HawkConfig.LIVE_URL, HawkConfig.WALLPAPER_URL,
            HawkConfig.API_URL, HawkConfig.SUBSCRIPTIONS, HawkConfig.EPG_URL, "wallpaper_mode", "source_defaults_20261009_v1", "independent_sources_20261010_v1"};
    @Override protected void setUp() throws Exception {
        super.setUp();
        for (String key : keys) { if (Hawk.contains(key)) original.put(key, Hawk.get(key)); Hawk.delete(key); }
    }
    @Override protected void tearDown() throws Exception {
        for (String key : keys) { if (original.containsKey(key)) Hawk.put(key, original.get(key)); else Hawk.delete(key); }
        super.tearDown();
    }
    public void testUpgradeRetainsHistoryAndCurrentSourceWithoutDuplicates() {
        Hawk.put(HawkConfig.LIVE_HISTORY, new ArrayList<>(Arrays.asList("https://example.test/a.m3u", "https://example.test/a.m3u")));
        Hawk.put(HawkConfig.LIVE_URL, "https://example.test/current.json");
        assertEquals(2, SourceLibrary.list(SourceLibrary.LIVE).size());
        assertTrue(Hawk.contains(SourceLibrary.key(SourceLibrary.LIVE)));
        assertEquals("https://example.test/current.json", SourceLibrary.list(SourceLibrary.LIVE).get(1).url);
    }
    public void testRequestedDefaultsPreserveSavedSourcesAndLaterUserChoices() {
        String custom = "https://example.test/mine.json";
        ArrayList<Subscription> subscriptions = new ArrayList<>();
        subscriptions.add(new Subscription("自己的源", custom).setChecked(true));
        Hawk.put(HawkConfig.SUBSCRIPTIONS, subscriptions);
        SourceLibrary.save(SourceLibrary.LIVE, "自己的直播", custom);
        SourceLibrary.save(SourceLibrary.WALLPAPER, "自己的壁纸", custom);
        SourceDefaults.applyOnce();
        assertEquals(SourceDefaults.VOD, Hawk.get(HawkConfig.API_URL));
        assertEquals(SourceDefaults.LIVE, Hawk.get(HawkConfig.LIVE_URL));
        assertEquals(SourceDefaults.WALLPAPER, Hawk.get(HawkConfig.WALLPAPER_URL));
        assertEquals("custom", Hawk.get("wallpaper_mode"));
        assertEquals(2, Hawk.<ArrayList<Subscription>>get(HawkConfig.SUBSCRIPTIONS).size());
        assertEquals(1, Hawk.<ArrayList<Subscription>>get(HawkConfig.SUBSCRIPTIONS).stream().filter(Subscription::isChecked).count());
        assertEquals(2, SourceLibrary.list(SourceLibrary.LIVE).size());
        assertEquals(2, SourceLibrary.list(SourceLibrary.WALLPAPER).size());
        Hawk.put(HawkConfig.API_URL, custom);
        Hawk.put(HawkConfig.LIVE_URL, custom);
        Hawk.put(HawkConfig.WALLPAPER_URL, custom);
        SourceDefaults.applyOnce();
        assertEquals(custom, Hawk.get(HawkConfig.API_URL));
        assertEquals(custom, Hawk.get(HawkConfig.LIVE_URL));
        assertEquals(custom, Hawk.get(HawkConfig.WALLPAPER_URL));
    }
    public void testLegacyFollowBecomesIndependentWithoutChangingVodOrReenablingRemovedSources() {
        String custom = "https://example.test/my-vod.json";
        Hawk.put("source_defaults_20261009_v1", true);
        Hawk.put(HawkConfig.API_URL, custom);
        Hawk.put(HawkConfig.LIVE_URL, "");
        Hawk.put("wallpaper_mode", "follow");
        SourceDefaults.applyOnce();
        assertEquals(custom, Hawk.get(HawkConfig.API_URL));
        assertEquals(SourceDefaults.LIVE, Hawk.get(HawkConfig.LIVE_URL));
        assertEquals(SourceDefaults.WALLPAPER, Hawk.get(HawkConfig.WALLPAPER_URL));
        assertEquals("custom", Hawk.get("wallpaper_mode"));
        Hawk.put(HawkConfig.LIVE_URL, "");
        Hawk.put(HawkConfig.WALLPAPER_URL, "");
        SourceDefaults.applyOnce();
        assertEquals("", Hawk.get(HawkConfig.LIVE_URL));
        assertEquals("", Hawk.get(HawkConfig.WALLPAPER_URL));
    }
    public void testBuiltInsCannotBeRemovedOrReplacedWhileUserSourcesCan() {
        SourceDefaults.applyOnce();
        for (int kind : new int[]{SourceLibrary.LIVE, SourceLibrary.WALLPAPER}) {
            String builtIn = kind == SourceLibrary.LIVE ? SourceDefaults.LIVE : SourceDefaults.WALLPAPER;
            String custom = "https://example.test/custom.json";
            SourceLibrary.remove(kind, builtIn);
            SourceLibrary.replace(kind, builtIn, "替换", custom);
            assertTrue(SourceLibrary.list(kind).stream().anyMatch(entry -> entry.url.equals(builtIn)));
            assertFalse(SourceLibrary.list(kind).stream().anyMatch(entry -> entry.url.equals(custom)));
            SourceLibrary.save(kind, "自己的源", custom);
            SourceLibrary.remove(kind, custom);
            assertFalse(SourceLibrary.list(kind).stream().anyMatch(entry -> entry.url.equals(custom)));
        }
    }
    public void testSavingAndRenamingPersistSeparatelyFromSelectionAndOtherCategory() {
        Hawk.put(HawkConfig.LIVE_URL, "https://example.test/current.m3u");
        SourceLibrary.save(SourceLibrary.LIVE, "备用直播", "https://example.test/backup.m3u");
        SourceLibrary.save(SourceLibrary.WALLPAPER, "风景", "https://example.test/wall.jpg");
        SourceLibrary.save(SourceLibrary.LIVE, "家里的直播", "https://example.test/backup.m3u");
        assertEquals("https://example.test/current.m3u", Hawk.get(HawkConfig.LIVE_URL));
        assertEquals(2, SourceLibrary.list(SourceLibrary.LIVE).size());
        assertEquals("家里的直播", SourceLibrary.name(SourceLibrary.LIVE, "https://example.test/backup.m3u"));
        assertEquals(1, SourceLibrary.list(SourceLibrary.WALLPAPER).size());
        assertTrue(Hawk.<String>get(SourceLibrary.key(SourceLibrary.LIVE)).contains("家里的直播"));
    }
    public void testRemovedSourceDoesNotReturnOnNextReadOrHistoryMigration() {
        String url = "https://example.test/remove.m3u";
        Hawk.put(HawkConfig.LIVE_HISTORY, new ArrayList<>(Arrays.asList(url)));
        assertEquals(1, SourceLibrary.list(SourceLibrary.LIVE).size());
        SourceLibrary.remove(SourceLibrary.LIVE, url);
        assertTrue(SourceLibrary.list(SourceLibrary.LIVE).isEmpty());
        assertTrue(SourceLibrary.list(SourceLibrary.LIVE).isEmpty());
        assertTrue(Hawk.<ArrayList<String>>get(HawkConfig.LIVE_HISTORY).isEmpty());
    }
    public void testEditAddressMergesDuplicatesAndDoesNotChangeActiveSource() {
        int kind = SourceLibrary.LIVE;
        Hawk.put(SourceLibrary.key(kind), "[]");
        String old = "https://example.test/old.m3u", target = "https://example.test/new.m3u";
        Hawk.put(HawkConfig.LIVE_URL, old);
        Hawk.put(HawkConfig.LIVE_HISTORY, new ArrayList<>(Arrays.asList(old)));
        SourceLibrary.save(kind, "原地址", old); SourceLibrary.save(kind, "重复目标", target);
        SourceLibrary.save(kind, "备用", "https://example.test/backup.m3u");
        SourceLibrary.replace(kind, old, "更新后", target);
        assertEquals(2, SourceLibrary.list(kind).size());
        assertEquals(target, SourceLibrary.list(kind).get(0).url);
        assertEquals("更新后", SourceLibrary.list(kind).get(0).name);
        assertEquals(old, Hawk.get(HawkConfig.LIVE_URL));
        assertFalse(Hawk.<ArrayList<String>>get(HawkConfig.LIVE_HISTORY).contains(old));
    }
}
