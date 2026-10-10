package com.kukuqi.tvbox.osc.util;

import java.io.File;
import java.io.IOException;

public final class CacheCleaner {
    private CacheCleaner() {}
    public static void clear(File root) throws IOException {
        if (root == null || !root.exists()) return;
        File[] children = root.listFiles();
        if (children == null) throw new IOException("Cannot read cache directory");
        for (File child : children) remove(child);
    }
    private static void remove(File file) throws IOException {
        if (file.isDirectory()) clear(file);
        if (!file.delete() && file.exists()) throw new IOException("Cannot delete cache file");
    }
}
