# Overhead

An Android live wallpaper that shows the Earth from space, centered on where you are, as it
looks right now: real sunlight and night, city lights, live clouds and the sun itself.

Inspired by the "Your World" live wallpaper that once shipped on Google Pixel phones. Overhead
is an independent project with no connection to Google.

## Features

- **3D globe centered on your location:** from the phone's approximate location, or a place
  you choose by name or coordinates.
- **Your place marked** with a small dot, which can be turned off in the settings.
- **Real day and night:** the sun's position is calculated from the date and time, with a soft
  twilight band and the line between day and night moving through the day.
- **City lights** on the night side, from NASA's Black Marble.
- **Live clouds,** updated about every 3 hours from weather satellite data, with cloud shadows.
- **Atmosphere:** a blue glow around the planet that turns orange at sunset, and glows all the
  way round when the sun is behind the Earth.
- **The sun in space,** in its real direction: soft light from the screen edge, or rising over
  the planet's edge around midnight.
- **Ocean glint** where sunlight reflects off the water.
- **The Milky Way and the real stars** behind the planet, turned as the sky really is right
  now: behind the Earth is the sky above the other side of the world, so the Milky Way passes
  by part of the day. A setting turns the sky so it's always in view instead.
- **Unlock animation:** the lock screen shows the planet from further away, and unlocking flies
  in toward your location.
- **Adjustable globe size** for the home screen and the lock screen, from 60% to 200%, with a
  live preview in the settings.
- **Light on battery:** when nothing moves, it redraws once a minute and stops completely while
  the wallpaper is hidden.

## Requirements

- Android 8.0 (API 26) or newer, with OpenGL ES 3.0 (practically every phone since 2014).
- Internet for cloud updates (optional: the globe works without clouds).

## Installing the APK

Overhead isn't on the Play Store. To install a shared APK:

1. Open the `.apk` file on the phone.
2. When Android blocks it, tap **Settings** and turn on **Allow from this source** for the app
   you opened it with (Files, WhatsApp, Drive…), then go back.
3. Tap **Install**. If Google Play Protect warns about an unknown developer, tap
   **Install anyway**.
4. Open **Overhead**, tap **Use my location** or **Choose a place**, then **Set as wallpaper**
   and choose **Home screen and lock screen**.

The current shared build (`dist/Overhead-0.3.0.apk`) is signed with a development key. Builds
signed with the upcoming release key can't be installed over it: uninstall it first.

## Building

You need Android Studio (it bundles the Java runtime) with the Android SDK installed.

- **Android Studio:** open this folder, wait for the Gradle sync, then press **Run**.
- **Command line:** Gradle needs a JDK 17 or newer, e.g. the one inside Android Studio:

  ```bash
  export JAVA_HOME=/path/to/android-studio/jbr
  ./gradlew assembleDebug         # APK in app/build/outputs/apk/debug/
  ./gradlew testDebugUnitTest     # unit tests
  ./gradlew lintDebug             # Android lint
  ```

The project uses Kotlin, the Android Gradle Plugin 9.5 (with its built-in Kotlin support) and
Gradle 9.6. Its only library is AndroidX Core.

### Emulator notes

On some Linux laptops the emulator crashes with **Graphics acceleration: Software**. Use
**Hardware** (host GPU) instead. If the emulator boots but adb reports it as `offline` or
`unauthorized`, make sure `~/.android/adbkey.pub` exists (`adb pubkey ~/.android/adbkey`) and
wipe the virtual device's data.

## How it works

The whole picture is drawn by one fragment shader, [`globe.frag`](app/src/main/assets/shaders/globe.frag).
For each pixel it shoots a ray from the camera, finds where it hits the planet, and looks up
that spot in equirectangular world maps. Lighting is done in Earth-fixed coordinates, using
the sun's direction from [`SunPosition.kt`](app/src/main/java/io/github/evertoncustodio/overhead/render/SunPosition.kt).
Pixels that miss the planet only draw space: the sky, the atmosphere glow and the sun. The sky
is a cube map turned by Earth's rotation angle, from [`Sky.kt`](app/src/main/java/io/github/evertoncustodio/overhead/render/Sky.kt).

```
app/src/main/
├─ assets/
│  ├─ shaders/        globe.vert, globe.frag
│  └─ textures/       earth_day.jpg, earth_night.jpg, ocean_mask.png,
│                     sky_*.jpg (the six faces of the sky's cube map)
└─ java/io/github/evertoncustodio/overhead/
   ├─ MainActivity.kt                 settings screen
   ├─ wallpaper/OverheadWallpaperService.kt
   │                                  the live wallpaper: GL thread, redraw timing,
   │                                  lock/unlock animation, home screen swipes
   ├─ render/   GlobeRenderer.kt      OpenGL setup and drawing
   │            GeoPoint.kt           latitude/longitude and globe rotation
   │            SunPosition.kt        where the sun is overhead at a given time
   │            Sky.kt                how the sky behind the globe is turned
   │            GlUtils.kt            shader and texture loading
   ├─ location/ LocationStore.kt      the saved place (rounded to ~11 km)
   │            DeviceLocation.kt     the phone's location, without Google Play services
   │            PlaceNames.kt         place names ↔ coordinates (Android's Geocoder)
   └─ clouds/   CloudUpdateJob.kt     background job, every 3 hours
                CloudDownloader.kt    downloads the latest cloud map
                CloudStore.kt         keeps the map on disk
```

The [`docs/`](docs/) folder tells how the project was built, one milestone at a time,
including the problems found along the way.

## Privacy and data use

- **Location** is only used to center the globe. It's rounded to about 11 km before being
  saved, stays on the phone and is never sent anywhere. Turning location names into places,
  and back, uses Android's built-in Geocoder service.
- **Network:** the only download is the cloud map from `clouds.matteason.co.uk`, about every
  3 hours: 420 KB on Wi-Fi, 120 KB on mobile data, and nothing when the map hasn't changed.
  No accounts, ads, analytics or tracking.

## Credits

- Earth imagery: NASA **Blue Marble Next Generation** (day) and **Black Marble 2012** (city
  lights), obtained through NASA's GIBS service. Public domain.
- Land/water mask: **OpenStreetMap** data via NASA GIBS. © OpenStreetMap contributors (ODbL).
- Clouds: **Live Cloud Maps** by Matt Eason (CC0). Contains modified EUMETSAT data.
- Sky: NASA's **Deep Star Maps 2020**, NASA/Goddard Space Flight Center Scientific
  Visualization Studio, with Gaia DR2 data (ESA/Gaia/DPAC). Converted to a cube map by
  [`tools/sky_cubemap.py`](tools/sky_cubemap.py).
- Sun position: low-precision formulas from the Astronomical Almanac.
- Star noise: "Hash without Sine" by Dave Hoskins.

## License

The code is released under the [MIT License](LICENSE). The Earth images and cloud maps keep
their own terms, listed under Credits.

## Status

Version 0.3.0: adds the [Milky Way](docs/10-milky-way.md) and the real stars behind the
planet. Version 0.2.0, shared with a few testers before it, added the location dot and
adjustable globe sizes.
Remaining work is listed in [milestone 7](docs/07-performance-and-release.md).
