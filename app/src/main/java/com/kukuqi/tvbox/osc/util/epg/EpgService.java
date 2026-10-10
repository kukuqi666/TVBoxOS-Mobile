package com.kukuqi.tvbox.osc.util.epg;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.LruCache;
import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.bean.LiveChannelItem;
import com.kukuqi.tvbox.osc.util.MD5;
import com.lzy.okgo.OkGo;
import okhttp3.*;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/** Separate bounded IO/cache so a slow guide cannot block playback or source loading. */
public final class EpgService {
    public interface Result { void ready(EpgSchedule schedule, String error); }
    public static final String CUSTOM = "live_epg_custom";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Object DOWNLOAD = new Object();
    private static final LruCache<String, Cached> CACHE = new LruCache<>(32);
    private static final long TTL = 10 * 60_000L;
    private static final class Cached {
        final EpgSchedule schedule; final long created = SystemClock.elapsedRealtime();
        Cached(EpgSchedule schedule) { this.schedule = schedule; }
    }
    public static final class RequestHandle {
        private volatile boolean cancelled; private volatile Call call; private Future<?> future;
        public void cancel() { cancelled = true; if (call != null) call.cancel(); if (future != null) future.cancel(true); }
        private void deliver(EpgSchedule schedule, String error, Result result) { MAIN.post(() -> { if (!cancelled) result.ready(schedule, error); }); }
    }
    public static String address(String template, LiveChannelItem channel, String date) {
        String name = channel.getTvgName().isEmpty() ? channel.getChannelName() : channel.getTvgName();
        String id = channel.getTvgId().isEmpty() ? name : channel.getTvgId();
        boolean placeholders = template.contains("{");
        String url = template.replace("{name}", encode(name)).replace("{id}", encode(id)).replace("{epg}", encode(id)).replace("{date}", date);
        String path = Uri.parse(url).getPath();
        boolean xml = path != null && (path.toLowerCase(Locale.ROOT).endsWith(".xml") || path.toLowerCase(Locale.ROOT).endsWith(".gz"));
        if (!placeholders && !xml && (url.startsWith("http://") || url.startsWith("https://")) && !url.contains("ch=") && !url.contains("date="))
            url += (url.contains("?") ? "&" : "?") + "ch=" + encode(name) + "&date=" + date;
        return url;
    }
    private static String encode(String value) {
        try { return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20"); } catch (Exception ignored) { return ""; }
    }
    public static RequestHandle load(String template, LiveChannelItem channel, String date, boolean refresh, Result result) {
        RequestHandle handle = new RequestHandle();
        TimeZone zone = TimeZone.getDefault();
        String url = address(template, channel, date);
        String id = channel.getTvgId(), name = channel.getChannelName(), tvgName = channel.getTvgName();
        String key = url + "|" + id + "|" + name + "|" + tvgName + "|" + date + "|" + zone.getID();
        handle.future = IO.submit(() -> {
            if (handle.cancelled) return;
            if (refresh) CACHE.evictAll();
            Cached cached = CACHE.get(key);
            if (cached != null && SystemClock.elapsedRealtime() - cached.created < TTL) { handle.deliver(cached.schedule, "", result); return; }
            if (template.trim().isEmpty()) { handle.deliver(null, "当前来源未提供节目表，可设置节目源", result); return; }
            try {
                File file = null;
                if (url.startsWith("http://") || url.startsWith("https://")) file = download(url, refresh, handle);
                try (InputStream raw = file == null ? App.getInstance().getContentResolver().openInputStream(Uri.parse(url)) : new FileInputStream(file);
                     BufferedInputStream buffered = new BufferedInputStream(raw)) {
                    buffered.mark(4); int first = buffered.read(), second = buffered.read(); buffered.reset();
                    InputStream decoded = first == 0x1f && second == 0x8b ? new GZIPInputStream(buffered) : buffered;
                    try (BufferedInputStream input = new BufferedInputStream(new LimitedInput(decoded, handle))) {
                        input.mark(256); byte[] prefix = new byte[128]; int length = input.read(prefix); input.reset();
                        String beginning = length <= 0 ? "" : new String(prefix, 0, length, "UTF-8").replace("\uFEFF", "").trim();
                        EpgSchedule schedule = beginning.startsWith("<") ? EpgSchedule.xml(input, id, name, tvgName, date, zone)
                                : EpgSchedule.json(text(input), date, zone);
                        if (!handle.cancelled) { CACHE.put(key, new Cached(schedule)); handle.deliver(schedule, "", result); }
                    }
                }
            } catch (Exception error) {
                if (!handle.cancelled) handle.deliver(null, "节目表加载失败，请检查地址或网络后重试", result);
            }
        });
        return handle;
    }
    private static File download(String url, boolean refresh, RequestHandle handle) throws Exception {
        synchronized (DOWNLOAD) {
            if (handle.cancelled) throw new InterruptedIOException();
            File directory = new File(App.getInstance().getCacheDir(), "epg"); if (!directory.exists() && !directory.mkdirs()) throw new IOException();
            File file = new File(directory, MD5.encode(url) + ".data");
            if (!refresh && file.isFile() && System.currentTimeMillis() - file.lastModified() < 30 * 60_000L) return file;
            File temp = new File(directory, file.getName() + ".part");
            OkHttpClient client = OkGo.getInstance().getOkHttpClient().newBuilder().connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(4, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build();
            handle.call = client.newCall(new Request.Builder().url(url).header("User-Agent", "okhttp/3.15").build());
            try (Response response = handle.call.execute()) {
                if (!response.isSuccessful() || response.body() == null) throw new IOException();
                try (InputStream input = response.body().byteStream(); FileOutputStream output = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[8192]; int length, total = 0;
                    while ((length = input.read(buffer)) != -1) {
                        if (handle.cancelled || (total += length) > 32 * 1024 * 1024) throw new InterruptedIOException();
                        output.write(buffer, 0, length);
                    }
                }
                if (handle.cancelled || !temp.renameTo(file)) throw new IOException();
            } finally { temp.delete(); handle.call = null; }
            File[] files = directory.listFiles((dir, name) -> name.endsWith(".data"));
            if (files != null) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed()); long bytes = 0;
                for (int i = 0; i < files.length; i++) { bytes += files[i].length(); if (i >= 8 || bytes > 48 * 1024 * 1024) if (!files[i].equals(file)) files[i].delete(); }
            }
            return file;
        }
    }
    private static String text(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int length;
        while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
        return output.toString("UTF-8").replace("\uFEFF", "");
    }
    private static final class LimitedInput extends FilterInputStream {
        int total; final RequestHandle handle;
        LimitedInput(InputStream input, RequestHandle handle) { super(input); this.handle = handle; }
        private void check(int count) throws IOException { if (handle.cancelled || Thread.currentThread().isInterrupted() || (total += Math.max(count, 0)) > 64 * 1024 * 1024) throw new InterruptedIOException(); }
        @Override public int read() throws IOException { int value = super.read(); check(value < 0 ? 0 : 1); return value; }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException { int count = in.read(buffer, offset, length); check(count); return count; }
    }
}
