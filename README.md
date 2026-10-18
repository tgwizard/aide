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

Aide's accessibility service (lock screen, screenshots) is switched on only while the watch uses
it, because apps like BankID refuse to run while an unknown accessibility service is enabled.
That, and reading the foreground app without accessibility, need two one-time grants:

```sh
adb -s <phone> shell pm grant com.tgwizard.aide android.permission.WRITE_SECURE_SETTINGS
adb -s <phone> shell appops set com.tgwizard.aide GET_USAGE_STATS allow
```

Both survive app updates, but not an uninstall. Without the first, enable the service by hand
under Settings → Accessibility → Aide (it then stays on, and BankID will complain).

Aide only ever adds/removes its own entry in `enabled_accessibility_services`; other services
(e.g. 1Password) are left untouched. If you edit that setting via adb yourself, mind that a
malformed value makes Android clear the whole list, and that in zsh `"$cur:c…"` is parsed as a
history modifier; write `"${cur}:…"`.

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
