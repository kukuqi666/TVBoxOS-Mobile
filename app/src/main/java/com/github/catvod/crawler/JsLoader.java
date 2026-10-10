package com.github.catvod.crawler;


import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.util.LOG;
import com.kukuqi.tvbox.osc.util.MD5;
import com.kukuqi.tvbox.osc.util.js.JsSpider;
import com.lzy.okgo.OkGo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;
import okhttp3.Response;

public class JsLoader {
    private static ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Class<?>> classs = new ConcurrentHashMap<>();

    public static void load() {
        for (Spider spider : spiders.values()){
            spider.cancelByTag();
            spider.destroy();
        }
        spiders.clear();
        classs.clear();
    }

    public static void stopAll() {
        for (Spider spider : spiders.values()){
            spider.cancelByTag();
        }
    }

    private boolean loadClassLoader(String jar, String key) {
        boolean success = false;
        Class<?> classInit = null;
        try {
            File cacheDir = new File(App.getInstance().getCacheDir().getAbsolutePath() + "/catvod_jsapi");
            if (!cacheDir.exists())
                cacheDir.mkdirs();
            if (!new File(jar).setReadOnly()) throw new java.io.IOException("模块文件无法设为只读");
            DexClassLoader classLoader = new DexClassLoader(jar, cacheDir.getAbsolutePath(), null, App.getInstance().getClassLoader());
            // make force wait here, some device async dex load
            int count = 0;
            do {
                try {
                    classInit = classLoader.loadClass("com.github.catvod.js.Method");
                    if (classInit != null) {
                        System.out.println("自定义jsapi加载成功!");
                        success = true;
                        break;
                    }
                    Thread.sleep(200);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                count++;
            } while (count < 5);

            if (success) {
                classs.put(key, classInit);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return success;
    }

    private Class<?> loadJarInternal(String jar, String md5, String key) {
        if (classs.contains(key))
            return classs.get(key);
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/" + key + ".jar");
        if (!md5.isEmpty()) {
            if (cache.exists() && MD5.getFileMd5(cache).equalsIgnoreCase(md5)) {
                loadClassLoader(cache.getAbsolutePath(), key);
                return classs.get(key);
            }
        }
        try {
            if (com.kukuqi.tvbox.osc.util.SourceManager.isLocal(jar)) {
                com.kukuqi.tvbox.osc.util.SourceManager.copyLocal(jar, cache);
            } else try (Response response = OkGo.<File>get(jar).execute()) {
                if (!response.isSuccessful() || response.body() == null) throw new java.io.IOException("模块下载失败");
                if (cache.exists() && !cache.delete()) throw new java.io.IOException("旧模块缓存无法替换");
                try (InputStream input = response.body().byteStream(); OutputStream output = new FileOutputStream(cache)) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
            }
            if (!md5.isEmpty() && !md5.equalsIgnoreCase(MD5.getFileMd5(cache))) throw new java.io.IOException("模块校验失败");
            loadClassLoader(cache.getAbsolutePath(), key);
            return classs.get(key);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }
    private volatile String recentJarKey = "";


    public Spider getSpider(String key, String api, String ext, String jar) {
        Class<?> classLoader = null;
        if (!jar.isEmpty()) {
            String[] urls = jar.split(";md5;");
            String jarUrl = urls[0];
            String jarKey = MD5.string2MD5(jarUrl);
            String jarMd5 = urls.length > 1 ? urls[1].trim() : "";
            classLoader = loadJarInternal(jarUrl, jarMd5, jarKey);
        }
        recentJarKey = key;
        if (spiders.containsKey(key))
            return spiders.get(key);
        try {
            Spider sp = new JsSpider(key, api, classLoader);
            sp.init(App.getInstance(), ext);
            spiders.put(key, sp);
            return sp;
        } catch (Throwable th) {
            th.printStackTrace();
            LOG.e("QuJS", th);
        }
        return new SpiderNull();
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            Spider proxyFun = spiders.get(recentJarKey);
            if (proxyFun != null) {
                return proxyFun.proxyLocal(params);
            }
        } catch (Throwable th) {
            LOG.e("proxyInvoke", th);
        }
        return null;
    }
}
