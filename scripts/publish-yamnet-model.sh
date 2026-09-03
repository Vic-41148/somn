#!/usr/bin/env bash
# Publishes the YAMNet audio model as a GitHub release asset for the in-app runtime download.
#
# The app pins this asset by tag (core:data YamnetModelRepository.MODEL_URL -> releases/download/
# yamnet-v1/yamnet.tflite) and by sha256 (EXPECTED_SHA256). Publish a NEW model as follows:
#   1. Put the new model at model/yamnet.tflite and compute its sha256
#        sha256sum model/yamnet.tflite
#   2. Update EXPECTED_SHA256 in core/data/src/main/java/dev/vic41148/somn/core/data/model/
#      YamnetModelRepository.kt
#   3. Upload it as yamnet-v1 (overwrites only the binary, tag stays stable for old clients);
#      or cut a yamnet-v2 tag and point MODEL_URL at it if you need old clients to keep the
#      pinned digest.
#
# One-time initial publish (executed manually, since the asset must upload with gh available):
#   gh release upload yamnet-v1 model/yamnet.tflite --clobber
set -euo pipefail

if ! command -v gh >/dev/null 2>&1; then
  echo "error: gh (GitHub CLI) is required - https://cli.github.com" >&2
  exit 1
fi
if [[ ! -f model/yamnet.tflite ]]; then
  echo "error: model/yamnet.tflite not found (run from the repo root)" >&2
  exit 1
fi

TAG="yamnet-v1"

# Source-sha256 that the app's EXPECTED_SHA256 must equal before this is safe to publish.
ACTUAL_SHA="$(sha256sum model/yamnet.tflite | awk '{print $1}')"
echo "uploading to ${TAG}: yamnet.tflite (sha256 ${ACTUAL_SHA})"
echo "  keep EXPECTED_SHA256 in YamnetModelRepository.kt in sync with the above."

gh release upload "$TAG" "model/yamnet.tflite" --clobber

echo "done. Download URL: https://github.com/Vic-41148/somn/releases/download/${TAG}/yamnet.tflite"