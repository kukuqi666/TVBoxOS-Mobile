#!/usr/bin/env python3
"""Plan releases and derive update metadata from the verified, signed APK."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request


def app_version(root=Path('.')):
    text = (root / 'app/build.gradle').read_text(encoding='utf-8')
    def field(name, pattern):
        match = re.search(r'^\s*' + name + r'\s+' + pattern, text, re.M)
        if not match:
            raise ValueError('Missing or invalid ' + name)
        return match.group(1)
    version = field('versionName', r"'([0-9]+\.[0-9]+\.[0-9]+)'")
    code = int(field('versionCode', r'(\d+)'))
    package = field('applicationId', r"'([^']+)'")
    return version, code, package


def version_tuple(version):
    if not re.fullmatch(r'v?\d+\.\d+\.\d+', version):
        raise ValueError('Expected a semantic version: ' + version)
    return tuple(map(int, version.removeprefix('v').split('.')))


def github_api(path):
    request = urllib.request.Request('https://api.github.com/repos/' + os.environ['GITHUB_REPOSITORY'] + '/' + path,
                                    headers={'Authorization': 'Bearer ' + os.environ['GH_TOKEN'], 'User-Agent': 'TVboxOSC-release',
                                             'Accept': 'application/vnd.github+json'})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise


def plan():
    version, code, _ = app_version()
    tag = 'v' + version
    ref = os.environ['GITHUB_REF']
    if ref.startswith('refs/tags/') and os.environ['GITHUB_REF_NAME'] != tag:
        raise ValueError('Tag must exactly match versionName: ' + tag)
    latest = github_api('releases/latest')
    if latest and version_tuple(latest['tag_name']) > version_tuple(tag):
        raise ValueError('Refusing to release an older version than ' + latest['tag_name'])
    release = github_api('releases/tags/' + tag)
    mode = 'publish'
    if release and not release['draft']:
        manifest = json.loads(Path('update.json').read_text(encoding='utf-8'))
        mode = 'none' if manifest.get('version') == version and manifest.get('sha256') else 'sync'
    else:
        result = subprocess.run(['git', 'rev-list', '-n', '1', tag], capture_output=True, text=True)
        if result.returncode == 0 and result.stdout.strip() != os.environ['GITHUB_SHA']:
            raise ValueError('Existing tag points to another commit; published tags are immutable')
        if latest:
            asset = next((a for a in latest['assets'] if a['name'] == 'update.json'), None)
            if asset:
                subprocess.run(['gh', 'release', 'download', latest['tag_name'], '--pattern', 'update.json',
                                '--dir', 'build/previous-release', '--clobber'], check=True)
                previous = json.loads(Path('build/previous-release/update.json').read_text(encoding='utf-8'))
                if previous.get('version_code', 0) >= code:
                    raise ValueError('versionCode must increase for every release')
    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
        output.write(f'mode={mode}\ntag={tag}\nversion={version}\n')
    print(f'{tag}: {mode}')


def metadata(apk, output, repository, aapt, apksigner):
    version, code, package = app_version()
    if apk.name != f'TVboxOSC-v{version}.apk':
        raise ValueError('Unexpected APK filename: ' + apk.name)
    badging = subprocess.check_output([aapt, 'dump', 'badging', str(apk)], text=True)
    expected = f"package: name='{package}' versionCode='{code}' versionName='{version}'"
    if not badging.startswith(expected):
        raise ValueError('APK package/version differs from app/build.gradle')
    if 'application-debuggable' in badging:
        raise ValueError('Refusing to publish a debuggable APK')
    signatures = subprocess.check_output([apksigner, 'verify', '--verbose', '--print-certs', str(apk)], text=True)
    signer = re.search(r'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]{64})', signatures)
    if not signer:
        raise ValueError('APK has no verified signing certificate')
    with apk.open('rb') as source:
        checksum = hashlib.file_digest(source, 'sha256').hexdigest()
    manifest = {'version': version, 'version_code': code, 'package_name': package,
                'apk_url': f'https://github.com/{repository}/releases/download/v{version}/{apk.name}',
                'sha256': checksum, 'size': apk.stat().st_size, 'signer_sha256': signer.group(1).lower()}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Verified signed APK; generated', output)


def sync(manifest_path, root=Path('.')):
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    version = manifest['version']
    version_tuple(version)
    if not re.fullmatch(r'[a-f0-9]{64}', manifest['sha256']) or manifest['size'] <= 0:
        raise ValueError('Invalid release manifest')
    current_path = root / 'update.json'
    if current_path.exists():
        current = json.loads(current_path.read_text(encoding='utf-8'))
        if version_tuple(current['version']) > version_tuple(version):
            raise ValueError('Refusing to replace a newer update manifest')
    readme_path = root / 'README.md'
    text = readme_path.read_text(encoding='utf-8')
    text, count = re.subn(r'^- TVboxOSC：.*$',
                         f"- TVboxOSC：[下载 v{version}](https://gh-proxy.com/{manifest['apk_url']}) · [所有版本](https://github.com/"
                         + os.environ.get('GITHUB_REPOSITORY', 'kukuqi666/TVboxOSC') + '/releases)', text, count=1, flags=re.M)
    if count != 1:
        raise ValueError('README TVboxOSC download entry missing')
    if f'TVboxOSC v{version}：' not in text:
        date = datetime.datetime.now(datetime.timezone.utc).strftime('%Y/%m/%d')
        summary = ('史诗级大更新：UI 重做，修复已知 Bug；完善来源、壁纸、直播、本地视频与关于页面，新增更新动画、自动检测及 Wi-Fi 后台下载，支持 main 版本变更自动发布。'
                   if version == '3.0.0' else '发布签名 APK，同步应用内更新清单和下载链接。')
        heading = '## 𝟭. 更新记录'
        if heading not in text:
            raise ValueError('README release history heading missing')
        text = text.replace(heading, heading + f'\n\n>* **{date} TVboxOSC v{version}：** {summary}', 1)
    current_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    readme_path.write_text(text, encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('plan')
    create = commands.add_parser('metadata')
    create.add_argument('--apk', type=Path, required=True)
    create.add_argument('--output', type=Path, required=True)
    create.add_argument('--aapt', required=True)
    create.add_argument('--apksigner', required=True)
    synchronize = commands.add_parser('sync')
    synchronize.add_argument('--manifest', type=Path, required=True)
    args = parser.parse_args()
    if args.command == 'plan':
        plan()
    elif args.command == 'metadata':
        metadata(args.apk, args.output, os.environ.get('GITHUB_REPOSITORY', 'kukuqi666/TVboxOSC'), args.aapt, args.apksigner)
    else:
        sync(args.manifest)
