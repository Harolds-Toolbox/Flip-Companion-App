#!/usr/bin/env bash
set -euo pipefail

: "${PABI_FLIP_KEYSTORE:?Set PABI_FLIP_KEYSTORE to the private release keystore path}"
: "${PABI_FLIP_STORE_PASSWORD:?Set PABI_FLIP_STORE_PASSWORD}"
: "${PABI_FLIP_KEY_ALIAS:?Set PABI_FLIP_KEY_ALIAS}"
: "${PABI_FLIP_KEY_PASSWORD:?Set PABI_FLIP_KEY_PASSWORD}"

cd "$(dirname "$0")/.."
./gradlew :app:assembleRelease

apk="app/build/outputs/apk/release/app-release.apk"
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
"$sdk_root/build-tools/36.0.0/apksigner" verify --verbose "$apk"
printf 'Signed release APK: %s\n' "$apk"
