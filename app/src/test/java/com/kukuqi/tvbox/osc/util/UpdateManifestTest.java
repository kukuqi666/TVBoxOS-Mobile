package com.kukuqi.tvbox.osc.util;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class UpdateManifestTest {
    private static final String PACKAGE = "com.kukuqi.tvbox.osc";
    private static final String JSON = "{\"version\":\"3.0.0\",\"version_code\":300,\"package_name\":\"com.kukuqi.tvbox.osc\","
            + "\"apk_url\":\"https://github.com/kukuqi666/TVboxOSC/releases/download/v3.0.0/TVboxOSC-v3.0.0.apk\","
            + "\"size\":3,\"sha256\":\"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad\"}";

    @Test public void versionCodeControlsUpgradeAndPreventsDowngrade() {
        UpdateManifest manifest = new UpdateManifest(JSON, PACKAGE);
        assertTrue(manifest.isNewer(238));
        assertFalse(manifest.isNewer(300));
        assertFalse(manifest.isNewer(301));
    }
    @Test public void legacyManifestsCannotAuthorizeAnUnverifiedUpgrade() {
        assertTrue(UpdateManifest.legacyIsNotNewer("{\"version\":\"2.1.26\"}", "3.0.0"));
        assertTrue(UpdateManifest.legacyIsNotNewer("{\"version\":\"3.0.0\"}", "3.0.0"));
        assertFalse(UpdateManifest.legacyIsNotNewer("{\"version\":\"3.0.1\"}", "3.0.0"));
        assertFalse(UpdateManifest.legacyIsNotNewer(JSON, "3.0.0"));
    }
    @Test public void refusesWrongPackageAndInsecureUrl() {
        assertThrows(IllegalArgumentException.class, () -> new UpdateManifest(JSON, "com.github.tvbox.osc"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateManifest(JSON.replace("https://", "http://"), PACKAGE));
        assertThrows(RuntimeException.class, () -> new UpdateManifest("{}", PACKAGE));
    }
    @Test public void rejectsMalformedVersionSizeAndChecksum() {
        assertThrows(IllegalArgumentException.class, () -> new UpdateManifest(JSON.replace("3.0.0", "../3"), PACKAGE));
        assertThrows(IllegalArgumentException.class, () -> new UpdateManifest(JSON.replace("\"size\":3", "\"size\":0"), PACKAGE));
        assertThrows(IllegalArgumentException.class, () -> new UpdateManifest(JSON.replace("ba7816", "invalid"), PACKAGE));
    }
    @Test public void verifiesChecksumAndRejectsTruncationAndSameSizeCorruption() throws Exception {
        UpdateManifest manifest = new UpdateManifest(JSON, PACKAGE);
        File file = File.createTempFile("tvbox-update", ".apk");
        try {
            Files.write(file.toPath(), new byte[]{'a','b','c'});
            manifest.verifyDownload(file);
            Files.write(file.toPath(), new byte[]{'a','b'});
            assertThrows(Exception.class, () -> manifest.verifyDownload(file));
            Files.write(file.toPath(), new byte[]{'a','b','d'});
            assertThrows(Exception.class, () -> manifest.verifyDownload(file));
        } finally { file.delete(); }
    }
}
