package com.kukuqi.tvbox.osc.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orhanobut.hawk.Hawk;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Public trending titles, cached independently of private search history. */
public final class HotSearch {
    public interface Result { void ready(List<String> words, String status); }
    private static final String[] URLS = {
        "https://movie.douban.com/j/search_subjects?type=movie&tag=%E7%83%AD%E9%97%A8&sort=recommend&page_limit=20&page_start=0",
        "https://movie.douban.com/j/search_subjects?type=tv&tag=%E7%83%AD%E9%97%A8&sort=recommend&page_limit=20&page_start=0"
    };
    private HotSearch() {}
    public static List<String> parse(String content) {
        LinkedHashSet<String> words = new LinkedHashSet<>();
        JsonElement root = JsonParser.parseString(content);
        if (root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            if (object.has("subjects")) root = object.get("subjects");
            else if (object.has("data")) {
                root = object.get("data");
                if (root.isJsonObject()) root = root.getAsJsonObject().getAsJsonObject("mapResult").getAsJsonObject("0").get("listInfo");
            }
        }
        if (root != null && root.isJsonArray()) for (JsonElement item : root.getAsJsonArray()) {
            String title = item.isJsonPrimitive() ? item.getAsString() : item.isJsonObject() ? SourceDescriptor.string(item.getAsJsonObject(), "title") : "";
            title = title.replaceAll("<[^>]*>", "").replace("《", "").replace("》", "").trim();
            if (!title.isEmpty() && title.length() <= 60) words.add(title);
            if (words.size() >= 20) break;
        }
        return new ArrayList<>(words);
    }
    public static void load(boolean force, Result result) {
        List<String> cache = cached();
        if (!cache.isEmpty()) result.ready(cache, "上次热门 · 点击刷新");
        if (!force && !cache.isEmpty() && System.currentTimeMillis() - Hawk.get("hot_search_time", 0L) < 6 * 60 * 60 * 1000L) return;
        fetch(0, cache, result);
    }
    private static List<String> cached() {
        try { return parse(Hawk.get("hot_search_cache", "[]")); } catch (Exception ignored) { return new ArrayList<>(); }
    }
    private static void fetch(int index, List<String> cache, Result result) {
        SourceManager.read(URLS[index], (content, error) -> {
            List<String> words = new ArrayList<>();
            try { if (content != null) words = parse(content); } catch (Exception ignored) {}
            if (!words.isEmpty()) {
                Hawk.put("hot_search_cache", new com.google.gson.Gson().toJson(words)); Hawk.put("hot_search_time", System.currentTimeMillis());
                result.ready(words, "热门搜索 · 点击刷新");
            } else if (index + 1 < URLS.length) fetch(index + 1, cache, result);
            else {
                if (cache.isEmpty()) { try { cache.addAll(parse(Hawk.get("home_hot", "[]"))); } catch (Exception ignored) {} }
                result.ready(cache, cache.isEmpty() ? "热门搜索暂时不可用 · 点击重试" : "网络不可用，展示上次热门 · 点击重试");
            }
        });
    }
}
