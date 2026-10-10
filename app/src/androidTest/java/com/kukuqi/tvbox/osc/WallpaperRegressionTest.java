package com.kukuqi.tvbox.osc;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.view.View;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.kukuqi.tvbox.osc.util.WallpaperManager;
import com.orhanobut.hawk.Hawk;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Real Android image decoding, preferences, async downloads and cache-independent imports. */
public class WallpaperRegressionTest extends InstrumentationTestCase {
    private String previous;
    private String previousMode;
    private String previousLibrary;
    private boolean hadLibrary;
    private final WallpaperManager manager = WallpaperManager.get();
    @Override protected void setUp() throws Exception {
        super.setUp(); previous = Hawk.get(HawkConfig.WALLPAPER_URL, ""); previousMode = Hawk.get("wallpaper_mode", "");
        String key = com.kukuqi.tvbox.osc.util.SourceLibrary.key(com.kukuqi.tvbox.osc.util.SourceLibrary.WALLPAPER);
        hadLibrary = Hawk.contains(key); previousLibrary = Hawk.get(key, "[]");
    }
    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            Hawk.put(HawkConfig.WALLPAPER_URL, previous);
            if (previousMode.isEmpty()) Hawk.delete("wallpaper_mode"); else Hawk.put("wallpaper_mode", previousMode);
            String key = com.kukuqi.tvbox.osc.util.SourceLibrary.key(com.kukuqi.tvbox.osc.util.SourceLibrary.WALLPAPER);
            if (hadLibrary) Hawk.put(key, previousLibrary); else Hawk.delete(key);
        });
        super.tearDown();
    }
    public void testDefaultRestoresBackgroundAndBuiltinPreferenceUsesName() {
        getInstrumentation().runOnMainSync(() -> {
            View root = new View(getInstrumentation().getTargetContext());
            Drawable original = new ColorDrawable(Color.WHITE); root.setBackground(original);
            manager.saveWallpaper(manager.getBuiltInWallpapers().get(2)); manager.applyToView(root);
            assertTrue(manager.getWallpaperPref().startsWith("drawable://wallpaper_gradient_"));
            assertNotSame(original, root.getBackground());
            manager.clearWallpaper(); manager.applyToView(root);
            assertSame(original, root.getBackground());
        });
    }
    public void testLocalImportSurvivesDeletingItsSourceFromCache() throws Exception {
        File source = new File(getInstrumentation().getTargetContext().getCacheDir(), "wallpaper-regression.png");
        try (FileOutputStream output = new FileOutputStream(source)) { output.write(png()); }
        CountDownLatch done = new CountDownLatch(1); String[] imported = new String[1];
        getInstrumentation().runOnMainSync(() -> manager.importWallpaper(Uri.fromFile(source), value -> { imported[0] = value; done.countDown(); }));
        assertTrue(done.await(15, TimeUnit.SECONDS));
        assertTrue(source.delete());
        assertNotNull(imported[0]); assertFalse(imported[0].isEmpty());
        File stored = new File(Uri.parse(imported[0]).getPath());
        assertTrue(stored.getCanonicalPath().startsWith(getInstrumentation().getTargetContext().getFilesDir().getCanonicalPath() + File.separator));
        assertTrue(stored.isFile());
        assertTrue(com.kukuqi.tvbox.osc.util.SourceLibrary.list(com.kukuqi.tvbox.osc.util.SourceLibrary.WALLPAPER)
                .stream().anyMatch(entry -> entry.url.equals(imported[0])));
        stored.delete();
    }
    public void testRandomPreviewAndApplicationShareOneDownload() throws Exception {
        try (ImageServer server = new ImageServer(150, true)) {
            CountDownLatch done = new CountDownLatch(2); File[] results = new File[2];
            getInstrumentation().runOnMainSync(() -> {
                manager.prepare(server.url(), file -> { results[0] = file; done.countDown(); });
                manager.prepare(server.url(), file -> { results[1] = file; done.countDown(); });
            });
            assertTrue(done.await(15, TimeUnit.SECONDS));
            assertNotNull(results[0]); assertEquals(results[0], results[1]); assertEquals(1, server.requests.get());
            results[0].delete();
        }
    }
    public void testSlowDownloadCannotRestoreWallpaperAfterClearing() throws Exception {
        try (ImageServer server = new ImageServer(250, true)) {
            CountDownLatch done = new CountDownLatch(1); View[] root = new View[1];
            Drawable original = new ColorDrawable(Color.WHITE);
            getInstrumentation().runOnMainSync(() -> {
                root[0] = new View(getInstrumentation().getTargetContext()); root[0].setBackground(original);
                Hawk.put("wallpaper_mode", "custom"); Hawk.put(HawkConfig.WALLPAPER_URL, server.url()); manager.applyToView(root[0]);
                manager.clearWallpaper(); manager.applyToView(root[0]);
                manager.prepare(server.url(), file -> done.countDown());
            });
            assertTrue(done.await(15, TimeUnit.SECONDS)); getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(() -> assertSame(original, root[0].getBackground()));
            manager.getCachedFile(server.url()).delete();
        }
    }
    public void testInvalidImageIsRejectedWithoutChangingPreference() throws Exception {
        try (ImageServer server = new ImageServer(0, false)) {
            CountDownLatch done = new CountDownLatch(1); File[] result = new File[1];
            getInstrumentation().runOnMainSync(() -> manager.prepare(server.url(), file -> { result[0] = file; done.countDown(); }));
            assertTrue(done.await(15, TimeUnit.SECONDS)); assertNull(result[0]); assertFalse(manager.isCached(server.url()));
            assertEquals(previous, Hawk.get(HawkConfig.WALLPAPER_URL, ""));
        }
    }
    public void testFailedRefreshRestoresWhiteBackgroundAndCanRetry() throws Exception {
        try (ImageServer server = new ImageServer(0, false)) {
            File cached = manager.getCachedFile(server.url());
            try (FileOutputStream output = new FileOutputStream(cached)) { output.write(png()); }
            CountDownLatch failed = new CountDownLatch(1);
            Drawable white = new ColorDrawable(Color.WHITE);
            View[] root = new View[1];
            getInstrumentation().runOnMainSync(() -> {
                root[0] = new View(getInstrumentation().getTargetContext()); root[0].setBackground(white);
                Hawk.put("wallpaper_mode", "custom"); Hawk.put(HawkConfig.WALLPAPER_URL, server.url());
                manager.refresh(file -> { assertNull(file); manager.applyToView(root[0]); failed.countDown(); });
            });
            assertTrue(failed.await(15, TimeUnit.SECONDS));
            getInstrumentation().runOnMainSync(() -> assertSame(white, root[0].getBackground()));
            CountDownLatch retried = new CountDownLatch(1);
            getInstrumentation().runOnMainSync(() -> manager.refresh(file -> retried.countDown()));
            assertTrue(retried.await(15, TimeUnit.SECONDS)); assertEquals(2, server.requests.get());
            cached.delete();
        }
    }
    public void testStalledDownloadReturnsFailureWithinTenSeconds() throws Exception {
        try (ImageServer server = new ImageServer(12000, true)) {
            CountDownLatch done = new CountDownLatch(1); File[] result = new File[1];
            getInstrumentation().runOnMainSync(() -> manager.prepare(server.url(), file -> { result[0] = file; done.countDown(); }));
            assertTrue("壁纸请求不能一直等待", done.await(11, TimeUnit.SECONDS));
            assertNull(result[0]);
        }
    }
    private byte[] png() {
        Bitmap bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.BLUE);
        ByteArrayOutputStream output = new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle(); return output.toByteArray();
    }
    private class ImageServer implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0); final AtomicInteger requests = new AtomicInteger();
        ImageServer(int delay, boolean valid) throws Exception {
            byte[] data = valid ? png() : "not an image".getBytes("UTF-8");
            Thread thread = new Thread(() -> {
                try {
                    while (!socket.isClosed()) try (Socket client = socket.accept()) {
                        requests.incrementAndGet();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                        String line; while ((line = reader.readLine()) != null && !line.isEmpty()) {}
                        Thread.sleep(delay);
                        client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: " + data.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                        client.getOutputStream().write(data); client.getOutputStream().flush();
                    }
                } catch (Exception ignored) {}
            }); thread.setDaemon(true); thread.start();
        }
        String url() { return "http://127.0.0.1:" + socket.getLocalPort() + "/random.png"; }
        @Override public void close() throws Exception { socket.close(); }
    }
}
