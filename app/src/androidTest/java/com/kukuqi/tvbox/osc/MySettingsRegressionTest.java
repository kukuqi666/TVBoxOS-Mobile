package com.kukuqi.tvbox.osc;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.widget.ScrollView;
import android.widget.TextView;
import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.ui.activity.MainActivity;
import com.kukuqi.tvbox.osc.ui.fragment.MyFragment;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.kukuqi.tvbox.osc.util.SourceLibrary;
import com.kukuqi.tvbox.osc.util.WallpaperManager;
import com.orhanobut.hawk.Hawk;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/** Verify document results reach inline settings and survive leaving the page. */
public class MySettingsRegressionTest extends InstrumentationTestCase {
    public void testInlineImportsAndPrivacyPersistAfterReopen() throws Exception {
        String[] keys = {HawkConfig.LIVE_URL, HawkConfig.EPG_URL, HawkConfig.WALLPAPER_URL,
                "wallpaper_mode", HawkConfig.WALLPAPER_HISTORY, SourceLibrary.key(SourceLibrary.LIVE),
                SourceLibrary.key(SourceLibrary.WALLPAPER), HawkConfig.PRIVATE_BROWSING};
        Map<String, Object> before = new HashMap<>();
        for (String key : keys) if (Hawk.contains(key)) before.put(key, Hawk.get(key));
        File image = new File(getInstrumentation().getTargetContext().getCacheDir(), "my-import.png");
        File playlist = new File(getInstrumentation().getTargetContext().getCacheDir(), "my-import.m3u");
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(android.graphics.Color.BLUE);
        try (FileOutputStream output = new FileOutputStream(image)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
        bitmap.recycle();
        Files.write(playlist.toPath(), "#EXTM3U\n#EXTINF:-1 group-title=\"测试\",导入频道\nhttp://127.0.0.1/fixture.mp4\n".getBytes(StandardCharsets.UTF_8));
        MainActivity main = null;
        String[] importedImage = {null};
        try {
            main = openMy(); MainActivity current = main;
            MyFragment[] fragment = new MyFragment[1];
            onMain(() -> fragment[0] = (MyFragment) current.getSupportFragmentManager().findFragmentByTag("f2"));
            assertNotNull(fragment[0]);
            onMain(() -> fragment[0].onActivityResult(9202, Activity.RESULT_OK, new Intent().setData(Uri.fromFile(image))));
            waitUntil(() -> WallpaperManager.get().getWallpaperPref().startsWith("file://"));
            importedImage[0] = WallpaperManager.get().getWallpaperPref();
            assertTrue(new File(Uri.parse(importedImage[0]).getPath()).isFile());
            onMain(() -> fragment[0].onActivityResult(9203, Activity.RESULT_OK, new Intent().setData(Uri.fromFile(playlist))));
            waitUntil(() -> Uri.fromFile(playlist).toString().equals(Hawk.get(HawkConfig.LIVE_URL, "")));
            onMain(() -> {
                assertEquals("本地直播", ((TextView) current.findViewById(R.id.tvLiveApi)).getText().toString());
                assertEquals("本地图片", ((TextView) current.findViewById(R.id.tvWallpaper)).getText().toString());
                current.findViewById(R.id.llPrivateBrowsing).performClick();
            });
            boolean privacy = Hawk.get(HawkConfig.PRIVATE_BROWSING, false);
            onMain(current::finish);
            main = openMy(); MainActivity reopened = main;
            onMain(() -> {
                assertEquals("本地直播", ((TextView) reopened.findViewById(R.id.tvLiveApi)).getText().toString());
                assertEquals("本地图片", ((TextView) reopened.findViewById(R.id.tvWallpaper)).getText().toString());
                assertEquals(privacy, Hawk.get(HawkConfig.PRIVATE_BROWSING, false).booleanValue());
                ((ScrollView) reopened.findViewById(R.id.my_scroll)).fullScroll(android.view.View.FOCUS_DOWN);
            });
            assertTrue(SourceLibrary.list(SourceLibrary.LIVE).stream().anyMatch(item -> item.url.equals(Uri.fromFile(playlist).toString())));
        } finally {
            MainActivity current = main;
            onMain(() -> {
                if (current != null) current.finish();
                for (String key : keys) { if (before.containsKey(key)) Hawk.put(key, before.get(key)); else Hawk.delete(key); }
                org.greenrobot.eventbus.EventBus.getDefault().post(new com.kukuqi.tvbox.osc.event.WallpaperChangedEvent());
            });
            image.delete(); playlist.delete();
            if (importedImage[0] != null) new File(Uri.parse(importedImage[0]).getPath()).delete();
        }
    }
    private MainActivity openMy() throws Exception {
        onMain(() -> App.getInstance().isNormalStart = true);
        MainActivity main = (MainActivity) getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                .putExtra(MainActivity.EXTRA_START_DESTINATION, R.id.navigation_dashboard).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitUntil(() -> main.findViewById(R.id.llVodApi) != null && main.getSupportFragmentManager().findFragmentByTag("f2") != null);
        return main;
    }
    private interface Check { boolean ready(); }
    private void waitUntil(Check check) throws Exception {
        boolean[] done = {false};
        for (int i = 0; i < 100; i++) { onMain(() -> done[0] = check.ready()); if (done[0]) return; Thread.sleep(100); }
        fail("Inline settings did not become ready");
    }
    private void onMain(Runnable action) {
        Throwable[] error = {null};
        getInstrumentation().runOnMainSync(() -> { try { action.run(); } catch (Throwable value) { error[0] = value; } });
        if (error[0] != null) throw new AssertionError(error[0]);
    }
}
