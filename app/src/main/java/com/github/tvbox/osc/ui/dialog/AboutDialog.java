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
import com.lzy.okgo.model.Response;
import com.lxj.xpopup.core.BottomPopupView;
import com.google.android.material.button.MaterialButton;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class AboutDialog extends BottomPopupView {
    private static final String UPDATE_MANIFEST_URL = "https://gh.xxooo.cf/https://raw.githubusercontent.com/kukuqi666/TVBoxOS-Mobile/main/update.json";
    private static final String UPDATE_MANIFEST_FALLBACK_URL = "https://raw.githubusercontent.com/kukuqi666/TVBoxOS-Mobile/main/update.json";
    private static final String GITHUB_RELEASE_PREFIX = "https://github.com/";
    private static final String GITHUB_ACCELERATOR_PREFIX = "https://gh.xxooo.cf/https://github.com/";
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

    private void checkForUpdate() {
        ToastUtils.showShort("正在检查更新");
        requestUpdateManifest(UPDATE_MANIFEST_URL, true);
    }

    private void requestUpdateManifest(String manifestUrl, boolean allowFallback) {
        OkGo.<String>get(manifestUrl)
                .headers("User-Agent", "TVBox-Mobile")
                .tag("check_update")
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() == null) {
                            throw new IllegalStateException("更新信息为空");
                        }
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
                                        .asConfirm("发现新版本", "当前版本：v" + DefaultConfig.getAppVersionName(getContext())
                                                + "\n最新版本：v" + remoteVersion + "\n是否下载并安装？", () -> downloadAndInstall(apkUrl, remoteVersion))
                                        .show();
                            } else {
                                ToastUtils.showShort("当前已是最新版本");
                            }
                        } catch (Throwable throwable) {
                            if (allowFallback) {
                                requestUpdateManifest(UPDATE_MANIFEST_FALLBACK_URL, false);
                            } else {
                                ToastUtils.showLong("更新信息不完整，请稍后重试");
                            }
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        if (allowFallback) {
                            requestUpdateManifest(UPDATE_MANIFEST_FALLBACK_URL, false);
                        } else {
                            ToastUtils.showLong("无法获取更新信息，请检查网络后重试");
                        }
                    }
                });
    }

    private String requiredString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            throw new IllegalStateException("Missing update field: " + key);
        }
        String value = object.get(key).getAsString().trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("Empty update field: " + key);
        }
        return value;
    }

    private void downloadAndInstall(String apkUrl, String version) {
        downloadingUpdate = true;
        showDownloadProgress(0, -1);
        String acceleratedUrl = accelerateGitHubUrl(apkUrl);
        downloadUpdatePackage(acceleratedUrl, apkUrl, version, !acceleratedUrl.equals(apkUrl));
    }

    private void downloadUpdatePackage(String apkUrl, String fallbackUrl, String version, boolean allowFallback) {
        OkGo.<File>get(apkUrl)
                .headers("User-Agent", "TVBox-Mobile")
                .tag("download_update")
                .execute(new AbsCallback<File>() {
                    @Override
                    public File convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() == null) {
                            throw new IllegalStateException("更新包为空");
                        }
                        File directory = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                        if (directory == null) {
                            throw new IllegalStateException("无法访问下载目录");
                        }
                        if (!directory.exists() && !directory.mkdirs()) {
                            throw new IllegalStateException("无法创建下载目录");
                        }
                        File apk = new File(directory, "TVBox-Mobile-v" + version + ".apk");
                        long total = response.body().contentLength();
                        long downloaded = 0;
                        int firstByte = -1;
                        int secondByte = -1;
                        byte[] buffer = new byte[8192];
                        try (InputStream input = response.body().byteStream();
                             FileOutputStream output = new FileOutputStream(apk)) {
                            int read;
                            while ((read = input.read(buffer)) != -1) {
                                if (downloaded == 0 && read > 0) {
                                    firstByte = buffer[0] & 0xff;
                                }
                                if (downloaded < 2 && downloaded + read >= 2) {
                                    secondByte = buffer[(int) (1 - downloaded)] & 0xff;
                                }
                                output.write(buffer, 0, read);
                                downloaded += read;
                                showDownloadProgress(downloaded, total);
                            }
                            output.flush();
                            if (downloaded < 2 || firstByte != 'P' || secondByte != 'K') {
                                throw new IllegalStateException("更新包格式无效");
                            }
                        } catch (Throwable throwable) {
                            if (apk.exists()) {
                                apk.delete();
                            }
                            throw throwable;
                        }
                        return apk;
                    }

                    @Override
                    public void onSuccess(Response<File> response) {
                        if (response.body() != null && response.body().exists()) {
                            showDownloadComplete();
                            installApk(response.body());
                        } else {
                            resetDownloadProgress();
                            ToastUtils.showLong("更新包下载失败");
                        }
                    }

                    @Override
                    public void onError(Response<File> response) {
                        super.onError(response);
                        if (allowFallback) {
                            downloadUpdatePackage(fallbackUrl, fallbackUrl, version, false);
                        } else {
                            resetDownloadProgress();
                            ToastUtils.showLong("下载更新失败，请稍后重试");
                        }
                    }
                });
    }

    private String accelerateGitHubUrl(String url) {
        if (url != null && url.startsWith(GITHUB_RELEASE_PREFIX)) {
            return GITHUB_ACCELERATOR_PREFIX + url.substring(GITHUB_RELEASE_PREFIX.length());
        }
        return url;
    }

    private void showDownloadProgress(long downloaded, long total) {
        downloadProgressText.post(() -> {
            if (!downloadingUpdate) {
                return;
            }
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

    private String normalizeVersion(String version) {
        if (version == null) {
            return "0";
        }
        return version.trim().replaceFirst("^[vV]", "");
    }

    private int compareVersion(String first, String second) {
        String[] left = normalizeVersion(first).split("\\.");
        String[] right = normalizeVersion(second).split("\\.");
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int leftPart = i < left.length ? parseVersionPart(left[i]) : 0;
            int rightPart = i < right.length ? parseVersionPart(right[i]) : 0;
            if (leftPart != rightPart) {
                return leftPart > rightPart ? 1 : -1;
            }
        }
        return 0;
    }

    private int parseVersionPart(String part) {
        String digits = part.replaceAll("[^0-9].*", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
