package com.github.tvbox.osc.ui.dialog;

import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.blankj.utilcode.util.ToastUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.DefaultConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Response;
import com.lxj.xpopup.core.BottomPopupView;
import com.google.android.material.button.MaterialButton;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class AboutDialog extends BottomPopupView {
    // Primary acceleration proxy for all GitHub accesses
    private static final String ACCEL_PRIMARY = "https://gh.xxooo.cf/";
    // Secondary acceleration mirror when primary is unavailable
    private static final String ACCEL_FALLBACK = "https://gh-proxy.com/";

    private static final String RAW_UPDATE_JSON = "https://raw.githubusercontent.com/kukuqi666/TVBoxOS-Mobile/main/update.json";

    private LinearLayout downloadProgressLayout;
    private ProgressBar downloadProgressBar;
    private TextView downloadProgressText;
    private MaterialButton checkUpdateButton;
    private boolean downloadingUpdate;

    public AboutDialog(@NonNull @NotNull Context context) {
        super(context);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_about;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        findViewById(R.id.iv_close).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
        ((TextView) findViewById(R.id.tv_about_version)).setText("Version " + DefaultConfig.getAppVersionName(getContext()));
        downloadProgressLayout = findViewById(R.id.layout_update_progress);
        downloadProgressBar = findViewById(R.id.pb_update_progress);
        downloadProgressText = findViewById(R.id.tv_update_progress);
        checkUpdateButton = findViewById(R.id.btn_check_update);
        checkUpdateButton.setOnClickListener(view -> checkForUpdate());
    }

    // ---- update check ----

    private void checkForUpdate() {
        ToastUtils.showShort("正在检查更新");
        fetchManifest(ACCEL_PRIMARY + RAW_UPDATE_JSON);
    }

    private void fetchManifest(String manifestUrl) {
        OkGo.<String>get(manifestUrl)
                .headers("User-Agent", "TVBox-Mobile")
                .tag("check_update")
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() == null)
                            throw new IllegalStateException("更新信息为空");
                        return response.body().string();
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            JsonObject manifest = new JsonParser().parse(response.body()).getAsJsonObject();
                            String remoteVersion = normalizeVersion(requiredString(manifest, "version"));
                            String apkUrl = requiredString(manifest, "apk_url");
                            if (compareVersion(remoteVersion, DefaultConfig.getAppVersionName(getContext())) > 0) {
                                new com.lxj.xpopup.XPopup.Builder(getContext())
                                        .asConfirm("发现新版本",
                                                "当前版本：v" + DefaultConfig.getAppVersionName(getContext())
                                                        + "\n最新版本：v" + remoteVersion + "\n是否下载并安装？",
                                                () -> downloadAndInstall(apkUrl, remoteVersion))
                                        .show();
                            } else {
                                ToastUtils.showShort("当前已是最新版本");
                            }
                        } catch (Throwable throwable) {
                            retryWithFallbackManifest(manifestUrl);
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        retryWithFallbackManifest(manifestUrl);
                    }
                });
    }

    private void retryWithFallbackManifest(String failedUrl) {
        if (failedUrl.startsWith(ACCEL_PRIMARY)) {
            fetchManifest(ACCEL_FALLBACK + RAW_UPDATE_JSON);
        } else {
            ToastUtils.showLong("无法获取更新信息，请检查网络后重试");
        }
    }

    // ---- download ----

    private void downloadAndInstall(String apkUrl, String version) {
        downloadingUpdate = true;
        // Ensure the URL is accelerated
        String acceleratedUrl = ensureAccelerated(apkUrl);
        // Parse the raw GitHub URL for fallback
        String rawUrl = extractRawUrl(apkUrl);
        if (rawUrl == null) rawUrl = apkUrl;
        downloadApk(acceleratedUrl, rawUrl, version);
    }

    private void downloadApk(String acceleratedUrl, String rawUrl, String version) {
        OkGo.<File>get(acceleratedUrl)
                .headers("User-Agent", "TVBox-Mobile")
                .tag("download_update")
                .execute(new FileCallback() {
                    @Override
                    public void onStart(com.lzy.okgo.request.base.Request<File, ? extends com.lzy.okgo.request.base.Request> request) {
                        showDownloadProgress(0, -1);
                    }

                    @Override
                    public void downloadProgress(long currentSize, long totalSize, float progress, long networkSpeed) {
                        super.downloadProgress(currentSize, totalSize, progress, networkSpeed);
                        showDownloadProgress(currentSize, totalSize);
                    }

                    @Override
                    public File convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() == null)
                            throw new IllegalStateException("更新包为空");
                        File directory = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                        if (directory == null) {
                            directory = getContext().getCacheDir();
                        }
                        File apk = new File(directory, "TVBox-Mobile-v" + version + ".apk");
                        if (apk.exists()) apk.delete();
                        InputStream is = response.body().byteStream();
                        FileOutputStream os = new FileOutputStream(apk);
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = is.read(buf)) > 0) {
                            os.write(buf, 0, len);
                        }
                        os.flush();
                        os.close();
                        is.close();
                        if (!apk.exists() || apk.length() < 1024) {
                            throw new IllegalStateException("更新包格式无效");
                        }
                        return apk;
                    }

                    @Override
                    public void onSuccess(Response<File> response) {
                        if (response.body() != null && response.body().exists()) {
                            showDownloadComplete();
                            installApk(response.body());
                        } else {
                            retryDownloadFallback(rawUrl, version, acceleratedUrl);
                        }
                    }

                    @Override
                    public void onError(Response<File> response) {
                        super.onError(response);
                        retryDownloadFallback(rawUrl, version, acceleratedUrl);
                    }
                });
    }

    private void retryDownloadFallback(String rawUrl, String version, String failedUrl) {
        // If primary acceleration failed, try the secondary one
        if (failedUrl.startsWith(ACCEL_PRIMARY)) {
            downloadApk(ACCEL_FALLBACK + rawUrl, rawUrl, version);
        } else {
            resetDownloadProgress();
            ToastUtils.showLong("下载更新失败，请稍后重试");
        }
    }

    // ---- URL helpers ----

    /** Ensure a GitHub URL is routed through the primary accelerator. */
    private static String ensureAccelerated(String url) {
        if (url == null) return url;
        if (url.startsWith("https://github.com/")) {
            return ACCEL_PRIMARY + url;
        }
        // Already accelerated or not a GitHub URL
        return url;
    }

    /** Extract the raw GitHub URL from an accelerated or direct URL. */
    private static String extractRawUrl(String url) {
        if (url == null) return null;
        if (url.startsWith(ACCEL_PRIMARY)) {
            return url.substring(ACCEL_PRIMARY.length());
        }
        if (url.startsWith(ACCEL_FALLBACK)) {
            return url.substring(ACCEL_FALLBACK.length());
        }
        return url;
    }

    // ---- progress UI ----

    private void showDownloadProgress(long downloaded, long total) {
        downloadProgressText.post(() -> {
            if (!downloadingUpdate) return;
            downloadProgressLayout.setVisibility(VISIBLE);
            checkUpdateButton.setEnabled(false);
            if (total > 0) {
                int percent = (int) Math.min(100, downloaded * 100 / total);
                downloadProgressBar.setIndeterminate(false);
                downloadProgressBar.setProgress(percent);
                downloadProgressText.setText("正在下载 " + Formatter.formatFileSize(getContext(), downloaded)
                        + " / " + Formatter.formatFileSize(getContext(), total) + " (" + percent + "%)");
            } else {
                downloadProgressBar.setIndeterminate(true);
                downloadProgressText.setText("正在下载 " + Formatter.formatFileSize(getContext(), downloaded));
            }
        });
    }

    private void resetDownloadProgress() {
        downloadingUpdate = false;
        downloadProgressLayout.setVisibility(GONE);
        checkUpdateButton.setEnabled(true);
    }

    private void showDownloadComplete() {
        downloadingUpdate = false;
        downloadProgressText.post(() -> {
            downloadProgressLayout.setVisibility(VISIBLE);
            downloadProgressBar.setIndeterminate(false);
            downloadProgressBar.setProgress(100);
            downloadProgressText.setText("下载完成，正在打开安装程序");
        });
    }

    // ---- install ----

    private void installApk(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getContext().getPackageManager().canRequestPackageInstalls()) {
            ToastUtils.showLong("请允许 TVBox Mobile 安装未知应用后重试");
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getContext().getPackageName()));
            getContext().startActivity(settings);
            return;
        }
        Uri apkUri = FileProvider.getUriForFile(getContext(),
                getContext().getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
    }

    // ---- version utilities ----

    private static String requiredString(JsonObject obj, String key) {
        if (!obj.has(key)) throw new IllegalStateException("Missing update field: " + key);
        String val = obj.get(key).getAsString();
        if (val == null || val.trim().isEmpty()) throw new IllegalStateException("Empty update field: " + key);
        return val.trim();
    }

    private String normalizeVersion(String version) {
        if (version == null) return "0";
        return version.trim().replaceFirst("^[vV]", "");
    }

    private int compareVersion(String first, String second) {
        String[] left = normalizeVersion(first).split("\\.");
        String[] right = normalizeVersion(second).split("\\.");
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int leftPart = i < left.length ? parseVersionPart(left[i]) : 0;
            int rightPart = i < right.length ? parseVersionPart(right[i]) : 0;
            if (leftPart != rightPart) return leftPart > rightPart ? 1 : -1;
        }
        return 0;
    }

    private int parseVersionPart(String part) {
        String digits = part.replaceAll("[^0-9].*", "");
        if (digits.isEmpty()) return 0;
        try { return Integer.parseInt(digits); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
