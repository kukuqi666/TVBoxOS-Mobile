package com.kukuqi.tvbox.osc.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.orhanobut.hawk.Hawk;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Keep channel favorites stable when playlist URLs change. */
public final class LiveFavorites {
    public static final String KEY = "live_favorite_names";
    private LiveFavorites() {}

    public static String nameKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public static Set<String> names() {
        try {
            Set<String> saved = new Gson().fromJson(Hawk.get(KEY, "[]"),
                    new TypeToken<LinkedHashSet<String>>() {}.getType());
            return saved == null ? new LinkedHashSet<>() : new LinkedHashSet<>(saved);
        } catch (Exception ignored) { return new LinkedHashSet<>(); }
    }

    public static boolean contains(String name) { return names().contains(nameKey(name)); }

    public static synchronized boolean toggle(String name) {
        String key = nameKey(name);
        if (key.isEmpty()) return false;
        Set<String> saved = names();
        boolean added = !saved.remove(key);
        if (added) saved.add(key);
        Hawk.put(KEY, new Gson().toJson(saved));
        return added;
    }
}
