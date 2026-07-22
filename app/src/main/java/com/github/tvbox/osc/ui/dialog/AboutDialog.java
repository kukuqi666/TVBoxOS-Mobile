package com.github.tvbox.osc.ui.dialog;

import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
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

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;

public class AboutDialog extends BottomPopupView {
    private static final String UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/kukuqi666/TVBoxOS-Mobile/main/update.json";

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
        findViewById(R.id.btn_check_update).setOnClickListener(view -> checkForUpdate());
    }

    private void checkForUpdate() {
        ToastUtils.showShort("正在检查更新");
        OkGo.<String>get(UPDATE_MANIFEST_URL)
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
                            ToastUtils.showLong("更新信息不完整，请稍后重试");
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        ToastUtils.showLong("无法获取更新信息，请检查网络后重试");
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
        ToastUtils.showLong("正在下载 v" + version + "，请稍候");
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
                        FileOutputStream output = new FileOutputStream(apk);
                        output.write(response.body().bytes());
                        output.flush();
                        output.close();
                        return apk;
                    }

                    @Override
                    public void onSuccess(Response<File> response) {
                        if (response.body() != null && response.body().exists()) {
                            installApk(response.body());
                        } else {
                            ToastUtils.showLong("更新包下载失败");
                        }
                    }

                    @Override
                    public void onError(Response<File> response) {
                        super.onError(response);
                        ToastUtils.showLong("下载更新失败，请稍后重试");
                    }
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
