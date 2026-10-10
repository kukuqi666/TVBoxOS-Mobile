package com.kukuqi.tvbox.osc.update;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.kukuqi.tvbox.osc.BuildConfig;
import com.kukuqi.tvbox.osc.util.UpdateManifest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Persistent background work, independent of the About view or current Activity. */
public class UpdateWorker extends Worker {
    private static final String MANIFEST_URL = "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/update.json";
    private static final String[] ROUTES = {"", "https://gh.xxooo.cf/", "https://gh-proxy.com/"};
    private final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(2, TimeUnit.MINUTES).build();
    private volatile Call running;
    private final UpdateStore store;

    public UpdateWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
        store = new UpdateStore(context);
    }

    @NonNull @Override public Result doWork() {
        try {
            return getInputData().getBoolean("download", false) ? download() : check();
        } catch (Exception error) {
            android.util.Log.w("AppUpdate", "Background update failed", error);
            if (!isStopped()) store.state(UpdateStore.ERROR, error instanceof IllegalStateException
                    ? error.getMessage() : "更新暂不可用，请检查网络后重试", 0);
            return !isStopped() && !(error instanceof IllegalStateException) && getRunAttemptCount() < 2 ? Result.retry() : Result.failure();
        } finally { running = null; }
    }

    private Result check() throws Exception {
        if (UpdateStore.DOWNLOADING.equals(store.state()) || UpdateStore.VERIFYING.equals(store.state())) return Result.success();
        boolean ready = store.hasReadyUpdate();
        if (!ready) store.state(UpdateStore.CHECKING, "正在检查新版本…", 0);
        Exception last = null;
        for (String route : ROUTES) {
            if (isStopped()) return Result.failure();
            try (Response response = request(route + MANIFEST_URL + "?t=" + System.currentTimeMillis())) {
                if (!response.isSuccessful() || response.body() == null) throw new IOException("无法连接更新服务");
                String json = response.body().string();
                if (UpdateManifest.legacyIsNotNewer(json, BuildConfig.VERSION_NAME)) {
                    store.prefs.edit().putLong("last_check", System.currentTimeMillis()).apply();
                    if (!ready) store.state(UpdateStore.CURRENT, "当前已是最新版本", 0);
                    return Result.success();
                }
                UpdateManifest manifest = new UpdateManifest(json, getApplicationContext().getPackageName());
                if (isStopped()) return Result.failure();
                store.prefs.edit().putLong("last_check", System.currentTimeMillis()).apply();
                if (!manifest.isNewer(BuildConfig.VERSION_CODE)) {
                    if (!ready) store.state(UpdateStore.CURRENT, "当前已是最新版本", 0);
                    return Result.success();
                }
                UpdateManifest previous = store.manifest();
                if (ready && previous != null && previous.versionCode >= manifest.versionCode) return Result.success();
                store.prefs.edit().putString("manifest", json).apply();
                store.state(UpdateStore.AVAILABLE, store.autoDownload() ? "发现 v" + manifest.version + "，等待 Wi-Fi 后台下载"
                        : "发现 v" + manifest.version + "，可手动下载", 0);
                if (store.autoDownload()) UpdateCoordinator.download(getApplicationContext(), false);
                return Result.success();
            } catch (Exception error) { last = error; }
        }
        if (ready) return Result.success(); // Keep an already verified download available during network outages.
        throw last == null ? new IOException("无法获取更新信息") : last;
    }

    private Result download() throws Exception {
        boolean manual = getInputData().getBoolean("manual", false);
        if (!manual && !store.autoDownload()) return Result.success();
        if (store.hasReadyUpdate()) return Result.success();
        UpdateManifest manifest = store.manifest();
        if (manifest == null || !manifest.isNewer(BuildConfig.VERSION_CODE)) return Result.success();
        File apk = store.apk(manifest);
        File directory = apk.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建下载目录");
        // Distinct files prevent a cancelled Wi-Fi worker from deleting a replacement task's download.
        File part = new File(directory, "TVboxOSC-v" + manifest.version + "." + getId() + ".part.apk");
        Exception last = null;
        for (String route : ROUTES) {
            if (isStopped()) return Result.failure();
            store.state(UpdateStore.DOWNLOADING, "正在后台下载 v" + manifest.version, 0);
            try {
                try (Response response = request(route + manifest.apkUrl)) {
                    if (!response.isSuccessful() || response.body() == null) throw new IOException("无法下载更新包");
                    long downloaded = 0, lastProgress = 0;
                    try (InputStream input = response.body().byteStream(); FileOutputStream output = new FileOutputStream(part)) {
                        byte[] buffer = new byte[32768];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (isStopped()) return Result.failure();
                            downloaded += count;
                            if (downloaded > manifest.size) throw new IOException("更新包大小异常");
                            output.write(buffer, 0, count);
                            long now = System.currentTimeMillis();
                            if (now - lastProgress >= 250) {
                                int percent = (int) (downloaded * 100 / manifest.size);
                                store.state(UpdateStore.DOWNLOADING, "正在后台下载 v" + manifest.version + " · " + percent + "%", percent);
                                lastProgress = now;
                            }
                        }
                    }
                }
                if (isStopped()) return Result.failure();
                store.state(UpdateStore.VERIFYING, "下载完成，正在校验更新包…", 100);
                manifest.verifyDownload(part);
                UpdateInstaller.verifyArchive(getApplicationContext(), part, manifest);
                if (isStopped()) return Result.failure();
                if (apk.exists() && !apk.delete()) throw new IOException("无法替换更新包");
                if (!part.renameTo(apk)) throw new IOException("无法保存更新包");
                store.state(UpdateStore.READY, "v" + manifest.version + " 已准备好，可安装更新", 100);
                UpdateCoordinator.notifyReady(getApplicationContext(), manifest.version);
                return Result.success();
            } catch (Exception error) {
                last = error;
                if (error instanceof IllegalStateException) throw error;
            } finally { part.delete(); }
        }
        throw last == null ? new IOException("下载失败") : last;
    }

    protected Response request(String url) throws IOException {
        running = client.newCall(new Request.Builder().url(url).header("User-Agent", "TVboxOSC")
                .header("Cache-Control", "no-cache").build());
        if (isStopped()) throw new IOException("更新任务已停止");
        return running.execute();
    }

    @Override public void onStopped() {
        Call call = running;
        if (call != null) call.cancel();
        super.onStopped();
    }
}
