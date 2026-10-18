# Aide

Personal Pixel phone + Pixel Watch companion app (`com.tgwizard.aide`), built for a Pixel 11 Pro and
Pixel Watch 5. Sideloaded via adb; not on the Play Store.

## What it does

On the watch (app, plus a "Lock phone" tile):

- **Lock phone**: one tap on the ring (app or tile).
- **Door**: Verisure/Yale smart lock status; tap to lock, press and hold 1.5 s to unlock.
- **Home**: Verisure door/window sensors ("All closed" / which are open) and temperature; tap a row for details.
- **Phone**: foreground app, locked/screen state, battery, and a live screen thumbnail while the screen is on.

The tile's AIDE button opens the app.

## How it works

- `phone/` does the work: locking, status, screenshots, Verisure.
- `wear/` is UI only. It sends RPCs to the phone over the Wearable Data Layer (`MessageClient.sendRequest`).
  The phone advertises the `aide_phone` capability so the watch can find it.

| Path | Phone does |
|---|---|
| `/aide/lock` | Lock the screen (accessibility service) |
| `/aide/status` | Foreground app, lock/screen state, battery (JSON) |
| `/aide/screen` | Screenshot as a small JPEG (accessibility service) |
| `/aide/door/status`, `/aide/door/lock`, `/aide/door/unlock` | Verisure smart lock (JSON) |
| `/aide/home` | Verisure door/window sensors and climate (JSON) |

The paths live in `Protocol.kt`, duplicated in both modules; keep them in sync.
The watch only polls while its app is in the foreground.

Both modules share the same `applicationId` and signing key; the Data Layer only connects them if both match.

### Accessibility on demand (and BankID)

Locking and screenshots need an accessibility service. But BankID (and likely other banking apps)
refuses to run while an unknown accessibility service is enabled. So Aide keeps its service **off**
and `AccessibilityToggle` switches it on only when needed:

- for a lock, then off again after 2 s;
- while the watch shows the screen thumbnail, then off 15 s after the last screenshot.

If it ever finds itself left on, it switches off after 15 s. The foreground app comes from usage
stats instead, so that works with the service off.

BankID also refuses to run while **wireless debugging** is on. Turn it off when not developing.

### Verisure

There is no official API. `phone/.../verisure/` is a small Kotlin port of
[vsure](https://github.com/persandstrom/python-verisure), the library Home Assistant uses:

- Login with email and password, plus MFA via SMS/email. A trust cookie skips MFA on later logins.
- Sessions refresh every 10 min, with a re-login if that fails.
- GraphQL calls: `SmartLock`, `DoorLock`/`DoorUnlock` + `pollLockState`, `DoorWindow`, `Climate`.
- All calls are serialized. Lock status and sensors are cached for 60 s to stay clear of rate limits.

The API can change or break without notice.

Set it up in the phone app under **Door · Verisure**: log in, enter the verification code, pick the
lock, and enter the door code (Verisure requires it for lock/unlock).

## Security notes

- **Stored on the phone:** Verisure email, password, door code and session cookies, encrypted with
  AES-GCM. The key is hardware-backed in the Android Keystore and can't be exported. Backups are
  disabled, and release builds aren't debuggable.
- **The password is kept on purpose,** so the phone can log in again without you. It can't require
  the phone to be unlocked, because unlocking the door from the watch has to work with the phone in a pocket.
- **Unlocking needs the watch app and a 1.5 s hold.** The tile can't unlock. Keep a screen lock and
  wrist detection on the watch.
- **The accessibility service can screenshot anything,** but it's only on while in use (see above).
  Thumbnails only travel to the watch.
- **Wireless debugging** gives whoever has this Mac's adb key full control of both devices. Keep it
  off when not developing.
- **No secrets are in this repo.**

## Toolchain

```sh
brew install --cask android-cli
brew install openjdk@21
android sdk install platform-tools
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
```

## Devices

Pair over Wi-Fi on each device: Developer options → Wireless debugging → *Pair device with pairing code*, then

```sh
adb pair <ip>:<pairing-port>
adb connect <ip>:<debug-port>   # usually automatic via mDNS
```

## Build & install

```sh
./deploy.sh
```

This builds release APKs (R8, release-signed), installs them on the phone and watch (found by model
name in `adb devices -l`), and AOT-compiles the watch app.

Use release builds on the watch; debug Compose builds scroll at ~8 fps on it.

Manual install: `adb -s <serial> install -r {phone,wear}/build/outputs/apk/release/*-release.apk`.

### Signing

Release builds are signed with a dedicated key, kept out of the repo:

- Keystore: `~/.android/aide-release.p12` (PKCS12, alias `aide`).
- Password: macOS Keychain, service `aide-release-keystore` (`security find-generic-password -s aide-release-keystore -w`).
- Backup: both in 1Password. If the key is lost, updating Aide means uninstalling and reinstalling it and redoing setup.

`deploy.sh` exports `AIDE_KEYSTORE` / `AIDE_KEYSTORE_PASSWORD` for Gradle; without them builds fall back
to the debug key.

Switching keys also means uninstalling first: Android won't update an app signed with a different key.

## Phone setup (after a fresh install)

1. Two one-time grants, for accessibility on demand and usage stats:

   ```sh
   adb -s <phone> shell pm grant com.tgwizard.aide android.permission.WRITE_SECURE_SETTINGS
   adb -s <phone> shell appops set com.tgwizard.aide GET_USAGE_STATS allow
   ```

   Both survive app updates, but not an uninstall. Without the first, enable the service by hand
   under Settings → Accessibility → Aide. It then stays on, and BankID will refuse to run.

2. Set up Verisure in the phone app (see above).

Aide only ever adds or removes its own entry in `enabled_accessibility_services`; other services
(e.g. 1Password) are left untouched. If you edit that setting via adb yourself:

- A malformed value makes Android clear the whole list.
- In zsh, `"$cur:c…"` is parsed as a history modifier; write `"${cur}:…"`.

## Watch tile

Long-press the watch face's tile area to add "Lock phone", or:

```sh
adb -s <watch> shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
  --es operation add-tile --ecn component com.tgwizard.aide/com.tgwizard.aide.LockTileService
```

## Debugging

```sh
adb -s <phone> logcat -s AideWatchListener AideA11yToggle AideAccessibility AideDoor AideVerisure
adb -s <watch> logcat -s AidePhoneClient AideLockTile
```

Scroll the watch app from adb with crown events; injected swipes are unreliable on the watch:

```sh
adb -s <watch> shell input rotaryencoder scroll --axis SCROLL,-1
```
