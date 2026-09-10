# DoubleTap Launcher

A minimal Android launcher for the Pixel 6a. Double-tap an empty part of the home screen
and the phone locks. Single taps, icon taps and the app drawer behave normally.

Built and lint-clean against AGP 9.2.1 / Gradle 9.4.1, `minSdk 28`, `targetSdk 36`.

---

## Why the previous version couldn't work

The old `DoubleTapLock` project watched the Pixel Launcher from an `AccessibilityService`
and floated an invisible `TYPE_ACCESSIBILITY_OVERLAY` over it. Both reported bugs come
from that one decision, and neither is fixable by tuning it.

### Bug 1 — "it thinks the app drawer is the home page"

`TapLockService` decided it was on the home screen like this:

```kotlin
val isLauncher = launcherPackages.contains(packageName) ||
        packageName == "com.google.android.apps.nexuslauncher"
```

On a Pixel, **the home screen and the app drawer are the same activity in the same
package** — `com.google.android.apps.nexuslauncher/.NexusLauncherActivity`. The drawer is
an internal view that slides up inside that single window. It is not a new window, so it
raises no `TYPE_WINDOW_STATE_CHANGED` that distinguishes it, and `packageName` is
identical either way. The check is true in both states by construction.

The `TYPE_VIEW_SCROLLED` and manual swipe-detection patches that were layered on top
misfire because scrolling *between home screen pages* produces the same event as opening
the drawer. There is no public API that reports "the Pixel app drawer is open" — that is
private state inside a closed-source app.

### Bug 2 — "one tap starts waiting for a second tap, so normal taps break"

Two separate things were happening.

**The overlay ate the taps.** The touch listener ended with:

```kotlin
// Always return false to never consume events
false
```

That comment describes an intention Android does not implement. Returning `false` tells
the *View* it didn't handle the event — but the *window* already consumed it, and it never
reaches the launcher underneath. `FLAG_NOT_TOUCH_MODAL` only passes through touches
landing **outside** the overlay's bounds; the overlay covered the top 72% of the screen,
so every tap in that region vanished. The one flag that does let touches through,
`FLAG_NOT_TOUCHABLE`, also stops you receiving them.

You cannot both observe a tap and forward it. That is a deliberate anti-tapjacking
boundary in Android, not an oversight to work around.

**The tap counter added latency.** Counting taps inside `onSingleTapUp` with a 500 ms
window means the first tap's outcome is undecided for half a second — the "waiting for a
second tap" feel.

---

## How this version fixes them

By *being* the launcher instead of spying on one.

| | Before | Now |
|---|---|---|
| "Am I on the home page?" | guessed from `packageName` | `HomeActivity` is resumed — an OS guarantee |
| "Is the drawer open?" | inferred from scroll events | it's a separate activity; home is paused |
| Icon taps | swallowed by the overlay | dispatched normally through our own view tree |
| Single-tap delay | 500 ms tap counter | none — `onDoubleTap` fires on the 2nd touch-down |

**Fix 1** lives in the manifest and the activity split. The app drawer is
`AppDrawerActivity`, a separate activity. While it is on top, `HomeActivity` is paused and
is not in the touch-dispatch path, so the lock gesture is *physically incapable* of firing
there. No flag to keep in sync, no heuristic to misfire. `HomeActivity` is `singleTask`,
so pressing HOME from the drawer brings it forward and destroys the drawer on the way.

**Fix 2** lives in `activity_home.xml` and the gesture listener:

* The layout is a `FrameLayout` with the gesture surface on the **bottom** layer and the
  UI on top. A `FrameLayout` offers `ACTION_DOWN` to children topmost-first, and the UI
  container is not clickable — so icon taps are consumed by the icons (instant, never
  reaching the detector) and only taps on bare wallpaper fall through to it.
* The listener implements `onDoubleTap` and deliberately **does not** implement
  `onSingleTapUp` or `onSingleTapConfirmed`. `onDoubleTap` fires on the second tap's
  `ACTION_DOWN`, so locking is immediate; and since a single tap on bare wallpaper has no
  action in any launcher, there is nothing for the double-tap window to delay.

---

## About the repo name: lock, not unlock

An app cannot unlock the phone. While the device is locked no app is foreground to receive
a tap, and dismissing the keyguard requires the user's biometric or PIN. Double-tap-to-wake
is a display-controller feature the Pixel 6a does not expose to apps either. Lock is the
half that is achievable — the same gesture, the useful direction.

---

## Setup on the Pixel 6a

1. Open the project in Android Studio and Run, or install the built APK:
   ```bash
   ~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Open **DoubleTap Launcher** from the app drawer. It opens the setup screen.
3. **Step 1 — Default home app.** Tap *Open home app settings* and pick DoubleTap Launcher.
4. **Step 2 — Permission to lock.** Tap *Grant Device Admin* and accept.
5. Press HOME, then double-tap empty wallpaper.

### Device Admin vs the accessibility service

Device Admin is the default because it is one toggle and is not affected by the
"Restricted setting" wall Android 13+ puts in front of accessibility services for
sideloaded APKs. It claims exactly one policy, `force-lock` (see
`res/xml/device_admin.xml`) — it cannot wipe the device or touch security settings.

The accessibility route (`LockAccessibilityService` →
`performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`) is offered as an alternative. Note the
contrast with the old service: it reads no events, resolves no packages and adds no
window. It is a capability provider, nothing more.

To use it, you'll likely need **Settings ▸ Apps ▸ DoubleTap Launcher ▸ ⋮ ▸ Allow
restricted settings** first.

### Uninstalling

Revoke Device Admin from the setup screen first — Android blocks uninstalling an app that
is an active device admin. If you get stuck:

```bash
~/Library/Android/sdk/platform-tools/adb shell dpm remove-active-admin com.amitroy.doubletaplauncher/.LockAdminReceiver
```

---

## Using it

| Gesture | Action |
|---|---|
| Double-tap empty wallpaper | Lock the phone |
| Long-press empty wallpaper | Open settings |
| Swipe up, or tap the apps button | Open the app drawer |
| Long-press an app in the drawer | Pin to dock / app info / uninstall |
| Long-press a dock icon | Unpin it |

The dock holds 5 apps (`Prefs.MAX_DOCK`).

## Tuning

The double-tap window is `ViewConfiguration.getDoubleTapTimeout()` — 300 ms, the same
value every Android app uses, so it feels native. If you want a longer window you'd have
to replace `GestureDetector` in `HomeActivity.WallpaperGestures` with your own timer; be
aware that widening it does not cost you anything here only because single-tap on
wallpaper is a no-op.

Swipe-up sensitivity is `swipeDistancePx` / `swipeVelocityPx` in `HomeActivity`.

## Layout

```
app/src/main/java/com/amitroy/doubletaplauncher/
├── HomeActivity.kt              # home screen + the two fixes
├── AppDrawerActivity.kt         # separate activity == unambiguous drawer state
├── SetupActivity.kt             # permissions + options
├── LockController.kt            # Device Admin, accessibility fallback
├── LockAdminReceiver.kt
├── LockAccessibilityService.kt  # capability only; reads nothing
├── AppRepository.kt             # cached off-thread app scan
└── Prefs.kt
```
