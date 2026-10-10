package com.kukuqi.tvbox.osc;

import android.app.Instrumentation.ActivityMonitor;
import android.content.Intent;
import android.graphics.Bitmap;
import android.test.InstrumentationTestCase;
import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.ui.activity.MainActivity;
import com.kukuqi.tvbox.osc.ui.activity.SettingActivity;
import com.kukuqi.tvbox.osc.ui.activity.SubscriptionActivity;
import com.kukuqi.tvbox.osc.ui.dialog.SourcePickerDialog;
import com.lxj.xpopup.XPopup;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.orhanobut.hawk.Hawk;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;

/** The removed tab must still have a working Settings route without clearing the selected source. */
public class SourceNavigationRegressionTest extends InstrumentationTestCase {
    public void testFourTabsAndSettingsSourceRouteRetainSelection() throws Exception {
        String before = Hawk.get(HawkConfig.API_URL, "");
        onMain(() -> App.getInstance().isNormalStart = true);
        MainActivity main = (MainActivity) getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SubscriptionActivity source = null;
        ActivityMonitor monitor = getInstrumentation().addMonitor(SubscriptionActivity.class.getName(), null, false);
        try {
            onMain(() -> {
                BottomNavigationView navigation = main.findViewById(R.id.bottom_nav);
                assertEquals(4, navigation.getMenu().size());
                assertNotNull(navigation.getMenu().findItem(R.id.navigation_live));
                assertNotNull(navigation.getMenu().findItem(R.id.navigation_local));
                navigation.setSelectedItemId(R.id.navigation_dashboard);
            });
            for (int i = 0; i < 50 && main.findViewById(R.id.my_scroll) == null; i++) Thread.sleep(100);
            getInstrumentation().waitForIdleSync();
            onMain(() -> {
                assertNotNull("我的页面应直接显示设置", main.findViewById(R.id.llVodApi));
                assertNotNull(main.findViewById(R.id.llAbout));
                assertEquals("点播来源应保留", com.kukuqi.tvbox.osc.util.SourceLibrary.defaultName(before),
                        ((android.widget.TextView) main.findViewById(R.id.tvVodApi)).getText().toString());
            });
            Thread.sleep(400);
            capture("tvboxosc-my-settings.png");
            onMain(() -> main.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            waitUntil(() -> main.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT);
            Thread.sleep(400);
            capture("tvboxosc-my-portrait.png");
            onMain(() -> ((android.widget.ScrollView) main.findViewById(R.id.my_scroll)).scrollBy(0,
                    main.findViewById(R.id.llAbout).getHeight()));
            Thread.sleep(300);
            capture("tvboxosc-my-sources.png");
            onMain(() -> main.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            waitUntil(() -> main.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
            Thread.sleep(400);
            capture("tvboxosc-my-landscape.png");
            MainActivity current = main;
            for (int kind : new int[]{SourcePickerDialog.LIVE, SourcePickerDialog.WALLPAPER}) {
                SourcePickerDialog picker = new SourcePickerDialog(current, kind, () -> {});
                onMain(() -> new XPopup.Builder(current).asCustom(picker).show());
                boolean[] ready = new boolean[1];
                for (int i = 0; i < 50 && !ready[0]; i++) {
                    onMain(() -> ready[0] = picker.findViewById(R.id.source_current_url) != null);
                    Thread.sleep(100);
                }
                assertTrue(ready[0]);
                Thread.sleep(400);
                getInstrumentation().waitForIdleSync();
                capture(kind == SourcePickerDialog.LIVE ? "independent-source-live.png" : "independent-source-wallpaper.png");
                onMain(picker::dismiss);
                Thread.sleep(350);
            }
            onMain(() -> current.findViewById(R.id.llVodApi).performClick());
            source = (SubscriptionActivity) getInstrumentation().waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull("设置应打开点播源管理", source);
            SubscriptionActivity page = source;
            onMain(() -> {
                assertNotNull(page.findViewById(R.id.rv));
                assertNull(page.findViewById(R.id.bottom_nav));
            });
            getInstrumentation().waitForIdleSync();
            capture("source-navigation-vod.png");
            onMain(page::finish);
            getInstrumentation().waitForIdleSync();
            assertEquals("只查看来源不能清空或更换选择", before, Hawk.get(HawkConfig.API_URL, ""));
        } finally {
            getInstrumentation().removeMonitor(monitor);
            SubscriptionActivity page = source;
            onMain(() -> { if (page != null && !page.isFinishing()) page.finish(); main.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED); main.finish(); });
        }
    }
    private void capture(String name) throws Exception {
        Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream output = new FileOutputStream(new File(getInstrumentation().getTargetContext().getExternalFilesDir(null), name))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally { screenshot.recycle(); }
    }
    private void onMain(Runnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    public void testLocalVideoTabScansFoldersAndOpensVideoList() throws Exception {
        java.util.concurrent.CountDownLatch scanned = new java.util.concurrent.CountDownLatch(1);
        android.media.MediaScannerConnection.scanFile(getInstrumentation().getTargetContext(),
                new String[]{"/sdcard/Download/tvbox-regression.mp4"}, new String[]{"video/mp4"}, (path, uri) -> scanned.countDown());
        assertTrue(scanned.await(10, java.util.concurrent.TimeUnit.SECONDS));
        onMain(() -> App.getInstance().isNormalStart = true);
        MainActivity main = (MainActivity) getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_START_DESTINATION, R.id.navigation_local));
        ActivityMonitor monitor = getInstrumentation().addMonitor(com.kukuqi.tvbox.osc.ui.activity.VideoListActivity.class.getName(), null, false);
        android.app.Activity videos = null;
        try {
            waitUntil(() -> main.findViewById(R.id.local_folders) != null
                    && ((androidx.recyclerview.widget.RecyclerView) main.findViewById(R.id.local_folders)).getAdapter().getItemCount() > 0);
            onMain(() -> {
                BottomNavigationView navigation = main.findViewById(R.id.bottom_nav);
                assertEquals(R.id.navigation_local, navigation.getSelectedItemId());
                assertTrue(navigation.isShown());
                assertTrue(main.findViewById(R.id.local_folders).isShown());
            });
            Thread.sleep(400); capture("tvboxosc-local-tab.png");
            waitUntil(() -> ((androidx.recyclerview.widget.RecyclerView) main.findViewById(R.id.local_folders)).findViewHolderForAdapterPosition(0) != null);
            onMain(() -> ((androidx.recyclerview.widget.RecyclerView) main.findViewById(R.id.local_folders)).findViewHolderForAdapterPosition(0).itemView.performClick());
            videos = getInstrumentation().waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull("本地标签页应打开文件夹的视频列表", videos);
            android.app.Activity page = videos;
            onMain(page::finish);
            onMain(() -> ((BottomNavigationView) main.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.navigation_dashboard));
            waitUntil(() -> main.findViewById(R.id.llAbout) != null && main.findViewById(R.id.llAbout).isShown());
            onMain(() -> assertTrue(main.findViewById(R.id.llVodApi).isShown()));
        } finally {
            getInstrumentation().removeMonitor(monitor);
            android.app.Activity page = videos;
            onMain(() -> { if (page != null && !page.isFinishing()) page.finish(); main.finish(); });
        }
    }
    private void waitUntil(java.util.function.BooleanSupplier check) throws Exception {
        boolean[] done = {false};
        for (int i = 0; i < 50; i++) { onMain(() -> done[0] = check.getAsBoolean()); if (done[0]) return; Thread.sleep(100); }
        fail("Rotated settings did not update");
    }
}
