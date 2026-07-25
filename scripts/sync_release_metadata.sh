#!/usr/bin/env bash
set -euo pipefail

skip_version_check=false
if [[ "${1:-}" == "--skip-version-check" ]]; then
  skip_version_check=true
  shift
fi

tag_name="${1:?Usage: sync_release_metadata.sh [--skip-version-check] vX.Y.Z}"
version="${tag_name#v}"
repository="${GITHUB_REPOSITORY:-kukuqi666/TVBoxOS-Mobile}"
app_version=$(sed -nE "s/^[[:space:]]*versionName '([^']+)'/\1/p" app/build.gradle)

if [[ "$tag_name" != v* || ! "$version" =~ ^[0-9]+(\.[0-9]+)+$ ]]; then
  echo "Expected a tag in the form vX.Y.Z, got: $tag_name" >&2
  exit 1
fi

if [[ "$skip_version_check" == false && "$app_version" != "$version" ]]; then
  echo "Tag $tag_name does not match app version $app_version" >&2
  exit 1
fi

apk_url="https://gh.xxooo.cf/https://github.com/${repository}/releases/download/${tag_name}/TVBox-Mobile-v${version}.apk"
readme_apk_url="https://gh.xxooo.cf/${apk_url}"

printf '{\n  "version": "%s",\n  "apk_url": "%s"\n}\n' "$version" "$apk_url" > update.json

if ! grep -q 'TVbox-Mobile：' README.md; then
  echo "README download link was not found" >&2
  exit 1
fi

README_APK_URL="$readme_apk_url" perl -0pi -e \
  's{https://gh\.xxooo\.cf/https://github\.com/[^/]+/[^/]+/releases/download/v[0-9.]+/TVBox-Mobile-v[0-9.]+\.apk}{$ENV{README_APK_URL}}g' README.md

if ! grep -q "TVBox Mobile v${version}" README.md; then
  release_date=$(date -u +%Y/%m/%d)
  entry=">* **${release_date} TVBox Mobile v${version}：** 同步发布 Android APK、应用内更新清单和下载链接。\n>\n"
  temp_file=$(mktemp)
  while IFS= read -r line || [[ -n "$line" ]]; do
    printf '%s\n' "$line"
    if [[ "$line" == "## 𝟭. 更新记录" ]]; then
      printf '\n%b\n' "$entry"
    fi
  done < README.md > "$temp_file"
  mv "$temp_file" README.md
fi
