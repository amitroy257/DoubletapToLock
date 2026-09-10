# Quick Lock

A Quick Settings tile that locks the screen. Keeps your normal launcher — this is not a
launcher replacement and changes nothing about your home screen.

Swipe down, tap **Lock**, screen off. Works from inside any app.

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

## Lock, not unlock

An app cannot unlock the phone. While the device is locked no app is foreground to receive
a tap, and dismissing the keyguard requires a biometric or PIN. Double-tap-to-wake is a
display-controller feature the Pixel 6a does not expose to apps either.

---

## Setup

1. Install and open **Quick Lock**.
2. **Grant Device Admin** — this is the permission that allows `lockNow()`.
3. **Add tile to Quick Settings** (Android 13+ places it for you). Otherwise: swipe down
   twice → pencil/edit → drag **Lock** into your active tiles.

### Device Admin vs the accessibility service

Device Admin is the default because it is one toggle and is not affected by the
"Restricted setting" wall Android 13+ puts in front of accessibility services for
sideloaded APKs. It claims exactly one policy, `force-lock` (see
`res/xml/device_admin.xml`) — it cannot wipe the device or touch security settings.

The accessibility route (`performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`) is offered as
an alternative. The service reads no events, resolves no packages and adds no window; it
exists only because that call must come from inside an `AccessibilityService`. To enable
it you'll likely need **Settings ▸ Apps ▸ Quick Lock ▸ ⋮ ▸ Allow restricted settings**
first.

### Uninstalling

Revoke Device Admin from the setup screen first — Android blocks uninstalling an app that
is an active device admin. If you get stuck:

```bash
adb shell dpm remove-active-admin com.amitroy.doubletaplock/.LockAdminReceiver
```

---

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
├── LockTileService.kt           # the Quick Settings tile
├── LockController.kt            # Device Admin, accessibility fallback
├── LockAdminReceiver.kt         # claims force-lock only
├── LockAccessibilityService.kt  # capability only; reads nothing
└── SetupActivity.kt             # permissions + add-tile
```
