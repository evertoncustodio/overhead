# Feature: Terrain relief

**Goal:** mountains you can see: slopes facing the sun brighter, slopes facing away darker,
most striking near sunrise and sunset.

## What was built

- **Shading, not shape.** Everest is 0.14% of Earth's radius, about one pixel at 200%, so
  raising the surface would be invisible, and stepping each ray through a height map is far
  too slow for the Mi A2. Instead the lighting uses a tilted ground normal (normal mapping).
  The sphere's outline stays perfectly round, and mountains don't cast shadows across valleys.
- **Elevation data:** NOAA's [ETOPO5](https://www.ngdc.noaa.gov/mgg/global/relief/ETOPO5/TOPO/ETOPO5/)
  (5 arc-minutes, about 9 km, public domain), averaged down to 2048×1024. Land only: the sea
  floor is flattened to sea level, since it wouldn't be visible from space.
- **No extra texture read.** [`tools/terrain_map.py`](../tools/terrain_map.py) adds the slopes
  to the ocean mask, which the shader already read for every surface pixel:
  `ocean_mask.png` became [`terrain.png`](../app/src/main/assets/textures/terrain.png), with
  the mask in red and the east and north slopes in green and blue. It's 1.2 MB instead of
  160 KB, so the APK grows by about 1 MB.
- **Square-root encoding.** Half the land is flatter than 0.24% averaged over a texel, which
  8 bits stored linearly would round to zero. The slopes are stored as `sign × sqrt(|slope| /
  0.1)`; the shader undoes it with `s * abs(s)`. Slopes beyond 10% are clipped (only the
  Himalayan front comes close). Where the mask says open water, the slope is forced to 0, so
  the glint stays flat where the coarser elevation grid disagrees about the coastline.
- **Exaggerated 20×** (`RELIEF_EXAGGERATION`). Chosen from offline previews comparing 8×
  (only visible at twilight), 20×, and 20× plus a fixed map-style light from the northwest
  (looked like a wall map, and lit slopes that were already in the night). At 20× mountains
  show from mid-afternoon on, with only the real sun lighting them.
- **What tilts:** the ground's sunlight and its day/night blend use the tilted normal, so the
  terminator turns ragged over mountains: sun-facing ridges stay lit past it. City lights,
  clouds, cloud shadows, the ocean glint and the edge haze keep the smooth sphere's lighting.
- **Cost per surface pixel:** one `normalize`, a few multiply-adds and one more `smoothstep`.
  The `sqrt` used for the cloud shadow's north vector became an `inversesqrt` that both share.

## Problems found

- **ETOPO5's byte order:** the `.DOS` file is little-endian, row 0 at 90°N, column 0 at
  longitude 0. Checked against known heights (the Everest area reads about 6100 m, an
  average over the 9 km cell).
- **NASA's image servers timed out** from this laptop, NOAA's didn't, which is why ETOPO5 was
  used instead of NASA's Blue Marble topography.

## Result

Checked on the emulator with a fixed-clock copy at Himalayan sunset: the Zagros, the Hindu
Kush and the Himalayan front show lit and shaded sides, and the shader compiles with no
errors.

Measured on the Mi A2 with a timing build that drew continuously and switched between the old
and new shader every 300 frames (median interval between frames, five rounds each, all within
0.05 ms of each other):

| Frame | Without relief | With relief |
|---|---|---|
| Still, full resolution | 23.4 ms | 23.8 ms |
| Animation, 75% resolution | 16.7 ms | 16.7 ms (60 fps) |

Relief costs about 0.4 ms on still frames and nothing visible during the unlock animation.
Still frames go a little further over the 23 ms target; they're drawn about once a minute, so
this doesn't show.

**Not yet done:**
- At 200% the relief looks soft: the slope map is 2048 wide. A 4096 map would need finer data
  than ETOPO5 (such as ETOPO 2022) and a bigger APK.
