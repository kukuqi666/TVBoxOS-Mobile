package com.kukuqi.tvbox.osc.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.kukuqi.tvbox.osc.R;
import com.kukuqi.tvbox.osc.api.ApiConfig;
import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.event.WallpaperChangedEvent;
import com.lzy.okgo.OkGo;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Request;

/** A downloaded image is a persistent snapshot shared by thumbnail, preview and background. */
public class WallpaperManager {
    public static class WallpaperItem {
        public static final int TYPE_BUILTIN = 0, TYPE_SUBSCRIPTION = 1, TYPE_ONLINE = 2, TYPE_CUSTOM = 3, TYPE_FOLLOW = 4;
        public String name, url;
        public int type, resId;
        public WallpaperItem(String name, int type, String url, int resId) {
            this.name = name; this.type = type; this.url = url; this.resId = resId;
        }
    }

    public interface FileResult { void onResult(@Nullable File file); }
    public interface ImportResult { void onResult(String value); }
    private static final WallpaperManager INSTANCE = new WallpaperManager();
    public static WallpaperManager get() { return INSTANCE; }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private static final class Download {
        final List<FileResult> callbacks = new ArrayList<>();
        volatile boolean cancelled;
        volatile okhttp3.Call call;
        Runnable timeout;
    }
    private final Map<String, Download> pending = new HashMap<>();
    private final java.util.Set<String> failedSources = new java.util.HashSet<>();
    private final WeakHashMap<View, Drawable> originals = new WeakHashMap<>();
    private final Map<View, CustomTarget<Bitmap>> targets = new WeakHashMap<>();
    private long revision;
    private WallpaperManager() {}

    private File directory() {
        File dir = new File(App.getInstance().getFilesDir(), "wallpapers");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
    public File getCachedFile(String url) { return new File(directory(), MD5.encode(url) + ".wp"); }
    public boolean isCached(String url) { return getCachedFile(url).isFile(); }
    public boolean isAvailable(String value) { return !failedSources.contains(value); }
    public boolean isFollowingSource() {
        return "follow".equals(Hawk.get("wallpaper_mode", Hawk.contains(HawkConfig.WALLPAPER_URL) ? "custom" : "follow"));
    }
    public String getSelectionValue() { return isFollowingSource() ? "follow://subscription" : Hawk.get(HawkConfig.WALLPAPER_URL, ""); }
    public String getWallpaperPref() {
        if (!isFollowingSource()) return Hawk.get(HawkConfig.WALLPAPER_URL, "");
        List<WallpaperItem> walls = getSubscriptionWallpapers();
        return walls.isEmpty() ? "" : walls.get(0).url;
    }
    public String sourceDescription() {
        if (isFollowingSource()) return getSubscriptionWallpapers().isEmpty() ? SourceManager.currentName() + "未提供壁纸" : "跟随 " + SourceManager.currentName();
        String value = getWallpaperPref();
        if (value.isEmpty()) return "无壁纸";
        if (value.startsWith("drawable://")) return "内置壁纸";
        if (value.startsWith("file://")) return "本地图片";
        return SourceLibrary.name(SourceLibrary.WALLPAPER, value);
    }
    public int getSoftness() { return Math.max(0, Math.min(80, Hawk.get("wallpaper_softness", 45))); }
    public void setSoftness(int value) {
        Hawk.put("wallpaper_softness", Math.max(0, Math.min(80, value)));
        changed();
    }
    public String valueOf(WallpaperItem item) {
        if (item.type == WallpaperItem.TYPE_FOLLOW) return "follow://subscription";
        if (item.type != WallpaperItem.TYPE_BUILTIN) return item.url == null ? "" : item.url;
        return item.resId == 0 ? "" : "drawable://" + App.getInstance().getResources().getResourceEntryName(item.resId);
    }
    public int drawableId(String value) {
        if (!value.startsWith("drawable://")) return 0;
        String name = value.substring(11);
        try {
            int id = Integer.parseInt(name); // Compatibility with the previous numeric preference.
            String entry = App.getInstance().getResources().getResourceEntryName(id);
            return entry.startsWith("wallpaper_gradient_") ? id : 0;
        } catch (Exception ignored) {
            return name.startsWith("wallpaper_gradient_")
                    ? App.getInstance().getResources().getIdentifier(name, "drawable", App.getInstance().getPackageName()) : 0;
        }
    }
    public void saveWallpaper(WallpaperItem item) {
        if (item.type != WallpaperItem.TYPE_FOLLOW && SourceLibrary.valid(valueOf(item)))
            SourceLibrary.save(SourceLibrary.WALLPAPER, item.name, valueOf(item));
        Hawk.put("wallpaper_mode", item.type == WallpaperItem.TYPE_FOLLOW ? "follow" : "custom");
        Hawk.put(HawkConfig.WALLPAPER_URL, item.type == WallpaperItem.TYPE_FOLLOW ? "" : valueOf(item));
        changed();
    }
    public void clearWallpaper() {
        Hawk.put("wallpaper_mode", "custom");
        Hawk.put(HawkConfig.WALLPAPER_URL, "");
        changed();
    }
    private void changed() { revision++; EventBus.getDefault().post(new WallpaperChangedEvent()); }

    public void setSource(String url, ImportResult callback) {
        prepareSource(url, file -> {
            if (file != null) {
                addCustomWallpaper(url);
                saveWallpaper(new WallpaperItem("单独壁纸源", WallpaperItem.TYPE_CUSTOM, url, 0));
            }
            callback.onResult(file == null ? "" : url);
        });
    }

    /** Callbacks run on the main thread. Concurrent requests for a random URL share one image. */
    public void prepare(String value, FileResult callback) {
        prepare(value, false, callback);
    }
    public void prepareSource(String value, FileResult callback) {
        prepare(value, true, callback);
    }
    public void refresh(FileResult callback) {
        String value = getWallpaperPref();
        prepare(value, true, file -> {
            if (value.equals(getWallpaperPref())) changed();
            callback.onResult(file);
        });
    }
    private void prepare(String value, boolean refresh, FileResult callback) {
        if (!refresh && failedSources.contains(value)) { callback.onResult(null); return; }
        Download existing = pending.get(value);
        if (existing != null) { existing.callbacks.add(callback); return; }
        Download download = new Download(); download.callbacks.add(callback); pending.put(value, download);
        download.timeout = () -> {
            if (pending.get(value) != download) return;
            download.cancelled = true;
            if (download.call != null) download.call.cancel();
            finish(value, download, null);
        };
        main.postDelayed(download.timeout, 10000);
        io.execute(() -> {
            File result = null;
            File temporary = null;
            try {
                if (download.cancelled) return;
                File destination = getCachedFile(value);
                if (!refresh && valid(destination)) result = destination;
                else if (value.startsWith("file://")) {
                    File source = new File(Uri.parse(value).getPath());
                    if (valid(source)) {
                        if (source.getCanonicalPath().startsWith(directory().getCanonicalPath() + File.separator)) result = source;
                        else { // Rescue legacy imports before the old cache is cleared.
                            temporary = new File(destination.getPath() + "." + System.nanoTime() + ".tmp");
                            try (InputStream input = new java.io.FileInputStream(source)) { copy(input, temporary); }
                            if (!download.cancelled && temporary.renameTo(destination)) result = destination;
                        }
                    }
                } else if (isHttp(value)) {
                    temporary = new File(destination.getPath() + "." + System.nanoTime() + ".tmp");
                    if (fetchImage(value, temporary, 0, download) && !download.cancelled && temporary.renameTo(destination)) result = destination;
                }
            } catch (Exception ignored) {
            } finally { if (temporary != null) temporary.delete(); }
            File ready = result;
            main.post(() -> finish(value, download, ready));
        });
    }

    private void finish(String value, Download download, File file) {
        if (pending.get(value) != download) return;
        pending.remove(value); main.removeCallbacks(download.timeout);
        if (file == null) failedSources.add(value); else failedSources.remove(value);
        for (FileResult listener : download.callbacks) listener.onResult(file);
    }

    private boolean fetchImage(String url, File temporary, int depth, Download download) throws Exception {
        if (depth > 3 || download.cancelled) return false;
        okhttp3.OkHttpClient client = OkGo.getInstance().getOkHttpClient().newBuilder()
                .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(false).build();
        download.call = client.newCall(new Request.Builder().url(url).header("User-Agent", "okhttp/3.15").build());
        if (download.cancelled) { download.call.cancel(); return false; }
        try (okhttp3.Response response = download.call.execute()) {
            if (!response.isSuccessful() || response.body() == null) return false;
            try (InputStream input = response.body().byteStream()) { copy(input, temporary); }
        }
        if (valid(temporary)) return true;
        if (temporary.length() > 8 * 1024 * 1024) return false;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (InputStream input = new java.io.FileInputStream(temporary)) {
            byte[] buffer = new byte[8192]; int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
        }
        String json = ApiConfig.FindResult(output.toString("UTF-8"), null);
        SourceDescriptor source = new SourceDescriptor(url, json);
        SourceManager.record(url, json);
        List<String> walls = source.wallpapers();
        return !walls.isEmpty() && fetchImage(walls.get(0), temporary, depth + 1, download);
    }

    private boolean valid(File file) {
        if (!file.isFile()) return false;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        return options.outWidth > 0 && options.outHeight > 0;
    }
    private void copy(InputStream input, File target) throws Exception {
        try (FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192]; int length; long total = 0;
            while ((length = input.read(buffer)) != -1) {
                total += length;
                if (total > 32L * 1024 * 1024) throw new java.io.IOException("Image too large");
                output.write(buffer, 0, length);
            }
        }
    }
    public void importWallpaper(Uri uri, ImportResult callback) {
        io.execute(() -> {
            File target = new File(directory(), "imported_" + System.currentTimeMillis() + ".wp");
            File temporary = new File(target.getPath() + ".tmp");
            String result = "";
            try (InputStream input = App.getInstance().getContentResolver().openInputStream(uri)) {
                if (input != null) {
                    copy(input, temporary);
                    if (valid(temporary) && temporary.renameTo(target)) result = Uri.fromFile(target).toString();
                }
            } catch (Exception ignored) {
            } finally { temporary.delete(); }
            String value = result;
            main.post(() -> {
                if (!value.isEmpty()) {
                    SourceLibrary.save(SourceLibrary.WALLPAPER, "本地图片", value);
                    Hawk.put("wallpaper_mode", "custom"); Hawk.put(HawkConfig.WALLPAPER_URL, value); changed();
                }
                callback.onResult(value);
            });
        });
    }

    public void applyToActivity(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root != null) applyToView(root);
    }
    public void releaseActivity(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        root.setTag(R.id.wallpaper_request_tag, null);
        CustomTarget<Bitmap> target = targets.remove(root);
        if (target != null) Glide.with(App.getInstance()).clear(target);
        originals.remove(root);
    }
    public void applyToView(View root) {
        String value = getWallpaperPref();
        String token = value + ":" + getSoftness() + ":" + Utils.isDarkTheme() + ":" + revision;
        if (token.equals(root.getTag(R.id.wallpaper_request_tag))) return;
        if (!originals.containsKey(root)) originals.put(root, root.getBackground());
        root.setTag(R.id.wallpaper_request_tag, token);
        CustomTarget<Bitmap> old = targets.remove(root);
        if (old != null) Glide.with(App.getInstance()).clear(old);
        root.setBackground(originals.get(root));
        if (value.isEmpty()) return;
        int id = drawableId(value);
        if (id != 0) {
            root.setBackground(softened(ContextCompat.getDrawable(root.getContext(), id)));
            return;
        }
        prepare(value, file -> {
            if (!token.equals(root.getTag(R.id.wallpaper_request_tag)) || file == null) return;
            if (root.getContext() instanceof Activity && ((Activity) root.getContext()).isDestroyed()) return;
            // Update legacy local paths only while they are still the user's active choice.
            if (value.startsWith("file://") && !Uri.fromFile(file).toString().equals(value) && value.equals(getWallpaperPref())) {
                Hawk.put(HawkConfig.WALLPAPER_URL, Uri.fromFile(file).toString());
            }
            int width = root.getResources().getDisplayMetrics().widthPixels;
            int height = root.getResources().getDisplayMetrics().heightPixels;
            CustomTarget<Bitmap> target = new CustomTarget<Bitmap>(width, height) {
                        @Override public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                            if (token.equals(root.getTag(R.id.wallpaper_request_tag)))
                                root.setBackground(softened(new BitmapDrawable(root.getResources(), bitmap)));
                        }
                        @Override public void onLoadCleared(@Nullable Drawable placeholder) {
                            if (token.equals(root.getTag(R.id.wallpaper_request_tag))) {
                                root.setBackground(originals.get(root));
                                root.setTag(R.id.wallpaper_request_tag, null);
                            }
                        }
                        @Override public void onLoadFailed(@Nullable Drawable error) {
                            if (token.equals(root.getTag(R.id.wallpaper_request_tag))) root.setTag(R.id.wallpaper_request_tag, null);
                        }
                    };
            targets.put(root, target);
            Glide.with(root).asBitmap().load(file).signature(new com.bumptech.glide.signature.ObjectKey(file.lastModified()))
                    .override(width, height).centerCrop().into(target);
        });
    }
    public Drawable softened(Drawable image) {
        return softened(image, getSoftness());
    }
    public Drawable softened(Drawable image, int softness) {
        return new WallpaperDrawable(image, softness, Utils.isDarkTheme());
    }
    /** Center-crop even after rotation; tint the image without fading text or controls. */
    private static class WallpaperDrawable extends Drawable {
        private final Drawable image;
        private final int tint;
        WallpaperDrawable(Drawable image, int softness, boolean dark) {
            this.image = image;
            tint = ((softness * 255 / 100) << 24) | (dark ? 0x0010151D : 0x00FFFFFF);
        }
        @Override public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            int save = canvas.save(); canvas.clipRect(bounds);
            if (image instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) image).getBitmap();
                float scale = Math.max((float) bounds.width() / bitmap.getWidth(), (float) bounds.height() / bitmap.getHeight());
                int width = Math.round(bitmap.getWidth() * scale), height = Math.round(bitmap.getHeight() * scale);
                int left = bounds.left + (bounds.width() - width) / 2, top = bounds.top + (bounds.height() - height) / 2;
                image.setBounds(left, top, left + width, top + height);
            } else image.setBounds(bounds);
            image.draw(canvas); canvas.drawColor(tint); canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int alpha) { image.setAlpha(alpha); }
        @Override public void setColorFilter(@Nullable ColorFilter filter) { image.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    public List<WallpaperItem> getBuiltInWallpapers() {
        List<WallpaperItem> items = new ArrayList<>();
        items.add(new WallpaperItem("跟随当前订阅", WallpaperItem.TYPE_FOLLOW, "follow://subscription", 0));
        items.add(new WallpaperItem("默认 · 无壁纸", 0, null, 0));
        String[] names = {"深邃蓝", "极夜紫", "日落橙", "森林绿", "海洋蓝", "梅子紫"};
        int[] ids = {R.drawable.wallpaper_gradient_blue, R.drawable.wallpaper_gradient_night, R.drawable.wallpaper_gradient_sunset,
                R.drawable.wallpaper_gradient_forest, R.drawable.wallpaper_gradient_ocean, R.drawable.wallpaper_gradient_plum};
        for (int i = 0; i < names.length; i++) items.add(new WallpaperItem(names[i], 0, null, ids[i]));
        return items;
    }
    public List<WallpaperItem> getSubscriptionWallpapers() {
        List<WallpaperItem> items = new ArrayList<>();
        String config = ApiConfig.get().wallpaper;
        if (config != null) for (String url : config.split(",")) {
            if (isHttp(url.trim())) items.add(new WallpaperItem("订阅壁纸 " + (items.size() + 1), 1, url.trim(), 0));
        }
        return items;
    }
    public List<WallpaperItem> getOnlineWallpapers() {
        List<WallpaperItem> items = new ArrayList<>();
        ArrayList<String> history = Hawk.get(HawkConfig.WALLPAPER_HISTORY, new ArrayList<>());
        for (String url : history) if (isHttp(url)) items.add(new WallpaperItem("导入壁纸 " + (items.size() + 1), 3, url, 0));
        items.add(new WallpaperItem("随机风景", 2, "https://picsum.photos/1280/720", 0));
        items.add(new WallpaperItem("随机动漫", 2, "https://www.dmoe.cc/random.php", 0));
        return items;
    }
    private boolean isHttp(String value) {
        if (value == null) return false;
        Uri uri = Uri.parse(value);
        return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null;
    }
    public boolean addCustomWallpaper(String url) {
        if (!isHttp(url)) return false;
        ArrayList<String> history = Hawk.get(HawkConfig.WALLPAPER_HISTORY, new ArrayList<>());
        if (history.contains(url)) return false;
        history.add(0, url); Hawk.put(HawkConfig.WALLPAPER_HISTORY, history); return true;
    }
    public void removeCustomWallpaper(String url) {
        ArrayList<String> history = Hawk.get(HawkConfig.WALLPAPER_HISTORY, new ArrayList<>());
        if (history.remove(url)) Hawk.put(HawkConfig.WALLPAPER_HISTORY, history);
    }
}
