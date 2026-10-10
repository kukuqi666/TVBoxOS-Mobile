package com.kukuqi.tvbox.osc;

import android.content.Intent;
import android.test.InstrumentationTestCase;
import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.player.MyVideoView;
import com.kukuqi.tvbox.osc.player.SleepTimer;
import com.kukuqi.tvbox.osc.ui.activity.LocalPlayActivity;
import java.io.File;

/** Uses a real 120-second H.264/AAC video pushed to the emulator by the validation script. */
public class PlaybackTimerRegressionTest extends InstrumentationTestCase {
    public void testTimerPausesRealPlaybackAndLifecycleDoesNotResumeIt() throws Exception {
        String path = "/sdcard/Download/tvbox-regression.mp4";
        assertTrue("Push the regression video before running this test", new File(path).isFile());
        getInstrumentation().runOnMainSync(() -> App.getInstance().isNormalStart = true);
        Intent intent = new Intent(getInstrumentation().getTargetContext(), LocalPlayActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("videoList", "[{\"path\":\"" + path + "\",\"displayName\":\"定时播放验证\"}]");
        intent.putExtra("position", 0);
        LocalPlayActivity activity = (LocalPlayActivity) getInstrumentation().startActivitySync(intent);
        MyVideoView player = activity.findViewById(R.id.player);
        try {
            waitForPlaying(player);
            getInstrumentation().runOnMainSync(() -> SleepTimer.get().setMinutes(1));
            Thread.sleep(62_000);
            getInstrumentation().runOnMainSync(() -> {
                assertEquals(0, SleepTimer.get().remaining());
                assertFalse(player.isPlaying());
                getInstrumentation().callActivityOnPause(activity);
                getInstrumentation().callActivityOnResume(activity);
                assertFalse("Returning to the player must preserve the timer pause", player.isPlaying());
                player.start();
            });
            waitForPlaying(player);
        } finally {
            getInstrumentation().runOnMainSync(() -> { SleepTimer.get().cancel(); activity.finish(); });
        }
    }
    private void waitForPlaying(MyVideoView player) throws Exception {
        boolean[] playing = new boolean[1];
        for (int i = 0; i < 100; i++) {
            getInstrumentation().runOnMainSync(() -> playing[0] = player.isPlaying());
            if (playing[0]) return;
            Thread.sleep(200);
        }
        fail("The real video did not start playing");
    }
}
