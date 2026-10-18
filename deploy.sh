#!/bin/sh
# Build release APKs and install them on the connected phone and watch.
set -eu
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

# Release signing key: file in ~/.android, password in the macOS Keychain (see README).
KEYSTORE="$HOME/.android/aide-release.p12"
if [ -f "$KEYSTORE" ]; then
    export AIDE_KEYSTORE="$KEYSTORE"
    AIDE_KEYSTORE_PASSWORD=$(security find-generic-password -a "$USER" -s aide-release-keystore -w)
    export AIDE_KEYSTORE_PASSWORD
else
    echo "No $KEYSTORE; signing with the debug key"
fi

serial_for() { adb devices -l | awk -v m="model:$1" '$0 ~ m { print $1; exit }'; }
PHONE=$(serial_for Pixel_11_Pro)
WATCH=$(serial_for Pixel_Watch)

./gradlew -q assembleRelease
if [ -n "$PHONE" ]; then
    adb -s "$PHONE" install -r phone/build/outputs/apk/release/phone-release.apk
else
    echo "Phone not connected"
fi
if [ -n "$WATCH" ]; then
    adb -s "$WATCH" install -r wear/build/outputs/apk/release/wear-release.apk
    # Compile ahead of time; the watch CPU is slow enough that JIT warm-up is noticeable.
    adb -s "$WATCH" shell cmd package compile -m speed -f com.tgwizard.aide >/dev/null
else
    echo "Watch not connected"
fi
