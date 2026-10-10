package com.kukuqi.tvbox.osc.update;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.kukuqi.tvbox.osc.util.UpdateManifest;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;

public final class UpdateInstaller {
    private UpdateInstaller() {}

    @SuppressWarnings("deprecation")
    public static void verifyArchive(Context context, File apk, UpdateManifest manifest) throws Exception {
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
        if (archive == null || !manifest.packageName.equals(archive.packageName)
                || !manifest.version.equals(archive.versionName)
                || versionCode(archive) != manifest.versionCode || versionCode(archive) <= versionCode(installed)) {
            throw new IllegalStateException("更新包的应用或版本不匹配");
        }
        Signature[] incoming = Build.VERSION.SDK_INT >= 28 && archive.signingInfo != null
                ? archive.signingInfo.getApkContentsSigners() : archive.signatures;
        Signature[] current = Build.VERSION.SDK_INT >= 28 && installed.signingInfo != null
                ? installed.signingInfo.getApkContentsSigners() : installed.signatures;
        if (incoming == null || current == null || incoming.length == 0
                || !new HashSet<>(Arrays.asList(incoming)).equals(new HashSet<>(Arrays.asList(current)))) {
            throw new IllegalStateException("签名不一致，请使用官方 Release 版本");
        }
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    /** Called on the UI thread only after the APK has been verified in the background. */
    public static boolean launch(Context context, File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.getPackageManager().canRequestPackageInstalls()) {
            context.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return false;
        }
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apk);
        context.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
        return true;
    }
}
