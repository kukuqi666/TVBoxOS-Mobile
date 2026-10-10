#!/usr/bin/env bash
set -euo pipefail
# Compatibility entry point. Metadata must come from a verified release APK.
python3 scripts/release.py sync --manifest "${1:?Usage: sync_release_metadata.sh path/to/release/update.json}"
