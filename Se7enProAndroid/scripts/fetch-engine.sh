#!/bin/bash
# Fetches the official sing-box engine binaries (per ABI) into app/src/main/jniLibs/.
# Run from the project root:  bash scripts/fetch-engine.sh
# Requires: curl + tar
set -e

SB_VERSION="1.14.4"
BASE="https://github.com/SagerNet/sing-box/releases/download/v${SB_VERSION}"
DEST="$(dirname "$0")/../app/src/main/jniLibs"

declare -A ABIS=(
  ["arm64-v8a"]="android-arm64"
  ["armeabi-v7a"]="android-arm"
  ["x86_64"]="android-amd64"
)

for abi in arm64-v8a armeabi-v7a x86_64; do
  target="${ABIS[$abi]}"
  url="${BASE}/sing-box-${SB_VERSION}-${target}.tar.gz"
  tmp="$(mktemp -d)"
  echo ">> $abi  <-  $url"
  curl -fL --retry 3 -o "$tmp/engine.tar.gz" "$url"
  tar xzf "$tmp/engine.tar.gz" -C "$tmp"
  mkdir -p "$DEST/$abi"
  cp "$tmp/sing-box-${SB_VERSION}-${target}/sing-box" "$DEST/$abi/libsingbox.so"
  chmod +x "$DEST/$abi/libsingbox.so"
  rm -rf "$tmp"
done

echo "Done. Binaries placed in app/src/main/jniLibs/<abi>/libsingbox.so"
