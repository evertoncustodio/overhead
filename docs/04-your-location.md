# Milestone 4: Your location

**Goal:** center the globe on where the phone is, or on a place the user chooses.

## What was built

- **[`LocationStore`](../app/src/main/java/io/github/evertoncustodio/overhead/location/LocationStore.kt)**
  saves the place in `SharedPreferences`. It records whether the place follows the device or
  was chosen by hand, plus a readable name. The settings screen and the wallpaper run in the
  same process, so the wallpaper reacts to changes immediately through a preference listener.
  - **Privacy:** coordinates are rounded to 0.1° (about 11 km) before they're saved.
  - **Before any location is known,** the globe faces the time zone's meridian, at 15° of
    longitude per hour from UTC.
- **[`DeviceLocation`](../app/src/main/java/io/github/evertoncustodio/overhead/location/DeviceLocation.kt)**
  reads the location through Android's own `LocationManager` and
  `LocationManagerCompat.getCurrentLocation`, not Google Play services, so it also works on
  phones without Google. It reuses any fix from the last 15 minutes. Providers are tried in
  order: fused (Android 12+), network, then GPS.
- **[`PlaceNames`](../app/src/main/java/io/github/evertoncustodio/overhead/location/PlaceNames.kt)**
  wraps Android's `Geocoder` to turn names into coordinates and back. It uses the
  asynchronous API on Android 13+, and a background thread on older versions.
- **The settings screen** shows where the globe is centered and has two buttons:
  - **Use my location:** asks for permission when needed.
  - **Choose a place:** accepts a city name or coordinates like `-23.5, -46.6`.

  It refreshes the location each time the app opens, and offers a shortcut to the app's
  settings if location access is blocked for good.
- AndroidX Core was added, for `LocationManagerCompat` and a few small helpers.

## Problems found

- **No location on Android 10 (found on a real Xiaomi Mi A2).** Up to Android 11, apps with
  only *approximate* location can't use the GPS provider: Android throws a
  `SecurityException`. That phone had network location turned off, leaving GPS as the only
  option. The emulator runs Android 16, where approximate apps may use GPS, so it couldn't
  show the bug. **Fix:**
  - On Android 11 and older, also request `ACCESS_FINE_LOCATION`, declared with
    `maxSdkVersion="30"`. Those versions don't offer an approximate/precise choice anyway.
  - Only try GPS when it's actually allowed.
  - Ask again if any permission is still missing.

  The saved location is still rounded to 11 km.
- **A test-script bug:** coordinates read from `uiautomator` dumps were mangled, so taps landed
  in the wrong places. This was in the test tooling, not the app.

## Known limitation

Android limits location access for apps in the background. The wallpaper tries the latest
known location whenever it becomes visible, and otherwise keeps the place saved the last time
the app was opened. So the globe follows the user to a new city once they open Overhead there.

## Result

Tested on the emulator (permission prompt, device location, a city search, typed coordinates)
and on the Mi A2 (device location by GPS, after the fix).
