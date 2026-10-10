package com.kukuqi.tvbox.osc;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.test.InstrumentationTestCase;
import com.kukuqi.tvbox.osc.util.LiveSourceLoader;
import com.kukuqi.tvbox.osc.util.SourceDescriptor;
import com.kukuqi.tvbox.osc.util.SourceManager;
import com.kukuqi.tvbox.osc.util.WallpaperManager;
import com.google.gson.JsonArray;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SourceFlowRegressionTest extends InstrumentationTestCase {
    public void testWallpaperCanUseCombinedSubscriptionWithRelativeImageAddress() throws Exception {
        try (Server server = new Server()) {
            CountDownLatch done = new CountDownLatch(1); File[] image = new File[1];
            getInstrumentation().runOnMainSync(() -> WallpaperManager.get().prepare(server.url("combo.json"), file -> { image[0] = file; done.countDown(); }));
            assertTrue(done.await(15, TimeUnit.SECONDS)); assertNotNull(image[0]); assertTrue(image[0].length() > 0);
            SourceDescriptor descriptor = SourceManager.cached(server.url("combo.json"));
            assertNotNull(descriptor); assertTrue(descriptor.hasVod()); assertTrue(descriptor.hasLive()); assertFalse(descriptor.wallpapers().isEmpty());
            image[0].delete();
        }
    }
    public void testCombinedSubscriptionResolvesItsLivePlaylist() throws Exception {
        try (Server server = new Server()) {
            CountDownLatch done = new CountDownLatch(1); JsonArray[] groups = new JsonArray[1];
            getInstrumentation().runOnMainSync(() -> LiveSourceLoader.load(server.url("combo.json"), (ready, error) -> { groups[0] = ready; done.countDown(); }));
            assertTrue(done.await(15, TimeUnit.SECONDS)); assertNotNull(groups[0]); assertEquals(1, groups[0].size());
            assertEquals("示例频道", groups[0].get(0).getAsJsonObject().getAsJsonArray("channels").get(0).getAsJsonObject().get("name").getAsString());
            assertEquals(server.url("epg.xml"), LiveSourceLoader.epg(groups[0]));
        }
    }
    public void testVodOnlySourceDoesNotDisableSeparateLivePlaylist() throws Exception {
        try (Server server = new Server()) {
            CountDownLatch done = new CountDownLatch(2); JsonArray[] result = new JsonArray[2]; String[] errors = new String[2];
            getInstrumentation().runOnMainSync(() -> {
                LiveSourceLoader.load(server.url("vod.json"), (ready, error) -> { result[0] = ready; errors[0] = error; done.countDown(); });
                LiveSourceLoader.load(server.url("live.m3u"), (ready, error) -> { result[1] = ready; errors[1] = error; done.countDown(); });
            });
            assertTrue(done.await(15, TimeUnit.SECONDS)); assertNull(result[0]); assertTrue(errors[0].contains("未提供直播"));
            assertNotNull(result[1]); assertEquals("", errors[1]);
        }
    }
    public void testLegacyNestedProxyResolvesPlaylist() throws Exception {
        try (Server server = new Server()) {
            CountDownLatch done = new CountDownLatch(1); JsonArray[] groups = new JsonArray[1];
            getInstrumentation().runOnMainSync(() -> LiveSourceLoader.load(server.url("legacy.json"), (ready, error) -> { groups[0] = ready; done.countDown(); }));
            assertTrue(done.await(15, TimeUnit.SECONDS)); assertNotNull(groups[0]);
            String stream = groups[0].get(0).getAsJsonObject().getAsJsonArray("channels").get(0).getAsJsonObject().getAsJsonArray("urls").get(0).getAsString();
            assertEquals(server.url("stream.mp4"), stream);
        }
    }
    private class Server implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0);
        Server() throws Exception {
            Bitmap bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.BLUE);
            ByteArrayOutputStream output = new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle();
            byte[] image = output.toByteArray();
            Thread thread = new Thread(() -> {
                try {
                    while (!socket.isClosed()) try (Socket client = socket.accept()) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                        String first = reader.readLine(); String line; while ((line = reader.readLine()) != null && !line.isEmpty()) {}
                        byte[] data; String type = "application/json";
                        if (first.contains("picture.png")) { data = image; type = "image/png"; }
                        else if (first.contains("live.m3u")) {
                            type = "text/plain"; data = ("#EXTM3U\n#EXTINF:-1 group-title=\"测试\",示例频道\n" + url("stream.mp4") + "\n").getBytes("UTF-8");
                        } else if (first.contains("legacy.json")) data = ("{\"lives\":[{\"group\":\"redirect\",\"channels\":[{\"name\":\"placeholder\",\"urls\":[\"proxy://do=live&type=txt&ext=" + android.util.Base64.encodeToString(url("live.m3u").getBytes("UTF-8"), android.util.Base64.NO_WRAP) + "\"]}]}]}").getBytes("UTF-8");
                        else if (first.contains("vod.json")) data = "{\"sites\":[{\"key\":\"test\"}]}".getBytes("UTF-8");
                        else data = "{\"sites\":[{\"key\":\"test\"}],\"lives\":[{\"type\":0,\"url\":\"live.m3u\",\"epg\":\"epg.xml\"}],\"wallpaper\":\"picture.png\"}".getBytes("UTF-8");
                        client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nContent-Length: " + data.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                        client.getOutputStream().write(data); client.getOutputStream().flush();
                    }
                } catch (Exception ignored) {}
            }); thread.setDaemon(true); thread.start();
        }
        String url(String path) { return "http://127.0.0.1:" + socket.getLocalPort() + "/" + path; }
        @Override public void close() throws Exception { socket.close(); }
    }
}
