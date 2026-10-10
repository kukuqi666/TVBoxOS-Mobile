package com.kukuqi.tvbox.osc.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Locale;

/** The same contract is generated from the signed APK by the release workflow. */
public final class UpdateManifest {
    public final String version, apkUrl, packageName, sha256;
    public final long versionCode, size;

    public UpdateManifest(String json, String expectedPackage) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        version = required(object, "version");
        apkUrl = required(object, "apk_url");
        packageName = required(object, "package_name");
        sha256 = required(object, "sha256").toLowerCase(Locale.ROOT);
        versionCode = object.get("version_code").getAsLong();
        size = object.get("size").getAsLong();
        URI uri = URI.create(apkUrl);
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || versionCode <= 0
                || size <= 0 || !sha256.matches("[a-f0-9]{64}")
                || !expectedPackage.equals(packageName)
                || !"https".equals(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("更新清单格式无效或不适用于本应用");
        }
    }

    public boolean isNewer(long installedCode) { return versionCode > installedCode; }

    /** Old manifests have no checksum or versionCode; they may confirm an older version, never authorize installation. */
    public static boolean legacyIsNotNewer(String json, String installedVersion) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        if (object.has("version_code")) return false;
        String version = required(object, "version").replaceFirst("^[vV]", "");
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) return false;
        String[] remote = version.split("\\."), installed = installedVersion.split("\\.");
        for (int i = 0; i < 3; i++) {
            int comparison = Integer.compare(Integer.parseInt(remote[i]), Integer.parseInt(installed[i]));
            if (comparison != 0) return comparison < 0;
        }
        return true;
    }

    public void verifyDownload(File apk) throws Exception {
        if (apk.length() != size) throw new IOException("更新包大小不完整");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(apk)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        if (!sha256.equals(hash.toString())) throw new IOException("更新包校验失败，请重新下载");
    }

    private static String required(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) throw new IllegalArgumentException("更新清单格式不完整");
        String value = object.get(name).getAsString().trim();
        if (value.isEmpty()) throw new IllegalArgumentException("缺少更新信息：" + name);
        return value;
    }
}
