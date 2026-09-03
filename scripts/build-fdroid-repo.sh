#!/usr/bin/env bash
#
# Export a self-hosted F-Droid-format repository from the signed release APKs.
#
# Somn distributes its own signed APKs (one dev-controlled key). A self-hosted
# F-Droid mirror publishes those same APKs with a repo index signed by the
# host's own keys - users add the repo URL to F-Droid / Droid-ify / NeoStore
# and the client verifies the APK signature matches the index entry, so every
# channel keeps the same signature. Only official F-Droid (build-from-source)
# re-signs with its own key; this is not that path.
#
# Requires fdroidserver (https://gitlab.com/fdroid/fdroidserver) on PATH:
#   Debian/Ubuntu: apt install fdroidserver
#   Arch:          pacman -S fdroidserver       # python-fdroidserver
#   From source:   see the docs; activate the venv before running this script.
#
# Usage:
#   scripts/build-fdroid-repo.sh [--apk-dir DIR] [--repo-dir DIR]
#
#   --apk-dir   Directory holding the signed release APKs. Default:
#               app/build/outputs/apk/release (after assembleRelease/CI).
#   --repo-dir  Where the F-Droid repo is materialized. Default: release-repo/
#               (gitignored; upload its repo/ subdirectory to any static host).
#
# Non-interactive: fdroid init is run with explicit options so it never prompts.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"

APK_DIR="app/build/outputs/apk/release"
REPO_DIR="release-repo"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk-dir)  APK_DIR="$2"; shift 2 ;;
    --repo-dir) REPO_DIR="$2"; shift 2 ;;
    -h|--help)  sed -n '2,22p' "$0"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

APK_DIR="$(cd "$ROOT/$APK_DIR" 2>/dev/null && pwd)" || { echo "error: APK dir not found: $APK_DIR" >&2; exit 1; }
REPO_DIR="$(cd "$ROOT" && pwd)/$REPO_DIR"

command -v fdroid >/dev/null 2>&1 || {
  echo "error: fdroidserver not found on PATH - install it first (apt install fdroidserver)." >&2
  exit 1
}

shopt -s nullglob
APKS=("$APK_DIR"/somn-*-signed.apk)
[[ ${#APKS[@]} -gt 0 ]] || { echo "error: no somn-*-signed.apk found in $APK_DIR" >&2; exit 1; }
echo "Found ${#APKS[@]} signed APK(s):"
for a in "${APKS[@]}"; do echo "  $a"; done

# fdroid init materializes a repo skeleton plus the host's signing keys
# (fdroid-repo.jks / fdroid-update.jks) and keystore passphrases. Treat the
# whole directory as private: the upload must go to a static host that serves
# HTTPS, and the keystores must never leave your machine.
mkdir -p "$REPO_DIR"
if [[ ! -f "$REPO_DIR/config.yml" ]]; then
  echo "==> fdroid init ($REPO_DIR)"
  (cd "$REPO_DIR" && fdroid init \
    --repo-name "Somn" \
    --repo-url "https://example.invalid/repo" \
    --repo-description "Somn - private, on-device sleep tracking. Replace the repo-url with your static host." \
    --no-prompt)
  # fdroid init asks for the keystore passphrase only when creating keys; run
  # non-interactively is fine as long as config.yml got written. If it still
  # prompted, the generated keystores use the default passphrase printed to
  # stdout above - rotate via `fdroid update --keystore` handling.
fi

echo "==> publishing release APKs into repo"
mkdir -p "$REPO_DIR/repo"
for a in "${APKS[@]}"; do
  cp "$a" "$REPO_DIR/repo/"
done

echo "==> regenerating the repo index"
(cd "$REPO_DIR" && fdroid update --use-metadata --pretty)

echo
echo "Done. Publish:"
echo "  1. Upload $REPO_DIR/repo/ (and its .json/.xml/jar index) to any HTTPS static host"
echo "     - e.g. rsync -av $REPO_DIR/repo/ user@host:/srv/www/somn/fdroid/repo/"
echo "  2. Keep $REPO_DIR/ (config.yml + *.jks + keystore passphrases) private and backed up"
echo "  3. Set the real repo URL in $REPO_DIR/config.yml (repo_url:) and re-run 'fdroid update'"
echo "  4. Users add it in F-Droid: Settings -> Repositories -> Add, or via a URL+qr link"