package com.kukuqi.tvbox.osc.danmaku;

import android.net.Uri;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kukuqi.tvbox.osc.api.ApiConfig;
import com.kukuqi.tvbox.osc.util.SourceDescriptor;
import com.kukuqi.tvbox.osc.util.SourceManager;
import com.lzy.okgo.OkGo;
import com.orhanobut.hawk.Hawk;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class DanmakuController {
    private final DanmakuView view;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile String status = "未加载";
    public String status() { return status; }
    private volatile int generation;
    private static final java.util.concurrent.ExecutorService PARSER = java.util.concurrent.Executors.newSingleThreadExecutor();
    private Call search;
    public DanmakuController(DanmakuView view) { this.view = view; }
    public void cancel() { generation++; if (search != null) search.cancel(); }
    public void load(JsonElement source, String name, String episode) {
        cancel(); view.setTimeline(null); status = "正在加载";
        if (!Hawk.get("danmaku_load", false)) { status = "已关闭"; return; }
        int id = generation;
        List<String> sources = urls(source);
        String api = Hawk.get("danmaku_api_url", "");
        if (api.isEmpty()) api = ApiConfig.get().getDanmakuApi();
        boolean auto = Hawk.get("danmaku_auto", false) && !api.isEmpty();
        final String endpoint = api;
        if (!sources.isEmpty() && (Hawk.get("danmaku_spider_first", true) || !auto)) {
            fetch(sources, 0, id, () -> { if (auto) match(endpoint, name, episode, new ArrayList<>(), id); });
        } else if (auto) match(endpoint, name, episode, sources, id);
        else fetch(sources, 0, id, () -> {});
    }
    private void match(String api, String name, String episode, List<String> sources, int id) {
        if (id != generation) return;
        try {
        Request.Builder request = new Request.Builder();
        if (api.contains("{name}") || api.contains("{episode}")) request.url(api.replace("{name}", Uri.encode(name)).replace("{episode}", Uri.encode(episode)));
        else {
            com.google.gson.JsonObject body = new com.google.gson.JsonObject(); body.addProperty("name", name); body.addProperty("episode", episode);
            request.url(api).post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body.toString()));
        }
            search = OkGo.getInstance().getOkHttpClient().newCall(request.build());
            final Call requestCall = search;
            PARSER.execute(() -> {
                List<String> found = new ArrayList<>();
                try (Response response = requestCall.execute()) {
                    if (response.isSuccessful() && response.body() != null) found.addAll(urls(JsonParser.parseString(response.body().string())));
                    status = "匹配 HTTP " + response.code() + "，地址数 " + found.size();
                } catch (Exception ignored) { status = "匹配请求失败 " + ignored.getClass().getSimpleName(); }
                found.addAll(sources);
                MAIN.post(() -> { if (id == generation) fetch(found, 0, id, () -> {}); });
            });
        } catch (Exception ignored) { status = "匹配格式错误"; fetch(sources, 0, id, () -> {}); }
    }
    private void fetch(List<String> urls, int index, int id, Runnable failed) {
        if (id != generation) return;
        if (index >= urls.size()) { status += "；没有可用的弹幕"; failed.run(); return; }
        SourceManager.read(urls.get(index), (content, error) -> {
            if (id != generation) return;
            if (content == null) status = "弹幕来源读取失败：" + error;
            PARSER.execute(() -> {
                DanmakuTimeline timeline = null;
                try { if (content != null) timeline = DanmakuTimeline.parse(content); } catch (Exception ignored) {}
                DanmakuTimeline parsed = timeline;
                MAIN.post(() -> {
                    if (id != generation) return;
                    if (parsed != null && !parsed.entries.isEmpty()) { status = "已加载 " + parsed.entries.size() + " 条"; view.setTimeline(parsed); }
                    else fetch(urls, index + 1, id, failed);
                });
            });
        });
    }
    public static List<String> urls(JsonElement value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isJsonNull()) return result;
        if (value.isJsonArray()) for (JsonElement item : value.getAsJsonArray()) result.addAll(urls(item));
        else if (value.isJsonObject()) { String url = SourceDescriptor.string(value.getAsJsonObject(), "url"); if (!url.isEmpty()) result.add(url); }
        else {
            String text = value.getAsString().trim();
            if (text.startsWith("[")) { try { result.addAll(urls(JsonParser.parseString(text))); } catch (Exception ignored) {} }
            else if (!text.isEmpty()) result.add(text);
        }
        return result;
    }
}
