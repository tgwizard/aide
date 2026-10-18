# Aide

Personal Pixel phone + Pixel Watch companion app (`com.tgwizard.aide`).

- `phone/` – runs on the phone; does the actual work (locking, status, …).
- `wear/` – runs on the watch; app + "Lock phone" tile. Talks to the phone over the Wearable Data Layer.

Both modules share the same `applicationId` and signing key; the Data Layer only connects them if both match.

## Toolchain

```sh
brew install --cask android-cli
brew install openjdk@21
android sdk install platform-tools
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
```

## Build & install

```sh
./deploy.sh        # release build (R8, debug-signed) → installs on the connected phone + watch
```

Use release builds on the watch: debug Compose builds scroll at ~8 fps on it.
Manual install: `adb -s <serial> install -r {phone,wear}/build/outputs/apk/release/*-release.apk`.

### Signing

Release builds are signed with a dedicated key, kept out of the repo:

- Keystore: `~/.android/aide-release.p12` (PKCS12, alias `aide`).
- Password: macOS Keychain, service `aide-release-keystore` (`security find-generic-password -s aide-release-keystore -w`).
- Backup: both in 1Password. Losing the key means uninstalling/reinstalling Aide (and redoing setup) to update it.

`deploy.sh` exports `AIDE_KEYSTORE` / `AIDE_KEYSTORE_PASSWORD` for Gradle; without them builds fall back to the debug key.
Phone and watch apps must be signed with the same key or the Wearable Data Layer won't connect them.

Pairing over Wi-Fi: on the device, Developer options → Wireless debugging → *Pair device with pairing code*,
then `adb pair <ip>:<pairing-port>` and `adb connect <ip>:<debug-port>`.

## Phone setup

Enable the accessibility service (Settings → Accessibility → Aide), or via adb, appending to whatever is already enabled
(e.g. 1Password). A malformed value makes Android clear the whole list, so check the result:

```sh
cur=$(adb -s <phone> shell settings get secure enabled_accessibility_services | tr -d '\r')
svc=com.tgwizard.aide/com.tgwizard.aide.AideAccessibilityService
# Braces matter in zsh: "$cur:c..." is parsed as a history modifier and eats the ":c".
[ "$cur" = null ] && val="$svc" || val="${cur}:${svc}"
adb -s <phone> shell settings put secure enabled_accessibility_services "$val"
adb -s <phone> shell settings get secure enabled_accessibility_services
```

## Watch tile

Long-press the watch face's tile area to add "Lock phone", or:

```sh
adb -s <watch> shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
  --es operation add-tile --ecn component com.tgwizard.aide/com.tgwizard.aide.LockTileService
```

## Debugging

```sh
adb -s <phone> logcat -s AideWatchListener
adb -s <watch> logcat -s AidePhoneClient
```
