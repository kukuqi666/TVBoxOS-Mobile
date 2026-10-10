package com.kukuqi.tvbox.osc.util;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.kukuqi.tvbox.osc.api.ApiConfig;
import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.bean.Subscription;
import com.kukuqi.tvbox.osc.event.SourceChangedEvent;
import com.kukuqi.tvbox.osc.event.WallpaperChangedEvent;
import com.lzy.okgo.OkGo;
import com.orhanobut.hawk.Hawk;
import org.greenrobot.eventbus.EventBus;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Request;

public final class SourceManager {
    public interface Result { void onResult(SourceDescriptor source, String error); }
    public interface ContentResult { void onResult(String content, String error); }
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    public static String currentName() {
        String url = Hawk.get(HawkConfig.API_URL, "");
        ArrayList<Subscription> items = Hawk.get(HawkConfig.SUBSCRIPTIONS, new ArrayList<>());
        for (Subscription item : items) if (url.equals(item.getUrl())) return item.getName();
        return "当前订阅";
    }
    public static SourceDescriptor cached(String url) {
        String json = Hawk.get("source_descriptor_" + MD5.encode(url), "");
        try { return json.isEmpty() ? null : new SourceDescriptor(url, json); } catch (Exception e) { return null; }
    }
    public static void record(String url, String json) {
        try {
            SourceDescriptor descriptor = new SourceDescriptor(url, json);
            Hawk.put("source_descriptor_" + MD5.encode(url), descriptor.content.toString());
            MAIN.post(() -> {
                EventBus.getDefault().post(new SourceChangedEvent());
                if (WallpaperManager.get().isFollowingSource()) EventBus.getDefault().post(new WallpaperChangedEvent());
            });
        } catch (Exception ignored) {}
    }
    public static void inspect(String url, Result result) {
        read(url, (content, error) -> {
            if (content == null) { result.onResult(null, error); return; }
            try {
                String json = ApiConfig.FindResult(content, null);
                SourceDescriptor descriptor = new SourceDescriptor(url, json);
                record(url, json); result.onResult(descriptor, "");
            } catch (Exception e) { result.onResult(null, "这个地址没有返回有效的订阅配置"); }
        });
    }
    public static void read(String url, ContentResult result) {
        IO.execute(() -> {
            String content = null, error = "来源加载失败，请检查地址或网络";
            try {
                if (url.startsWith("assets://")) {
                    try (InputStream input = App.getInstance().getAssets().open(url.substring(9))) { content = text(input); }
                } else if (url.startsWith("content://") || url.startsWith("file://")) {
                    try (InputStream input = App.getInstance().getContentResolver().openInputStream(Uri.parse(url))) { content = text(input); }
                } else {
                    try (okhttp3.Response response = OkGo.getInstance().getOkHttpClient().newCall(new Request.Builder().url(url).header("User-Agent", "okhttp/3.15").build()).execute()) {
                        if (response.isSuccessful() && response.body() != null) content = text(response.body().byteStream());
                    }
                }
            } catch (Exception ignored) {}
            String ready = content, message = error; MAIN.post(() -> result.onResult(ready, ready == null ? message : ""));
        });
    }
    private static String text(InputStream input) throws Exception {
        if (input == null) throw new java.io.IOException();
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int length;
        while ((length = input.read(buffer)) != -1) {
            if (output.size() + length > 8 * 1024 * 1024) throw new java.io.IOException();
            output.write(buffer, 0, length);
        }
        return output.toString("UTF-8");
    }
}
