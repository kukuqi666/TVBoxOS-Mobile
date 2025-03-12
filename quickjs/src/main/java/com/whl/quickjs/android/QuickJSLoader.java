package com.whl.quickjs.android;

public final class QuickJSLoader {
    public static void init() {
        init(Boolean.FALSE);
    }

    public static void init(Boolean bool) {
        System.loadLibrary("quickjs-android-wrapper");
        if (bool.booleanValue()) {
            startRedirectingStdoutStderr("QuJs ==> ");
        }
    }

    public static native void startRedirectingStdoutStderr(String str);
}
