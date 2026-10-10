package com.kukuqi.tvbox.osc;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.test.InstrumentationTestCase;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.bean.LiveChannelItem;
import com.kukuqi.tvbox.osc.player.MyVideoView;
import com.kukuqi.tvbox.osc.ui.activity.LiveActivity;
import com.kukuqi.tvbox.osc.ui.dialog.EpgGuideDialog;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.kukuqi.tvbox.osc.util.epg.EpgSchedule;
import com.kukuqi.tvbox.osc.util.epg.EpgService;
import com.orhanobut.hawk.Hawk;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

public class EpgFeaturesRegressionTest extends InstrumentationTestCase {
    private static final String[] KEYS = {HawkConfig.LIVE_URL, HawkConfig.LIVE_CHANNEL, HawkConfig.EPG_URL, EpgService.CUSTOM};
    private File backup() { return new File(getInstrumentation().getTargetContext().getFilesDir(), "epg-regression-state.json"); }
    @Override protected void setUp() throws Exception {
        super.setUp(); restore();
        JSONObject state = new JSONObject();
        for (String key : KEYS) state.put(key, Hawk.contains(key) ? Hawk.get(key) : JSONObject.NULL);
        Files.write(backup().toPath(), state.toString().getBytes(StandardCharsets.UTF_8));
    }
    @Override protected void tearDown() throws Exception { try { restore(); } finally { super.tearDown(); } }
    private void restore() throws Exception {
        if (!backup().isFile()) return;
        JSONObject state = new JSONObject(new String(Files.readAllBytes(backup().toPath()), StandardCharsets.UTF_8));
        for (String key : KEYS) { if (state.isNull(key)) Hawk.delete(key); else Hawk.put(key, state.getString(key)); }
        backup().delete();
    }
    private LiveChannelItem channel() {
        LiveChannelItem item = new LiveChannelItem(); item.setChannelName("新闻 台"); item.setTvgId("news-1"); item.setTvgName("新闻 高清"); return item;
    }
    public void testJsonHttpCacheRefreshAndCancellation() throws Exception {
        try (Server server = new Server()) {
            String template = server.url("guide.json?ch={id}&date={date}");
            EpgSchedule first = load(template, channel(), false);
            assertNotNull(first.current(System.currentTimeMillis())); assertEquals(1, server.requests.get());
            assertTrue(server.lastRequest.contains("ch=news-1"));
            load(template, channel(), false); assertEquals("Cached guide must not redownload", 1, server.requests.get());
            load(template, channel(), true); assertEquals(2, server.requests.get());
            CountDownLatch called = new CountDownLatch(1); EpgService.RequestHandle[] handle = new EpgService.RequestHandle[1];
            onMain(() -> { handle[0] = EpgService.load(server.url("slow.json"), channel(), EpgSchedule.today(TimeZone.getDefault()), false, (ready, error) -> called.countDown()); handle[0].cancel(); });
            assertFalse("Cancelled request delivered a callback", called.await(1500, TimeUnit.MILLISECONDS));
            assertEquals("节目接口基础地址应补全查询", server.url("api?ch=%E6%96%B0%E9%97%BB%20%E9%AB%98%E6%B8%85&date=2026-10-09"), EpgService.address(server.url("api"), channel(), "2026-10-09"));
        }
    }
    public void testGzipXmltvParsesOnAndroidWithChannelId() throws Exception {
        try (Server server = new Server()) {
            EpgSchedule guide = load(server.url("guide.xml.gz"), channel(), false);
            assertEquals(1, guide.programmes.size()); assertEquals("XML 当前节目", guide.current(System.currentTimeMillis()).title);
            assertEquals(1, server.requests.get());
        }
    }
    public void testLiveSummaryGuideDatesAndSourceWithoutEpg() throws Exception {
        try (Server server = new Server()) {
            File fixture = new File(getInstrumentation().getTargetContext().getFilesDir(), "epg-live-fixture.json");
            String stream = "file:///sdcard/Download/tvbox-regression.mp4";
            String json = "[{\"group\":\"新闻\",\"epg\":\"" + server.url("guide.json?ch={id}&date={date}")
                    + "\",\"channels\":[{\"name\":\"新闻 台\",\"tvg-id\":\"news-1\",\"urls\":[\"" + stream + "\"]}]},"
                    + "{\"group\":\"地方\",\"channels\":[{\"name\":\"无节目源频道\",\"urls\":[\"" + stream + "\"]}]}]";
            Files.write(fixture.toPath(), json.getBytes(StandardCharsets.UTF_8));
            onMain(() -> { App.getInstance().isNormalStart = true; Hawk.put(HawkConfig.LIVE_URL, "file://" + fixture.getAbsolutePath()); Hawk.put(HawkConfig.LIVE_CHANNEL, ""); Hawk.delete(EpgService.CUSTOM); });
            LiveActivity live = (LiveActivity) getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), LiveActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try {
                waitUntil(() -> ((TextView) live.findViewById(R.id.live_epg_summary)).getText().toString().contains("测试当前节目"));
                onMain(() -> { live.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); ((MyVideoView) live.findViewById(R.id.mVideoView)).pause(); });
                Thread.sleep(700); capture("live-epg-portrait.png");
                onMain(live::showEpgGuide);
                EpgGuideDialog[] guide = new EpgGuideDialog[1];
                onMain(() -> { try { Field field = LiveActivity.class.getDeclaredField("epgDialog"); field.setAccessible(true); guide[0] = (EpgGuideDialog) field.get(live); } catch (Exception e) { throw new RuntimeException(e); } });
                waitUntil(() -> guide[0].findViewById(R.id.epg_programmes) != null && ((RecyclerView) guide[0].findViewById(R.id.epg_programmes)).getAdapter().getItemCount() == 2);
                Thread.sleep(400); capture("epg-guide.png");
                onMain(() -> guide[0].findViewById(R.id.epg_previous).performClick());
                waitUntil(() -> ((TextView) guide[0].findViewById(R.id.epg_status)).getText().toString().contains("共 2"));
                onMain(() -> {
                    assertTrue(((TextView) guide[0].findViewById(R.id.epg_date)).getText().toString().startsWith(EpgSchedule.shift(EpgSchedule.today(TimeZone.getDefault()), -1, TimeZone.getDefault())));
                    guide[0].findViewById(R.id.epg_date).performClick();
                });
                waitUntil(() -> ((TextView) guide[0].findViewById(R.id.epg_date)).getText().toString().contains("今天"));
                onMain(() -> { guide[0].dismiss(); live.playBrowserChannel(live.searchLiveChannels("无节目源", false).get(0)); });
                waitUntil(() -> live.getCurrentLiveChannelItem().getChannelName().equals("无节目源频道"));
                onMain(() -> {
                    assertEquals("", live.epgAddress(live.getCurrentLiveChannelItem()));
                    assertFalse(((TextView) live.findViewById(R.id.live_epg_summary)).getText().toString().contains("测试当前节目"));
                    live.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                });
                Thread.sleep(650); onMain(() -> ((MyVideoView) live.findViewById(R.id.mVideoView)).pause());
                onMain(() -> assertTrue("横屏应留出可用频道列表高度", live.findViewById(R.id.tvLeftChannnelListLayout).getHeight() >= 48 * live.getResources().getDisplayMetrics().density));
                Thread.sleep(400); capture("live-epg-landscape.png");
                onMain(() -> { Hawk.put(EpgService.CUSTOM, "-"); live.refreshEpg(false); assertEquals("节目表已关闭", ((TextView) live.findViewById(R.id.live_epg_summary)).getText().toString()); });
                com.kukuqi.tvbox.osc.ui.dialog.LiveSettingRightDialog[] settingHolder = new com.kukuqi.tvbox.osc.ui.dialog.LiveSettingRightDialog[1];
                onMain(() -> { try {
                    java.lang.reflect.Method show = LiveActivity.class.getDeclaredMethod("showSettingDialog", boolean.class); show.setAccessible(true); show.invoke(live, true);
                    Field field = LiveActivity.class.getDeclaredField("mSettingRightDialog"); field.setAccessible(true);
                    settingHolder[0] = (com.kukuqi.tvbox.osc.ui.dialog.LiveSettingRightDialog) field.get(live);
                } catch (Exception error) { throw new RuntimeException(error); } });
                com.kukuqi.tvbox.osc.ui.dialog.LiveSettingRightDialog settings = settingHolder[0];
                waitUntil(() -> settings.findViewById(R.id.live_setting_epg) != null);
                try {
                    waitUntil(() -> settings.findViewById(R.id.mSettingGroupView).getHeight() >= 60 * live.getResources().getDisplayMetrics().density);
                } catch (AssertionError error) {
                    capture("live-setting-failure.png");
                    onMain(() -> android.util.Log.e("EPG_LAYOUT", "popup=" + settings.getWidth() + "x" + settings.getHeight()
                            + " impl=" + settings.getPopupImplView().getWidth() + "x" + settings.getPopupImplView().getHeight()
                            + " group=" + settings.findViewById(R.id.mSettingGroupView).getHeight()
                            + " header=" + settings.findViewById(R.id.live_setting_epg).getHeight()
                            + " density=" + live.getResources().getDisplayMetrics().density
                            + " decor=" + live.getWindow().getDecorView().getWidth() + "x" + live.getWindow().getDecorView().getHeight()));
                    throw error;
                }
                Thread.sleep(400); capture("live-setting-epg.png");
                onMain(() -> settings.findViewById(R.id.live_setting_epg).performClick());
                waitUntil(() -> !settings.isShow());
                onMain(() -> { try {
                    Field field = LiveActivity.class.getDeclaredField("epgDialog"); field.setAccessible(true);
                    assertTrue(((EpgGuideDialog) field.get(live)).isShow()); live.onBackPressed();
                } catch (Exception error) { throw new RuntimeException(error); } });
            } finally { onMain(() -> { live.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED); live.finish(); }); fixture.delete(); }
        }
    }
    private EpgSchedule load(String url, LiveChannelItem channel, boolean refresh) throws Exception {
        CountDownLatch done = new CountDownLatch(1); EpgSchedule[] result = new EpgSchedule[1]; String[] error = new String[1];
        onMain(() -> EpgService.load(url, channel, EpgSchedule.today(TimeZone.getDefault()), refresh, (ready, message) -> { result[0] = ready; error[0] = message; done.countDown(); }));
        assertTrue(done.await(12, TimeUnit.SECONDS)); assertNotNull(error[0], result[0]); return result[0];
    }
    private interface Check { boolean ready(); }
    private void waitUntil(Check condition) throws Exception {
        boolean[] ready = new boolean[1]; for (int i = 0; i < 100; i++) { onMain(() -> ready[0] = condition.ready()); if (ready[0]) return; Thread.sleep(150); } fail("UI did not become ready");
    }
    private void onMain(Runnable action) {
        Throwable[] error = new Throwable[1]; getInstrumentation().runOnMainSync(() -> { try { action.run(); } catch (Throwable e) { error[0] = e; } });
        if (error[0] instanceof Error) throw (Error) error[0]; if (error[0] != null) throw new RuntimeException(error[0]);
    }
    private void capture(String name) throws Exception {
        Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        try (OutputStream output = new FileOutputStream(new File(getInstrumentation().getTargetContext().getExternalFilesDir(null), name))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); } bitmap.recycle();
    }
    private static final class Server implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0); final AtomicInteger requests = new AtomicInteger(); volatile String lastRequest;
        Server() throws Exception {
            Thread thread = new Thread(() -> { while (!socket.isClosed()) try {
                Socket client = socket.accept(); Thread worker = new Thread(() -> serve(client)); worker.setDaemon(true); worker.start();
            } catch (Exception ignored) {} }); thread.setDaemon(true); thread.start();
        }
        private void serve(Socket client) {
            try (Socket connection = client) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream())); String request = reader.readLine(), line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {}
                lastRequest = request; requests.incrementAndGet();
                if (request.contains("slow.json")) Thread.sleep(1000);
                TimeZone zone = TimeZone.getDefault(); long now = System.currentTimeMillis(); String date = EpgSchedule.today(zone);
                int start = request.indexOf("date="); if (start >= 0) date = request.substring(start + 5, start + 15);
                byte[] bytes;
                if (request.contains(".gz")) {
                    String a = EpgSchedule.format(now - 3600_000L, "yyyyMMddHHmmss Z", zone), b = EpgSchedule.format(now + 3600_000L, "yyyyMMddHHmmss Z", zone);
                    String xml = "<tv><channel id='news-1'><display-name>新闻 台</display-name></channel><programme channel='news-1' start='" + a + "' stop='" + b + "'><title>XML 当前节目</title></programme></tv>";
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream(); try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) { gzip.write(xml.getBytes(StandardCharsets.UTF_8)); } bytes = buffer.toByteArray();
                } else {
                    String json = "{\"date\":\"" + date + "\",\"epg_data\":[{\"title\":\"测试当前节目\",\"start\":\"00:00\",\"end\":\"23:59\"},{\"title\":\"午夜节目\",\"start\":\"23:59\",\"end\":\"24:00\"}]}";
                    bytes = json.getBytes(StandardCharsets.UTF_8);
                }
                connection.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8)); connection.getOutputStream().write(bytes);
            } catch (Exception ignored) {}
        }
        String url(String path) { return "http://127.0.0.1:" + socket.getLocalPort() + "/" + path; }
        @Override public void close() throws Exception { socket.close(); }
    }
}
