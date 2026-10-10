package com.kukuqi.tvbox.osc;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import androidx.work.Data;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestWorkerBuilder;
import com.kukuqi.tvbox.osc.update.UpdateStore;
import com.kukuqi.tvbox.osc.update.UpdateWorker;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Exercises real worker persistence, mirror fallback and corrupt-download rejection without production network access. */
public class UpdateRegressionTest extends InstrumentationTestCase {
    private UpdateStore store;
    private Map<String, ?> original;
    private ExecutorService executor;
    private static final String MIRROR = "https://gh-proxy.com/";
    private static final String MANIFEST_URL = "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/update.json";
    private static final String APK_URL = "https://github.com/kukuqi666/TVboxOSC/releases/download/v3.0.3/TVboxOSC-v3.0.3.apk";
    private static final String MANIFEST = "{\"version\":\"3.0.3\",\"version_code\":" + (BuildConfig.VERSION_CODE + 1) + ",\"package_name\":\"" + BuildConfig.APPLICATION_ID + "\","
            + "\"apk_url\":\"" + APK_URL + "\","
            + "\"size\":3,\"sha256\":\"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad\"}";

    @Override protected void setUp() throws Exception {
        super.setUp();
        store = new UpdateStore(getInstrumentation().getTargetContext());
        original = store.prefs.getAll();
        store.prefs.edit().clear().putBoolean("auto_download", false).commit();
        executor = Executors.newSingleThreadExecutor();
        FakeWorker.bodies.clear(); FakeWorker.urls.clear(); FakeWorker.networkFailures = 0;
    }
    @Override protected void tearDown() throws Exception {
        SharedPreferences.Editor editor = store.prefs.edit().clear();
        for (Map.Entry<String, ?> entry : original.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
        }
        editor.commit(); executor.shutdownNow(); super.tearDown();
    }
    private FakeWorker worker(boolean download) {
        return TestWorkerBuilder.from(getInstrumentation().getTargetContext(), FakeWorker.class, executor)
                .setInputData(new Data.Builder().putBoolean("download", download).putBoolean("manual", true).build()).build();
    }
    public void testManifestUsesMirrorFirstWithoutDirectRequestOnSuccess() {
        FakeWorker.bodies.add(MANIFEST);
        assertEquals(androidx.work.ListenableWorker.Result.success(), worker(false).doWork());
        assertEquals(UpdateStore.AVAILABLE, store.state());
        assertEquals(1, FakeWorker.urls.size());
        assertTrue(FakeWorker.urls.get(0).startsWith(MIRROR + MANIFEST_URL + "?t="));
    }
    public void testManifestFallbackPersistsAcrossReopen() {
        FakeWorker.bodies.add("<html>unavailable</html>"); FakeWorker.bodies.add(MANIFEST);
        assertEquals(androidx.work.ListenableWorker.Result.success(), worker(false).doWork());
        UpdateStore reopened = new UpdateStore(getInstrumentation().getTargetContext());
        assertEquals(UpdateStore.AVAILABLE, reopened.state());
        assertEquals((long) BuildConfig.VERSION_CODE + 1, reopened.manifest().versionCode);
        assertEquals(2, FakeWorker.urls.size());
        assertTrue(FakeWorker.urls.get(0).startsWith(MIRROR + MANIFEST_URL + "?t="));
        assertTrue(FakeWorker.urls.get(1).startsWith(MANIFEST_URL + "?t="));
    }
    public void testManifestNetworkFailureFallsBackToOfficial() {
        FakeWorker.networkFailures = 1;
        FakeWorker.bodies.add(MANIFEST);
        assertEquals(androidx.work.ListenableWorker.Result.success(), worker(false).doWork());
        assertEquals(UpdateStore.AVAILABLE, store.state());
        assertEquals(2, FakeWorker.urls.size());
        assertTrue(FakeWorker.urls.get(0).startsWith(MIRROR + MANIFEST_URL + "?t="));
        assertTrue(FakeWorker.urls.get(1).startsWith(MANIFEST_URL + "?t="));
    }
    public void testEqualVersionDoesNotDownload() {
        FakeWorker.bodies.add(MANIFEST.replace("3.0.3", BuildConfig.VERSION_NAME).replace("\"version_code\":" + (BuildConfig.VERSION_CODE + 1), "\"version_code\":" + BuildConfig.VERSION_CODE));
        assertEquals(androidx.work.ListenableWorker.Result.success(), worker(false).doWork());
        assertEquals(UpdateStore.CURRENT, store.state());
        assertFalse(store.hasReadyUpdate());
    }
    public void testOldOnlineManifestDoesNotShowAnException() {
        FakeWorker.bodies.add("{\"version\":\"2.1.26\",\"apk_url\":\"https://example.com/old.apk\"}");
        assertEquals(androidx.work.ListenableWorker.Result.success(), worker(false).doWork());
        assertEquals(UpdateStore.CURRENT, store.state());
        assertEquals(1, FakeWorker.urls.size());
    }
    public void testCorruptDownloadNeverBecomesInstallable() {
        store.prefs.edit().putString("manifest", MANIFEST).commit();
        FakeWorker.bodies.add("abd"); FakeWorker.bodies.add("abd");
        worker(true).doWork();
        assertEquals(UpdateStore.ERROR, store.state());
        assertFalse(store.hasReadyUpdate());
        assertFalse(store.apk(store.manifest()).exists());
        assertEquals(2, FakeWorker.urls.size());
        assertEquals(MIRROR + APK_URL, FakeWorker.urls.get(0));
        assertEquals(APK_URL, FakeWorker.urls.get(1));
    }
    public void testDownloadNetworkFailureFallsBackToOfficial() {
        store.prefs.edit().putString("manifest", MANIFEST).commit();
        FakeWorker.networkFailures = 1;
        FakeWorker.bodies.add("abd");
        worker(true).doWork();
        assertEquals(2, FakeWorker.urls.size());
        assertEquals(MIRROR + APK_URL, FakeWorker.urls.get(0));
        assertEquals(APK_URL, FakeWorker.urls.get(1));
        assertFalse(store.hasReadyUpdate());
    }
    public void testInvalidMirrorArchiveFallsBackToOfficial() {
        store.prefs.edit().putString("manifest", MANIFEST).commit();
        FakeWorker.bodies.add("abc"); // Correct checksum, but not an APK for this app.
        FakeWorker.bodies.add("abd");
        worker(true).doWork();
        assertEquals(2, FakeWorker.urls.size());
        assertEquals(MIRROR + APK_URL, FakeWorker.urls.get(0));
        assertEquals(APK_URL, FakeWorker.urls.get(1));
        assertFalse(store.hasReadyUpdate());
    }

    public void testAboutKeepsBackgroundDownloadStateWhenClosed() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        store.prefs.edit().putString("manifest", MANIFEST).commit();
        store.state(UpdateStore.DOWNLOADING, "正在后台下载 v3.0.1 · 42%", 42);
        getInstrumentation().runOnMainSync(() -> com.kukuqi.tvbox.osc.base.App.getInstance().isNormalStart = true);
        com.kukuqi.tvbox.osc.ui.activity.MainActivity activity =
                (com.kukuqi.tvbox.osc.ui.activity.MainActivity) getInstrumentation().startActivitySync(
                        new android.content.Intent(context, com.kukuqi.tvbox.osc.ui.activity.MainActivity.class)
                                .putExtra(com.kukuqi.tvbox.osc.ui.activity.MainActivity.EXTRA_START_DESTINATION, R.id.navigation_dashboard)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        com.kukuqi.tvbox.osc.ui.activity.AboutActivity[] dialog = {null};
        try {
            dialog[0] = openAbout(context);
            waitForButton(dialog[0]);
            getInstrumentation().runOnMainSync(() -> {
                android.widget.TextView button = dialog[0].findViewById(R.id.btn_check_update);
                assertFalse(button.isEnabled());
                assertTrue(button.getText().toString().contains("后台下载"));
                dialog[0].finish();
            });
            Thread.sleep(350);
            assertEquals(UpdateStore.DOWNLOADING, new UpdateStore(context).state());
            assertEquals(42, store.prefs.getInt("progress", 0));
            dialog[0] = openAbout(context);
            waitForButton(dialog[0]);
            getInstrumentation().runOnMainSync(() -> assertTrue(((android.widget.TextView) dialog[0].findViewById(R.id.tv_update_progress))
                    .getText().toString().contains("42%")));
        } finally {
            getInstrumentation().runOnMainSync(() -> { if (dialog[0] != null) dialog[0].finish(); activity.finish(); });
        }
    }
    private com.kukuqi.tvbox.osc.ui.activity.AboutActivity openAbout(Context context) {
        return (com.kukuqi.tvbox.osc.ui.activity.AboutActivity)getInstrumentation().startActivitySync(
            new android.content.Intent(context,com.kukuqi.tvbox.osc.ui.activity.AboutActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private void waitForButton(com.kukuqi.tvbox.osc.ui.activity.AboutActivity dialog) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            boolean[] ready = {false};
            getInstrumentation().runOnMainSync(() -> ready[0] = dialog.findViewById(R.id.btn_check_update) != null);
            if (ready[0]) { Thread.sleep(300); return; }
            Thread.sleep(100);
        }
        fail("About panel did not open");
    }
    public void testNotificationOpensUpdatePanelOnColdStart() throws Exception {
        com.kukuqi.tvbox.osc.base.App app = com.kukuqi.tvbox.osc.base.App.getInstance();
        boolean originalStart = app.isNormalStart;
        getInstrumentation().runOnMainSync(() -> app.isNormalStart = false);
        android.app.Activity activity = getInstrumentation().startActivitySync(
                new android.content.Intent(getInstrumentation().getTargetContext(), com.kukuqi.tvbox.osc.ui.activity.MainActivity.class)
                        .putExtra(com.kukuqi.tvbox.osc.update.UpdateCoordinator.OPEN_UPDATES, true)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            assertTrue(app.isNormalStart);
            boolean found = false;
            for (int attempt = 0; attempt < 50; attempt++) {
                android.view.accessibility.AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
                if (root != null && !root.findAccessibilityNodeInfosByText("Wi-Fi 后台更新").isEmpty()) { found = true; break; }
                Thread.sleep(100);
            }
            assertTrue("Update notification must open About even in a fresh app process", found);
        } finally {
            getInstrumentation().getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            getInstrumentation().runOnMainSync(() -> { activity.finish(); app.isNormalStart = originalStart; });
        }
    }

    public static class FakeWorker extends UpdateWorker {
        static final ArrayDeque<String> bodies = new ArrayDeque<>();
        static final ArrayList<String> urls = new ArrayList<>();
        static int networkFailures;
        public FakeWorker(Context context, WorkerParameters parameters) { super(context, parameters); }
        @Override protected Response request(String url) throws IOException {
            urls.add(url);
            if (networkFailures > 0) { networkFailures--; throw new IOException("Test network failure"); }
            if (bodies.isEmpty()) throw new IOException("No test response");
            return new Response.Builder().request(new Request.Builder().url(url).build()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(MediaType.get("application/octet-stream"), bodies.remove())).build();
        }
    }
}
