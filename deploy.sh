#!/bin/sh
# Build release APKs and install them on the connected phone and watch.
set -eu
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

serial_for() { adb devices -l | awk -v m="model:$1" '$0 ~ m { print $1; exit }'; }
PHONE=$(serial_for Pixel_11_Pro)
WATCH=$(serial_for Pixel_Watch)

./gradlew -q assembleRelease
[ -n "$PHONE" ] && adb -s "$PHONE" install -r phone/build/outputs/apk/release/phone-release.apk || echo "Phone not connected"
if [ -n "$WATCH" ]; then
    adb -s "$WATCH" install -r wear/build/outputs/apk/release/wear-release.apk
    # Compile ahead of time; the watch CPU is slow enough that JIT warm-up is noticeable.
    adb -s "$WATCH" shell cmd package compile -m speed -f com.tgwizard.aide >/dev/null
else
    echo "Watch not connected"
fi
