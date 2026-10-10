package com.kukuqi.tvbox.osc.util;

import android.net.Uri;
import com.kukuqi.tvbox.osc.bean.Subscription;
import com.google.gson.Gson;
import com.orhanobut.hawk.Hawk;
import java.util.ArrayList;
import java.util.Locale;

/** Saved sources are separate from the active source and survive removal of legacy history. */
public final class SourceLibrary {
    public static final int LIVE = 1, WALLPAPER = 2;
    public static final class Entry {
        public String name, url;
        public Entry(String name, String url) { this.name = name; this.url = url; }
    }
    public static String key(int kind) { return kind == LIVE ? "saved_live_sources" : "saved_wallpaper_sources"; }
    private static String historyKey(int kind) { return kind == LIVE ? HawkConfig.LIVE_HISTORY : HawkConfig.WALLPAPER_HISTORY; }
    public static ArrayList<Entry> list(int kind) {
        if (!Hawk.contains(key(kind))) migrate(kind);
        ArrayList<Entry> entries = new ArrayList<>();
        try {
            Entry[] stored = new Gson().fromJson(Hawk.get(key(kind), "[]"), Entry[].class);
            if (stored != null) for (Entry entry : stored)
                if (entry != null && valid(entry.url)) entries.add(new Entry(entry.name, entry.url));
        } catch (Exception ignored) {}
        return entries;
    }
    private static void migrate(int kind) {
        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> history = Hawk.get(historyKey(kind), new ArrayList<>());
        for (String url : history) append(entries, "", url);
        append(entries, "", Hawk.get(kind == LIVE ? HawkConfig.LIVE_URL : HawkConfig.WALLPAPER_URL, ""));
        write(kind, entries);
    }
    public static void save(int kind, String name, String url) {
        ArrayList<Entry> entries = list(kind);
        append(entries, name, url); write(kind, entries);
    }
    public static void replace(int kind, String oldUrl, String name, String newUrl) {
        if (newUrl == null || !valid(newUrl.trim())) return;
        newUrl = newUrl.trim();
        if (isBuiltIn(kind, oldUrl) && !oldUrl.equals(newUrl)) return;
        ArrayList<Entry> entries = list(kind);
        int position = entries.size();
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).url.equals(oldUrl)) { position = i; break; }
        final String target = newUrl;
        ArrayList<Entry> updated = new ArrayList<>();
        Entry replacement = new Entry(name == null || name.trim().isEmpty() ? defaultName(newUrl) : name.trim(), newUrl);
        for (int i = 0; i < entries.size(); i++) {
            if (i == position) updated.add(replacement);
            Entry entry = entries.get(i);
            if (!entry.url.equals(oldUrl) && !entry.url.equals(target)) updated.add(entry);
        }
        if (position == entries.size()) updated.add(replacement);
        write(kind, updated);
        if (!oldUrl.equals(newUrl)) {
            ArrayList<String> history = Hawk.get(historyKey(kind), new ArrayList<>());
            history.remove(oldUrl); Hawk.put(historyKey(kind), history);
        }
    }
    private static void append(ArrayList<Entry> entries, String name, String url) {
        if (url == null || !valid(url.trim())) return;
        url = url.trim();
        for (Entry entry : entries) if (entry.url.equals(url)) {
            if (name != null && !name.trim().isEmpty()) entry.name = name.trim();
            return;
        }
        entries.add(new Entry(name == null || name.trim().isEmpty() ? defaultName(url) : name.trim(), url));
    }
    public static void remove(int kind, String url) {
        if (isBuiltIn(kind, url)) return;
        ArrayList<Entry> entries = list(kind);
        entries.removeIf(entry -> entry.url.equals(url)); write(kind, entries);
        ArrayList<String> history = Hawk.get(historyKey(kind), new ArrayList<>());
        history.remove(url); Hawk.put(historyKey(kind), history);
    }
    public static boolean isBuiltIn(int kind, String url) {
        return (kind == LIVE ? SourceDefaults.LIVE : SourceDefaults.WALLPAPER).equals(url);
    }
    public static String name(int kind, String url) {
        for (Entry entry : list(kind)) if (entry.url.equals(url)) return entry.name;
        return defaultName(url);
    }
    private static void write(int kind, ArrayList<Entry> entries) { Hawk.put(key(kind), new Gson().toJson(entries)); }
    public static boolean valid(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return ((scheme.equals("https") || scheme.equals("http")) && uri.getHost() != null && !uri.getHost().isEmpty())
                    || ((scheme.equals("content") || scheme.equals("file") || scheme.equals("assets")) && uri.getPath() != null);
        } catch (Exception e) { return false; }
    }
    public static String defaultName(String url) {
        ArrayList<Subscription> items = Hawk.get(HawkConfig.SUBSCRIPTIONS, new ArrayList<>());
        for (Subscription item : items) if (url.equals(item.getUrl())) return item.getName();
        Uri uri = Uri.parse(url);
        if ("file".equals(uri.getScheme()) || "content".equals(uri.getScheme())) return "本地文件";
        return uri.getHost() == null ? "来源" : uri.getHost();
    }
}
