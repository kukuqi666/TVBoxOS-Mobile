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
    private static final String MANIFEST = "{\"version\":\"3.0.1\",\"version_code\":301,\"package_name\":\"com.kukuqi.tvbox.osc\","
            + "\"apk_url\":\"https://github.com/kukuqi666/TVboxOSC/releases/download/v3.0.1/TVboxOSC-v3.0.1.apk\","
            + "\"size\":3,\"sha256\":\"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad\"}";

    @Override protected void setUp() throws Exception {
        super.setUp();
        store = new UpdateStore(getInstrumentation().getTargetContext());
        original = store.prefs.getAll();
        store.prefs.edit().clear().putBoolean("auto_download", false).commit();
        executor = Executors.newSingleThreadExecutor();
        FakeWorker.bodies.clear(); FakeWorker.urls.clear();
    }
    @Override protected void tearDown() throws Exception {
        java.io.File partial = new java.io.File(getInstrumentation().getTargetContext().getFilesDir(), "updates/TVboxOSC-v3.0.1.part.apk");
        partial.delete();
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
    public void testManifestFallbackPersistsAcrossReopen() {
        FakeWorker.bodies.add("<html>unavailable</html>"); FakeWorker.bodies.add(MANIFEST);
        assertEquals(androidx.work.ListenableWorker.Result.success(), worker(false).doWork());
        UpdateStore reopened = new UpdateStore(getInstrumentation().getTargetContext());
        assertEquals(UpdateStore.AVAILABLE, reopened.state());
        assertEquals(301L, reopened.manifest().versionCode);
        assertEquals(2, FakeWorker.urls.size());
        assertTrue(FakeWorker.urls.get(0).startsWith("https://raw.githubusercontent.com/"));
        assertTrue(FakeWorker.urls.get(1).startsWith("https://gh.xxooo.cf/"));
    }
    public void testEqualVersionDoesNotDownload() {
        FakeWorker.bodies.add(MANIFEST.replace("3.0.1", "3.0.0").replace("301", "300"));
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
        FakeWorker.bodies.add("abd"); FakeWorker.bodies.add("abd"); FakeWorker.bodies.add("abd");
        worker(true).doWork();
        assertEquals(UpdateStore.ERROR, store.state());
        assertFalse(store.hasReadyUpdate());
        assertFalse(store.apk(store.manifest()).exists());
        assertEquals(3, FakeWorker.urls.size());
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
        com.kukuqi.tvbox.osc.ui.dialog.AboutDialog[] dialog = {null};
        try {
            getInstrumentation().runOnMainSync(() -> {
                dialog[0] = new com.kukuqi.tvbox.osc.ui.dialog.AboutDialog(activity);
                new com.lxj.xpopup.XPopup.Builder(activity).asCustom(dialog[0]).show();
            });
            waitForButton(dialog[0]);
            getInstrumentation().runOnMainSync(() -> {
                android.widget.TextView button = dialog[0].findViewById(R.id.btn_check_update);
                assertFalse(button.isEnabled());
                assertTrue(button.getText().toString().contains("后台下载"));
                dialog[0].dismiss();
            });
            Thread.sleep(350);
            assertEquals(UpdateStore.DOWNLOADING, new UpdateStore(context).state());
            assertEquals(42, store.prefs.getInt("progress", 0));
            getInstrumentation().runOnMainSync(() -> {
                dialog[0] = new com.kukuqi.tvbox.osc.ui.dialog.AboutDialog(activity);
                new com.lxj.xpopup.XPopup.Builder(activity).asCustom(dialog[0]).show();
            });
            waitForButton(dialog[0]);
            getInstrumentation().runOnMainSync(() -> assertTrue(((android.widget.TextView) dialog[0].findViewById(R.id.tv_update_progress))
                    .getText().toString().contains("42%")));
        } finally {
            getInstrumentation().runOnMainSync(() -> { if (dialog[0] != null) dialog[0].dismiss(); activity.finish(); });
        }
    }
    private void waitForButton(com.kukuqi.tvbox.osc.ui.dialog.AboutDialog dialog) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            boolean[] ready = {false};
            getInstrumentation().runOnMainSync(() -> ready[0] = dialog.findViewById(R.id.btn_check_update) != null);
            if (ready[0]) { Thread.sleep(300); return; }
            Thread.sleep(100);
        }
        fail("About panel did not open");
    }

    public static class FakeWorker extends UpdateWorker {
        static final ArrayDeque<String> bodies = new ArrayDeque<>();
        static final ArrayList<String> urls = new ArrayList<>();
        public FakeWorker(Context context, WorkerParameters parameters) { super(context, parameters); }
        @Override protected Response request(String url) throws IOException {
            urls.add(url);
            if (bodies.isEmpty()) throw new IOException("No test response");
            return new Response.Builder().request(new Request.Builder().url(url).build()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(MediaType.get("application/octet-stream"), bodies.remove())).build();
        }
    }
}
