# Milestone 6: Live clouds

**Goal:** show today's real clouds, refreshed in the background without wasting data or
battery.

## Data source

**Live Cloud Maps** by Matt Eason (`clouds.matteason.co.uk`) builds a global greyscale cloud
map from EUMETSAT weather satellite data every 3 hours.

- **License:** the images are CC0. EUMETSAT requires the credit "Contains modified EUMETSAT
  data", which the settings screen shows.
- **Server:** Cloudflare, with `ETag` support, so asking "has it changed?" (`If-None-Match`)
  gets an empty `304 Not Modified` reply when it hasn't.
- **Limits:** the satellites don't see the poles, so the map fills them by mirroring nearby
  rows. Occasionally a missed region shows as a square hole.

## What was built

- **[`CloudUpdateJob`](../app/src/main/java/io/github/evertoncustodio/overhead/clouds/CloudUpdateJob.kt)**
  uses Android's own `JobScheduler`, not WorkManager, to avoid a dependency of a couple of MB.
  - A periodic job runs every 3 hours, requires a network and survives reboots.
  - A one-off job runs when the clouds are out of date as the wallpaper or app opens.
  - Scheduling is skipped if the job already exists, so the 3-hour countdown isn't reset.
- **[`CloudDownloader`](../app/src/main/java/io/github/evertoncustodio/overhead/clouds/CloudDownloader.kt)**
  uses plain `HttpURLConnection`.
  - It downloads 2048×1024 maps (420 KB) on unmetered networks and 1024×512 (120 KB) on mobile
    data.
  - It sends the stored `ETag`, unless a Wi-Fi download should replace a smaller map.
  - It checks the byte count against `Content-Length` and the image's 2:1 shape before using
    a download.
- **[`CloudStore`](../app/src/main/java/io/github/evertoncustodio/overhead/clouds/CloudStore.kt)**
  saves each download to a temporary file and renames it over the old one only when it's
  complete, so a broken download never shows. A map-version key changes only when a new file
  is saved, so the wallpaper reloads the texture only then, not on every "unchanged" check.
- **The renderer** uploads the new map on the GL thread (`queueEvent`), then redraws.
- **In the shader:**
  - Cloud cover comes from the map's brightness. Thin haze is eased in, so the ground shows
    through it.
  - Clouds are white in sunlight, warm at twilight and dim by moonlight.
  - They dim the city lights beneath them and hide the ocean glint.
  - Cloud shadows come from a second read, offset toward the sun.
  - Clouds fade out between 60° and 75° of latitude, where the map's data is mirrored.
- **The settings screen** shows "Clouds updated *n* minutes ago" ("just now" under a minute).
- New permissions, all granted automatically: `INTERNET`, `ACCESS_NETWORK_STATE` and
  `RECEIVE_BOOT_COMPLETED`.

## Problems found

- **Lint caught a crash before it happened.** `isActiveNetworkMetered` needs
  `ACCESS_NETWORK_STATE`, which hadn't been declared.
- **A flat white cap over Antarctica** from the mirrored polar data. Fixed with the latitude
  fade.
- **A black preview screen** once, which turned out to be a broken, restored wallpaper-picker
  screen, not the app. A fresh picker showed the clouds correctly.

## Result

On the emulator and on a Xiaomi Mi A2:
- The first download ran right after install.
- The periodic job was scheduled.
- A repeat check got `304` and downloaded nothing.
- A forced re-download raised the map version with no errors while the wallpaper was showing.
- The clouds matched the day's weather, including a storm over southeastern Brazil that the
  phone's weather app was warning about.
