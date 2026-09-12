# Quick Lock

Two Quick Settings tiles that lock the screen. Keeps your normal launcher — this is not a
launcher replacement and changes nothing about your home screen.

| Tile | Icon | Route | Coming back in |
|---|---|---|---|
| **Screen Lock** | fingerprint ridges | accessibility `GLOBAL_ACTION_LOCK_SCREEN` | fingerprint works |
| **Secure Lock** | padlock in a shield | Device Admin `lockNow()` | PIN / pattern / password only |

Use either, both, or neither — they depend on different permissions and work independently.

Built against AGP 9.2.1 / Gradle 9.4.1, `minSdk 28`, `targetSdk 36`.

---

## Why a tile and not a double tap on the home screen

The original goal was to double-tap empty wallpaper on the Pixel home screen. That is not
achievable from an ordinary app, and it is worth writing down why, because the failure is
structural rather than a bug to fix.

To detect a tap on another app's screen you need an overlay window. But **a window that
receives `ACTION_DOWN` consumes it** — returning `false` from an `OnTouchListener` tells
the *View* it didn't handle the event; it does not forward it to the window underneath.
`FLAG_NOT_TOUCH_MODAL` only passes through touches landing *outside* the overlay's bounds.
The one flag that does let touches through, `FLAG_NOT_TOUCHABLE`, also stops you receiving
them.

You cannot both observe a tap and forward it. That is a deliberate anti-tapjacking
boundary in Android. An earlier version of this app tried it anyway and the result was
that app icons under the overlay stopped responding entirely.

There is a second, independent problem. On a Pixel the home screen and the app drawer are
**the same activity in the same package** (`nexuslauncher/.NexusLauncherActivity`) — the
drawer is a view that slides up inside that one window. No accessibility event
distinguishes them, so a `packageName` check is true in both states and the gesture fires
in the drawer too. There is no public API that reports "the Pixel app drawer is open".

A Quick Settings tile sidesteps both problems: it is a surface the system hands the app,
so there is nothing underneath to break and no ambiguity about what the user meant. It
also beats the original idea on its own terms — one tap instead of two, no double-tap
timeout, and it works from inside any app rather than only on the home screen.

The double tap was only ever a workaround for tapping empty wallpaper, where a single tap
would fire constantly by accident. On a dedicated target that reason disappears.

> The full double-tap launcher is still in git history if you ever want it:
> `git show b2626d5`

---

## Why two tiles

`DevicePolicyManager.lockNow()` does not merely lock the screen. It also sets the
framework's `STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW` flag, which tells the keyguard to
accept only PIN, pattern or password on the next unlock and to refuse biometrics — the
same state the phone is in after a reboot or after the periodic strong-auth timeout.

That is deliberate on Android's part: an admin-initiated lock is treated as a security
event. **It cannot be opted out of from the app side.** There is no flag to `lockNow()`
that suppresses it and the system sets it rather than the app, so the only way to get a
biometric-friendly lock is to not use Device Admin for it.

`performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` (API 28+) does not set that flag. It is
the power-button equivalent: screen off, ordinary keyguard, fingerprint works. It has to
live inside an `AccessibilityService` because that is the only place the call is reachable
from — the service here reads no events, resolves no packages and adds no window.

So the PIN-only behaviour is not a defect to route around; it is a different tool. It is
what you want when handing the phone to someone, and not what you want forty times a day.
Each route gets its own tile so the choice is made at tap time.

## Setup

Open **Quick Lock**. Each section is independent — set up whichever tiles you want.

**Screen Lock** (the everyday one)

1. *Enable accessibility service* → pick **Quick Lock** under Installed apps.
   If the toggle is greyed out, first: Settings ▸ Apps ▸ Quick Lock ▸ ⋮ ▸
   **Allow restricted settings**. Android 13+ puts that wall in front of accessibility
   services for sideloaded APKs.
2. *Add Screen Lock tile*.

**Secure Lock** (PIN-only, optional)

1. *Grant Device Admin* — claims exactly one policy, `force-lock` (see
   `res/xml/device_admin.xml`). It cannot wipe the device or change security settings.
2. *Add Secure Lock tile*.

On Android 13+ the add-tile buttons ask the system to place the tile for you. Below that,
swipe down twice → pencil/edit → drag the tile in by hand.

### Uninstalling

Revoke Device Admin from the setup screen first — Android blocks uninstalling an app that
is an active device admin. If you get stuck:

```bash
adb shell dpm remove-active-admin com.amitroy.doubletaplock/.LockAdminReceiver
```

## Lock, not unlock

An app cannot *unlock* the phone. While the device is locked no app is foreground to
receive a tap, and dismissing the keyguard requires a biometric or PIN. Double-tap-to-wake
is a display-controller feature the Pixel 6a does not expose to apps either.

## Building a signed release APK

One-time: create a keystore **outside** the repo and point `keystore.properties` at it.

```bash
mkdir -p ~/keystores
keytool -genkeypair -v \
  -keystore ~/keystores/doubletaptolock.jks \
  -alias doubletaptolock \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Amit Bikram Roy, OU=Development, O=Amit Bikram Roy, L=Dhaka, ST=Dhaka, C=BD"
```

Then `cp keystore.properties.example keystore.properties` and fill it in. The keystore is
PKCS12 (the JDK 17 default) and was created without `-keypass`, so **`keyPassword` must be
the same value as `storePassword`**.

```bash
./gradlew clean :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

If the output is named `app-release-unsigned.apk`, `keystore.properties` was not found.

> **This repo is public.** `*.jks`, `*.keystore` and `keystore.properties` are gitignored
> and must stay that way. A committed keystore plus its password lets anyone publish an
> update signed as you, and git history keeps it even after a later delete. Back the
> `.jks` up somewhere private — lose it and you cannot ship an upgrade to an installed
> copy, only a fresh install under a new signature.

## Layout

```
app/src/main/java/com/amitroy/doubletaplock/
├── LockTileBase.kt              # shared tile behaviour
├── ScreenLockTileService.kt     # "Screen Lock"  -> fingerprint works
├── LockTileService.kt           # "Secure Lock"  -> PIN required
├── LockController.kt            # both lock routes, and why they differ
├── LockAdminReceiver.kt         # claims force-lock only
├── LockAccessibilityService.kt  # capability only; reads nothing
└── SetupActivity.kt             # permissions + add-tile
```
