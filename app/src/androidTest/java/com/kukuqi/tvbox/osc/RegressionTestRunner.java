package com.kukuqi.tvbox.osc;

import android.app.Application;
import android.test.InstrumentationTestRunner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Wait for real application initialization before exercising its singletons. */
public class RegressionTestRunner extends InstrumentationTestRunner {
    private final CountDownLatch created = new CountDownLatch(1);
    @Override public void callApplicationOnCreate(Application application) {
        super.callApplicationOnCreate(application);
        created.countDown();
    }
    @Override public void onStart() {
        try {
            if (!created.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Application did not initialize");
        } catch (InterruptedException e) { throw new RuntimeException(e); }
        super.onStart();
    }
}
