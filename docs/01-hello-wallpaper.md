# Milestone 1: Hello wallpaper

**Goal:** an Android project that installs a live wallpaper, with all the tooling working
end to end, before any real drawing.

## What was built

- A new Kotlin project, `io.github.evertoncustodio.overhead`:
  - Android Gradle Plugin 9.5.0 (Kotlin support is built in, no separate Kotlin plugin)
  - Gradle 9.6.0
  - `minSdk` 26 (Android 8.0), built against API 37
  - no libraries at this stage
- **`OverheadWallpaperService`:** a `WallpaperService` whose engine drew a dark background
  with a flat blue circle using a `Canvas`, a placeholder for the globe.
- **`MainActivity`:** a plain `Activity` (no AndroidX) showing whether Overhead is the current
  wallpaper, with a **Set as wallpaper** button. That button opens the system preview through
  `ACTION_CHANGE_LIVE_WALLPAPER`, or the full wallpaper list if a launcher doesn't support it.
- A manifest declaring the service with the `BIND_WALLPAPER` permission, plus its
  `res/xml/wallpaper.xml` description.
- An adaptive launcher icon drawn as a vector: a globe with its right side lit.

## Decisions

- **Android only, distributed as an APK** to a few people, not through the Play Store.
- **No libraries.** A plain `Activity` and platform APIs keep the APK small and builds fast.

## Problems found

Getting the emulator to run took longer than writing the code:

1. **The emulator crashed on boot** (`SIGSEGV`) when its graphics were set to *Software*. Its
   software renderer (lavapipe + SwiftShader ANGLE) crashes on this machine, an Intel + NVIDIA
   laptop running Wayland, as soon as Android starts drawing. **Fix:** use host GPU graphics
   (`hw.gpu.mode=host`).
2. **The emulator booted, but adb saw it as `offline`/`unauthorized`.** `~/.android/adbkey` had
   been created without its `adbkey.pub`, so the virtual phone's data was set up with no trusted
   key. **Fix:** create the public key with `adb pubkey`, then wipe the virtual phone's data.
3. The API 37 emulator image never finished booting; an API 36 image was used instead.

## Result

The wallpaper installed and showed the placeholder on the emulator, confirming the project,
the build and the device workflow.
