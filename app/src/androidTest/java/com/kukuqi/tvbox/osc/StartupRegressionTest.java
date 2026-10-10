package com.kukuqi.tvbox.osc;

import android.app.Instrumentation.ActivityMonitor;
import android.content.Intent;
import android.graphics.Bitmap;
import android.test.InstrumentationTestCase;
import android.view.View;
import com.kukuqi.tvbox.osc.ui.activity.MainActivity;
import com.kukuqi.tvbox.osc.ui.activity.SplashActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;

/** Startup remains visible with a wallpaper selected, then launches the renamed application. */
public class StartupRegressionTest extends InstrumentationTestCase {
    public void testRenamedLauncherShowsAnimationAndOpensHome() throws Exception {
        assertEquals("com.kukuqi.tvbox.osc", getInstrumentation().getTargetContext().getPackageName());
        assertEquals("TVboxOSC", getInstrumentation().getTargetContext().getString(R.string.app_name));
        ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        SplashActivity splash = (SplashActivity) getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), SplashActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        MainActivity main = null;
        try {
            Thread.sleep(500);
            onMain(() -> {
                View content = splash.findViewById(R.id.splash_content);
                assertTrue(content.isShown());
                assertTrue(content.getAlpha() > 0f);
            });
            Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            try (FileOutputStream output = new FileOutputStream(new File(getInstrumentation().getTargetContext().getExternalFilesDir(null), "tvboxosc-startup.png"))) {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
            } finally { screenshot.recycle(); }
            main = (MainActivity) getInstrumentation().waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull("启动动画后应进入首页", main);
        } finally {
            getInstrumentation().removeMonitor(monitor);
            MainActivity page = main;
            onMain(() -> { splash.finish(); if (page != null) page.finish(); });
        }
    }
    private void onMain(Runnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
