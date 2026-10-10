package com.kukuqi.tvbox.osc;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.SeekBar;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.player.MyVideoView;
import com.kukuqi.tvbox.osc.player.controller.LocalVideoController;
import com.kukuqi.tvbox.osc.ui.activity.LiveActivity;
import com.kukuqi.tvbox.osc.ui.activity.LocalPlayActivity;
import com.kukuqi.tvbox.osc.ui.dialog.AllChannelsRightDialog;
import com.kukuqi.tvbox.osc.ui.dialog.PlaybackSpeedDialog;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.kukuqi.tvbox.osc.util.LiveFavorites;
import com.orhanobut.hawk.Hawk;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Exercises real channel selection, Hawk storage and video playback, preserving user settings. */
public class PlaybackFeaturesRegressionTest extends InstrumentationTestCase {
    private static final String VIDEO = "/sdcard/Download/tvbox-regression.mp4";
    private static final String[] STATE_KEYS = {HawkConfig.LIVE_URL, HawkConfig.LIVE_CHANNEL, LiveFavorites.KEY, "local_playback_speed"};
    private File stateFile() { return new File(getInstrumentation().getTargetContext().getFilesDir(), "playback-regression-state.json"); }

    @Override protected void setUp() throws Exception {
        super.setUp();
        restoreInterruptedRun();
        // Discard only an orphaned internal fixture left by an emulator shutdown before this safeguard existed.
        String selected = Hawk.get(HawkConfig.LIVE_URL, "");
        if (selected.endsWith("/channel-regression.json")) { Hawk.delete(HawkConfig.LIVE_URL); Hawk.delete(HawkConfig.LIVE_CHANNEL); }
        JSONObject state = new JSONObject();
        for (String key : STATE_KEYS) state.put(key, Hawk.contains(key) ? Hawk.get(key) : JSONObject.NULL);
        try (FileOutputStream stream = new FileOutputStream(stateFile())) { stream.write(state.toString().getBytes(StandardCharsets.UTF_8)); }
    }
    @Override protected void tearDown() throws Exception {
        try { restoreInterruptedRun(); } finally { super.tearDown(); }
    }
    private void restoreInterruptedRun() throws Exception {
        if (!stateFile().exists()) return;
        JSONObject state = new JSONObject(new String(Files.readAllBytes(stateFile().toPath()), StandardCharsets.UTF_8));
        for (String key : STATE_KEYS) {
            if (state.isNull(key)) Hawk.delete(key);
            else if (key.equals("local_playback_speed")) Hawk.put(key, (float) state.getDouble(key));
            else Hawk.put(key, state.getString(key));
        }
        stateFile().delete();
    }

    public void testFavoritesPersistAndCanBeRemoved() {
        boolean existed = Hawk.contains(LiveFavorites.KEY); String before = Hawk.get(LiveFavorites.KEY, "[]");
        try {
            Hawk.delete(LiveFavorites.KEY);
            assertTrue(LiveFavorites.toggle(" CCTV-1 "));
            assertTrue(LiveFavorites.contains("cctv-1"));
            assertTrue("Saved JSON survives a fresh read", Hawk.get(LiveFavorites.KEY, "").contains("cctv-1"));
            assertFalse(LiveFavorites.toggle("CCTV-1"));
            assertFalse(LiveFavorites.contains("cctv-1"));
        } finally { if (existed) Hawk.put(LiveFavorites.KEY, before); else Hawk.delete(LiveFavorites.KEY); }
    }

    public void testSearchAndFavoritesPlayOriginalChannelAndExcludeLockedGroups() throws Exception {
        assertTrue(new File(VIDEO).isFile());
        String[] keys = {HawkConfig.LIVE_URL, HawkConfig.LIVE_CHANNEL, LiveFavorites.KEY};
        boolean[] existed = new boolean[keys.length]; String[] before = new String[keys.length];
        for (int i = 0; i < keys.length; i++) { existed[i] = Hawk.contains(keys[i]); before[i] = Hawk.get(keys[i], ""); }
        File source = new File(getInstrumentation().getTargetContext().getFilesDir(), "channel-regression.json");
        String url = "file://" + VIDEO;
        String json = "[{\"group\":\"新闻\",\"channels\":[{\"name\":\"测试首页\",\"urls\":[\"" + url + "\"]}]},"
                + "{\"group\":\"地方\",\"channels\":[{\"name\":\"其他台\",\"urls\":[\"" + url + "\"]},{\"name\":\"目标台\",\"urls\":[\"" + url + "\"]}]},"
                + "{\"group\":\"加密_123\",\"channels\":[{\"name\":\"隐藏台\",\"urls\":[\"" + url + "\"]}]}]";
        try (FileOutputStream stream = new FileOutputStream(source)) { stream.write(json.getBytes("UTF-8")); }
        onMain(() -> {
            App.getInstance().isNormalStart = true;
            Hawk.put(HawkConfig.LIVE_URL, "file://" + source.getAbsolutePath());
            Hawk.put(HawkConfig.LIVE_CHANNEL, ""); Hawk.delete(LiveFavorites.KEY);
        });
        LiveActivity activity = null; AllChannelsRightDialog[] browser = new AllChannelsRightDialog[1];
        try {
            activity = (LiveActivity) getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), LiveActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            LiveActivity live = activity;
            waitUntil(() -> live.getCurrentLiveChannelItem() != null);
            onMain(() -> {
                assertEquals("测试首页", live.getCurrentLiveChannelItem().getChannelName());
                assertTrue(live.searchLiveChannels("隐藏", false).isEmpty());
                live.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                live.showAllChannelDialog();
                try {
                    Field field = LiveActivity.class.getDeclaredField("mAllChannelRightDialog"); field.setAccessible(true);
                    browser[0] = (AllChannelsRightDialog) field.get(live);
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            waitUntil(() -> browser[0].findViewById(R.id.channel_query) != null);
            onMain(() -> ((MyVideoView) live.findViewById(R.id.mVideoView)).pause());
            Thread.sleep(500); capture("live-channels.png");
            onMain(() -> ((EditText) browser[0].findViewById(R.id.channel_query)).setText("目标"));
            RecyclerView recycler = browser[0].findViewById(R.id.channel_results);
            waitUntil(() -> recycler.findViewHolderForAdapterPosition(0) != null);
            onMain(() -> {
                assertEquals(1, recycler.getAdapter().getItemCount());
                recycler.findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.result_favorite).performClick();
                assertTrue(LiveFavorites.contains("目标台"));
                browser[0].findViewById(R.id.channel_clear).performClick();
                browser[0].findViewById(R.id.channels_favorites).performClick();
            });
            waitUntil(() -> recycler.findViewHolderForAdapterPosition(0) != null);
            Thread.sleep(1200); capture("live-favorites.png");
            onMain(() -> {
                recycler.findViewHolderForAdapterPosition(0).itemView.performClick();
                assertEquals("目标台", live.getCurrentLiveChannelItem().getChannelName());
                assertEquals(1, LiveActivity.currentChannelGroupIndex);
                assertEquals(1, live.getCurrentLiveChannelItem().getChannelIndex());
            });
            waitUntil(() -> !browser[0].isShow());
            onMain(() -> live.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            Thread.sleep(600);
            onMain(() -> {
                live.showAllChannelDialog();
                try {
                    Field field = LiveActivity.class.getDeclaredField("mAllChannelRightDialog"); field.setAccessible(true);
                    browser[0] = (AllChannelsRightDialog) field.get(live);
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            waitUntil(() -> browser[0].findViewById(R.id.channel_query) != null);
            onMain(() -> ((MyVideoView) live.findViewById(R.id.mVideoView)).pause());
            Thread.sleep(500); capture("live-channels-portrait.png");
        } finally {
            LiveActivity live = activity;
            onMain(() -> {
                if (browser[0] != null) browser[0].dismiss(); if (live != null) live.finish();
                for (int i = 0; i < keys.length; i++) { if (existed[i]) Hawk.put(keys[i], before[i]); else Hawk.delete(keys[i]); }
            });
            source.delete();
        }
    }

    public void testCustomSpeedReopensAndLongPressRestoresChosenSpeed() throws Exception {
        boolean existed = Hawk.contains("local_playback_speed"); float before = Hawk.get("local_playback_speed", 1f);
        onMain(() -> { App.getInstance().isNormalStart = true; Hawk.put("local_playback_speed", 1f); });
        LocalPlayActivity activity = null; AlertDialog[] dialog = new AlertDialog[1];
        try {
            for (int pass = 0; pass < 2; pass++) {
                com.blankj.utilcode.util.SPUtils.getInstance(com.kukuqi.tvbox.osc.constant.CacheConst.VIDEO_PROGRESS_SP).put(VIDEO, 0L);
                Intent intent = new Intent(getInstrumentation().getTargetContext(), LocalPlayActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("videoList", "[{\"path\":\"" + VIDEO + "\",\"displayName\":\"自定义倍速验证\"}]"); intent.putExtra("position", 0);
                activity = (LocalPlayActivity) getInstrumentation().startActivitySync(intent);
                LocalPlayActivity local = activity;
                MyVideoView player = local.findViewById(R.id.player);
                waitForPlayback(player);
                Field field = LocalPlayActivity.class.getDeclaredField("mController"); field.setAccessible(true);
                LocalVideoController controller = (LocalVideoController) field.get(local);
                Thread.sleep(300);
                if (pass == 0) {
                    onMain(() -> {
                        dialog[0] = PlaybackSpeedDialog.show(local, controller.getConfiguredSpeed(), controller::setSpeed);
                        ((SeekBar) dialog[0].findViewById(R.id.speed_slider)).setProgress(16);
                    });
                    Thread.sleep(500); capture("custom-speed.png");
                    onMain(() -> {
                        dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                    });
                    // AlertDialog dispatches button callbacks through the main Handler.
                    waitUntil(() -> Math.abs(controller.getConfiguredSpeed() - 1.7f) < 0.001f);
                    onMain(() -> {
                        assertEquals(1.7f, player.getSpeed(), 0.02f);
                        assertEquals(1.7f, Hawk.get("local_playback_speed", 1f), 0.001f);
                        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 400, 300, 0);
                        controller.onLongPress(down); down.recycle();
                        MotionEvent cancel = MotionEvent.obtain(0, 50, MotionEvent.ACTION_CANCEL, 400, 300, 0);
                        controller.onTouchEvent(cancel); cancel.recycle();
                        assertEquals("Long press must restore chosen speed", 1.7f, player.getSpeed(), 0.02f);
                    });
                } else onMain(() -> assertEquals("Reopen must keep chosen speed", 1.7f, player.getSpeed(), 0.02f));
                onMain(local::finish); activity = null; Thread.sleep(400);
            }
        } finally {
            LocalPlayActivity local = activity;
            onMain(() -> {
                if (dialog[0] != null) dialog[0].dismiss(); if (local != null) local.finish();
                if (existed) Hawk.put("local_playback_speed", before); else Hawk.delete("local_playback_speed");
            });
        }
    }

    private interface Check { boolean check(); }
    private void waitForPlayback(MyVideoView player) throws Exception {
        String[] status = new String[1]; boolean[] playing = new boolean[1];
        for (int i = 0; i < 100; i++) {
            onMain(() -> {
                playing[0] = player.isPlaying();
                status[0] = "state=" + player.getCurrentPlayState() + ", position=" + player.getCurrentPosition()
                        + ", duration=" + player.getDuration() + ", backend=" + (player.getMediaPlayer() == null ? "null" : player.getMediaPlayer().getClass().getSimpleName());
            });
            if (playing[0]) return; Thread.sleep(150);
        }
        fail("Playback did not start: " + status[0]);
    }
    private void onMain(Runnable action) {
        Throwable[] failure = new Throwable[1];
        getInstrumentation().runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] instanceof Error) throw (Error) failure[0];
        if (failure[0] != null) throw new RuntimeException(failure[0]);
    }
    private void waitUntil(Check check) throws Exception {
        boolean[] ready = new boolean[1];
        for (int i = 0; i < 100; i++) {
            onMain(() -> ready[0] = check.check());
            if (ready[0]) return; Thread.sleep(150);
        }
        fail("界面或播放器未在 15 秒内就绪");
    }
    private void capture(String name) throws Exception {
        Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
        if (screenshot == null) return;
        File file = new File(getInstrumentation().getTargetContext().getExternalFilesDir(null), name);
        try (FileOutputStream stream = new FileOutputStream(file)) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        screenshot.recycle();
    }
}
