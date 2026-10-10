import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import release


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / 'app').mkdir()
        (self.root / 'app/build.gradle').write_text("versionCode 300\nversionName '3.0.0'\napplicationId 'com.kukuqi.tvbox.osc'\n")
        (self.root / 'README.md').write_text('# 项目\n- TVboxOSC：[旧下载](https://example.com/old)\n\n## 𝟭. 更新记录\n\n>* **旧记录 v2.1.26**\n', encoding='utf-8')
        (self.root / 'update.json').write_text('{"version":"2.1.26"}')
        self.manifest = {'version': '3.0.0', 'version_code': 300, 'package_name': 'com.kukuqi.tvbox.osc',
                         'apk_url': 'https://github.com/kukuqi666/TVboxOSC/releases/download/v3.0.0/TVboxOSC-v3.0.0.apk',
                         'sha256': hashlib.sha256(b'apk').hexdigest(), 'size': 3}
        self.manifest_path = self.root / 'published.json'
        self.manifest_path.write_text(json.dumps(self.manifest))
    def tearDown(self):
        self.temp.cleanup()
    def test_sync_preserves_history_and_is_idempotent(self):
        release.sync(self.manifest_path, self.root)
        first = (self.root / 'README.md').read_text(encoding='utf-8')
        self.assertIn('史诗级大更新', first)
        self.assertIn('旧记录 v2.1.26', first)
        self.assertIn(f"[下载 v3.0.0](https://gh-proxy.com/{self.manifest['apk_url']})", first)
        self.assertEqual(self.manifest, json.loads((self.root / 'update.json').read_text()))
        release.sync(self.manifest_path, self.root)
        self.assertEqual(first, (self.root / 'README.md').read_text(encoding='utf-8'))
    def test_sync_existing_release_keeps_update_history_unchanged(self):
        readme_path = self.root / 'README.md'
        heading = '## 𝟭. 更新记录'
        original = readme_path.read_text(encoding='utf-8').replace(
            heading, heading + '\n\n>* **2026/10/10 TVboxOSC v3.0.0：** 原有更新说明。')
        readme_path.write_text(original, encoding='utf-8')
        release.sync(self.manifest_path, self.root)
        updated = readme_path.read_text(encoding='utf-8')
        self.assertEqual(original.split(heading, 1)[1], updated.split(heading, 1)[1])
        self.assertIn(f"(https://gh-proxy.com/{self.manifest['apk_url']})", updated)
        self.assertEqual(self.manifest, json.loads((self.root / 'update.json').read_text()))
    def test_sync_never_downgrades_main(self):
        (self.root / 'update.json').write_text('{"version":"3.0.1"}')
        with self.assertRaises(ValueError): release.sync(self.manifest_path, self.root)
    def test_metadata_refuses_wrong_package_or_debug_apk(self):
        apk = self.root / 'TVboxOSC-v3.0.0.apk'
        apk.write_bytes(b'apk')
        with patch('release.app_version', return_value=('3.0.0', 300, 'com.kukuqi.tvbox.osc')):
            with patch('release.subprocess.check_output', return_value="package: name='wrong'"):
                with self.assertRaises(ValueError): release.metadata(apk, self.manifest_path, 'owner/repo', 'aapt', 'apksigner')
            with patch('release.subprocess.check_output', return_value="package: name='com.kukuqi.tvbox.osc' versionCode='300' versionName='3.0.0'\napplication-debuggable"):
                with self.assertRaises(ValueError): release.metadata(apk, self.manifest_path, 'owner/repo', 'aapt', 'apksigner')
    def test_metadata_uses_real_file_and_signed_certificate(self):
        apk = self.root / 'TVboxOSC-v3.0.0.apk'
        apk.write_bytes(b'actual signed bytes')
        outputs = ["package: name='com.kukuqi.tvbox.osc' versionCode='300' versionName='3.0.0'", 'Signer #1 certificate SHA-256 digest: ' + 'a' * 64]
        with patch('release.app_version', return_value=('3.0.0', 300, 'com.kukuqi.tvbox.osc')), patch('release.subprocess.check_output', side_effect=outputs):
            release.metadata(apk, self.manifest_path, 'owner/repo', 'aapt', 'apksigner')
        manifest = json.loads(self.manifest_path.read_text())
        self.assertEqual(hashlib.sha256(apk.read_bytes()).hexdigest(), manifest['sha256'])
        self.assertEqual(apk.stat().st_size, manifest['size'])
        self.assertEqual('a' * 64, manifest['signer_sha256'])
        self.assertIn('/owner/repo/', manifest['apk_url'])
    def test_tag_mismatch_fails_before_github_access(self):
        with (patch('release.app_version', return_value=('3.0.0', 300, 'com.kukuqi.tvbox.osc')),
              patch.dict(os.environ, {'GITHUB_REF': 'refs/tags/v3.0.1', 'GITHUB_REF_NAME': 'v3.0.1'})):
            with self.assertRaises(ValueError): release.plan()
    def test_main_bump_publishes_and_unchanged_version_skips(self):
        import subprocess
        output = self.root / 'outputs'
        with (patch('release.app_version', return_value=('3.0.0', 300, 'com.kukuqi.tvbox.osc')),
              patch.dict(os.environ, {'GITHUB_REF': 'refs/heads/main', 'GITHUB_SHA': 'commit', 'GITHUB_OUTPUT': str(output)}),
              patch('release.github_api', side_effect=[{'tag_name':'v2.1.26','assets':[]}, None]),
              patch('release.subprocess.run', return_value=subprocess.CompletedProcess([], 1, '', ''))):
            release.plan()
        self.assertIn('mode=publish', output.read_text())
        output.write_text('')
        with (patch('release.app_version', return_value=('3.0.0', 300, 'com.kukuqi.tvbox.osc')),
              patch.dict(os.environ, {'GITHUB_REF': 'refs/heads/main', 'GITHUB_OUTPUT': str(output)}),
              patch('release.github_api', side_effect=[{'tag_name':'v3.0.0'}, {'draft':False}]),
              patch('release.Path.read_text', return_value=json.dumps(self.manifest))):
            release.plan()
        self.assertIn('mode=none', output.read_text())


if __name__ == '__main__': unittest.main()
