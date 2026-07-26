package com.github.tvbox.osc.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class WallpaperManager {

    public static class WallpaperItem {
        public static final int TYPE_BUILTIN = 0;
        public static final int TYPE_SUBSCRIPTION = 1;
        public static final int TYPE_ONLINE = 2;

        public String name;
        public String url;
        public int type;
        public int resId;

        public WallpaperItem(String name, int type, String url, int resId) {
            this.name = name;
            this.type = type;
            this.url = url;
            this.resId = resId;
        }
    }

    private static WallpaperManager instance;

    public static synchronized WallpaperManager get() {
        if (instance == null) {
            instance = new WallpaperManager();
        }
        return instance;
    }

    private WallpaperManager() {}

    private File cacheDir() {
        File base = App.getInstance().getExternalCacheDir();
        if (base == null) base = App.getInstance().getFilesDir();
        File dir = new File(base, "wallpapers");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private File cacheFile(String key) {
        return new File(cacheDir(), key + ".wp");
    }

    public void saveWallpaper(WallpaperItem item) {
        if (item.type == WallpaperItem.TYPE_BUILTIN) {
            if (item.resId == 0) {
                Hawk.put(HawkConfig.WALLPAPER_URL, "");
            } else {
                Hawk.put(HawkConfig.WALLPAPER_URL, "drawable://" + item.resId);
            }
        } else if (item.url != null && !item.url.isEmpty()) {
            Hawk.put(HawkConfig.WALLPAPER_URL, item.url);
        }
    }

    public String getWallpaperPref() {
        return Hawk.get(HawkConfig.WALLPAPER_URL, "");
    }

    public void clearWallpaper() {
        Hawk.put(HawkConfig.WALLPAPER_URL, "");
    }

    public void applyToActivity(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root != null) applyToView(root);
    }

    public void applyToView(View rootView) {
        String pref = getWallpaperPref();
        if (pref.isEmpty()) return;

        if (pref.startsWith("drawable://")) {
            try {
                int resId = Integer.parseInt(pref.substring(11));
                if (resId != 0) {
                    rootView.setBackgroundResource(resId);
                    return;
                }
            } catch (NumberFormatException ignored) {}
        }

        String cacheKey = MD5.encode(pref);
        File cached = cacheFile(cacheKey);
        if (cached.exists()) {
            try {
                Bitmap bmp = BitmapFactory.decodeFile(cached.getAbsolutePath());
                if (bmp != null) {
                    rootView.setBackground(new BitmapDrawable(rootView.getResources(), bmp));
                    return;
                }
            } catch (Exception ignored) {}
        }

        if (pref.startsWith("http")) {
            OkGo.<File>get(pref)
                    .execute(new FileCallback() {
                        @Override
                        public void onSuccess(Response<File> response) {
                            try {
                                File src = response.body();
                                File dest = cacheFile(cacheKey);
                                copyFile(src, dest);
                                Bitmap bmp = BitmapFactory.decodeFile(dest.getAbsolutePath());
                                if (bmp != null) {
                                    rootView.post(() -> rootView.setBackground(
                                            new BitmapDrawable(rootView.getResources(), bmp)));
                                }
                            } catch (Exception ignored) {}
                        }
                        @Override
                        public void onError(Response<File> response) {}
                    });
        }
    }

    public boolean isCached(String url) {
        return cacheFile(MD5.encode(url)).exists();
    }

    public File getCachedFile(String url) {
        return cacheFile(MD5.encode(url));
    }

    private void copyFile(File src, File dest) {
        try {
            FileInputStream in = new FileInputStream(src);
            FileOutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            in.close();
            out.close();
        } catch (Exception ignored) {}
    }

    // ---- Wallpaper lists ----

    public List<WallpaperItem> getBuiltInWallpapers() {
        List<WallpaperItem> list = new ArrayList<>();
        String pkg = App.getInstance().getPackageName();
        android.content.res.Resources res = App.getInstance().getResources();
        list.add(new WallpaperItem("深邃蓝", TYPE_BUILTIN, null,
                res.getIdentifier("wallpaper_gradient_blue", "drawable", pkg)));
        list.add(new WallpaperItem("极夜紫", TYPE_BUILTIN, null,
                res.getIdentifier("wallpaper_gradient_night", "drawable", pkg)));
        list.add(new WallpaperItem("日落橙", TYPE_BUILTIN, null,
                res.getIdentifier("wallpaper_gradient_sunset", "drawable", pkg)));
        list.add(new WallpaperItem("森林绿", TYPE_BUILTIN, null,
                res.getIdentifier("wallpaper_gradient_forest", "drawable", pkg)));
        list.add(new WallpaperItem("海洋蓝", TYPE_BUILTIN, null,
                res.getIdentifier("wallpaper_gradient_ocean", "drawable", pkg)));
        list.add(new WallpaperItem("梅子紫", TYPE_BUILTIN, null,
                res.getIdentifier("wallpaper_gradient_plum", "drawable", pkg)));
        list.add(new WallpaperItem("默认(无壁纸)", TYPE_BUILTIN, null, 0));
        return list;
    }

    public List<WallpaperItem> getSubscriptionWallpapers() {
        List<WallpaperItem> list = new ArrayList<>();
        try {
            String configWallpaper = ApiConfig.get().wallpaper;
            if (configWallpaper != null && !configWallpaper.isEmpty()) {
                String[] urls = configWallpaper.split(",");
                for (int i = 0; i < urls.length; i++) {
                    String url = urls[i].trim();
                    if (url.startsWith("http")) {
                        list.add(new WallpaperItem("订阅壁纸" + (i + 1), TYPE_SUBSCRIPTION, url, 0));
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public List<WallpaperItem> getOnlineWallpapers() {
        List<WallpaperItem> list = new ArrayList<>();
        list.add(new WallpaperItem("随机渐变", TYPE_ONLINE,
                "https://jianbian.chuqiuyu.workers.dev", 0));
        list.add(new WallpaperItem("随机风景", TYPE_ONLINE,
                "https://picsum.photos/1280/720/?blur=10", 0));
        list.add(new WallpaperItem("随机动漫", TYPE_ONLINE,
                "https://www.dmoe.cc/random.php", 0));
        return list;
    }
}
