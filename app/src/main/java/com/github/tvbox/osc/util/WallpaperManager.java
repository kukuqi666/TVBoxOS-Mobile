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
        if (instance == null) instance = new WallpaperManager();
        return instance;
    }

    private WallpaperManager() {}

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
                int id = Integer.parseInt(pref.substring(11));
                if (id != 0) {
                    rootView.setBackgroundResource(id);
                    return;
                }
            } catch (NumberFormatException ignored) {}
        }

        if (!pref.startsWith("http")) return;

        String cacheKey = MD5.encode(pref);
        File cached = cacheFile(cacheKey);
        if (applyBitmap(rootView, cached)) return;

        OkGo.<File>get(pref).execute(new FileCallback() {
            @Override
            public void onSuccess(Response<File> response) {
                File destination = cacheFile(cacheKey);
                copyFile(response.body(), destination);
                rootView.post(new Runnable() {
                    @Override
                    public void run() {
                        applyBitmap(rootView, destination);
                    }
                });
            }

            @Override
            public void onError(Response<File> response) {
            }
        });
    }

    private File cacheDir() {
        File base = App.getInstance().getExternalCacheDir();
        if (base == null) base = App.getInstance().getFilesDir();
        File dir = new File(base, "wallpapers");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public File getCachedFile(String url) {
        return new File(cacheDir(), MD5.encode(url) + ".wp");
    }

    public boolean isCached(String url) {
        return getCachedFile(url).exists();
    }

    private boolean applyBitmap(View rootView, File file) {
        if (!file.exists()) return false;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) return false;
        rootView.setBackground(new BitmapDrawable(rootView.getResources(), bitmap));
        return true;
    }

    private void copyFile(File source, File destination) {
        try {
            FileInputStream input = new FileInputStream(source);
            FileOutputStream output = new FileOutputStream(destination);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
            input.close();
            output.close();
        } catch (Exception ignored) {
        }
    }

    public List<WallpaperItem> getBuiltInWallpapers() {
        List<WallpaperItem> list = new ArrayList<WallpaperItem>();
        android.content.res.Resources res = App.getInstance().getResources();
        String pkg = App.getInstance().getPackageName();
        list.add(new WallpaperItem("深邃蓝", WallpaperItem.TYPE_BUILTIN, null,
                res.getIdentifier("wallpaper_gradient_blue", "drawable", pkg)));
        list.add(new WallpaperItem("极夜紫", WallpaperItem.TYPE_BUILTIN, null,
                res.getIdentifier("wallpaper_gradient_night", "drawable", pkg)));
        list.add(new WallpaperItem("日落橙", WallpaperItem.TYPE_BUILTIN, null,
                res.getIdentifier("wallpaper_gradient_sunset", "drawable", pkg)));
        list.add(new WallpaperItem("森林绿", WallpaperItem.TYPE_BUILTIN, null,
                res.getIdentifier("wallpaper_gradient_forest", "drawable", pkg)));
        list.add(new WallpaperItem("海洋蓝", WallpaperItem.TYPE_BUILTIN, null,
                res.getIdentifier("wallpaper_gradient_ocean", "drawable", pkg)));
        list.add(new WallpaperItem("梅子紫", WallpaperItem.TYPE_BUILTIN, null,
                res.getIdentifier("wallpaper_gradient_plum", "drawable", pkg)));
        list.add(new WallpaperItem("默认(无壁纸)", WallpaperItem.TYPE_BUILTIN, null, 0));
        return list;
    }

    public List<WallpaperItem> getSubscriptionWallpapers() {
        List<WallpaperItem> list = new ArrayList<WallpaperItem>();
        String configWallpaper = ApiConfig.get().wallpaper;
        if (configWallpaper == null || configWallpaper.isEmpty()) return list;

        String[] urls = configWallpaper.split(",");
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i].trim();
            if (url.startsWith("http")) {
                list.add(new WallpaperItem("订阅壁纸" + (i + 1),
                        WallpaperItem.TYPE_SUBSCRIPTION, url, 0));
            }
        }
        return list;
    }

    public List<WallpaperItem> getOnlineWallpapers() {
        List<WallpaperItem> list = new ArrayList<WallpaperItem>();
        list.add(new WallpaperItem("随机渐变", WallpaperItem.TYPE_ONLINE,
                "https://jianbian.chuqiuyu.workers.dev", 0));
        list.add(new WallpaperItem("随机风景", WallpaperItem.TYPE_ONLINE,
                "https://picsum.photos/1280/720/?blur=10", 0));
        list.add(new WallpaperItem("随机动漫", WallpaperItem.TYPE_ONLINE,
                "https://www.dmoe.cc/random.php", 0));
        return list;
    }
}
