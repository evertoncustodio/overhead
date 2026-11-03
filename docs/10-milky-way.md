# Feature: The Milky Way

**Goal:** the real sky behind the planet, with the Milky Way, instead of random stars.

## What was built

- **The sky map:** NASA's [Deep Star Maps 2020](https://svs.gsfc.nasa.gov/4851)
  (`starmap_2020_4k.exr`, 4096×2048, stars and the Milky Way from Gaia and other surveys).
  It's an equirectangular map in equatorial coordinates (right ascension and declination).
- **A cube map instead of a flat map.** Reading an equirectangular image needs `atan` and
  `asin` per pixel, which the shader rules avoid. A cube map is read straight from a
  direction. [`tools/sky_cubemap.py`](../tools/sky_cubemap.py) converts the map into six
  1024×1024 JPEG faces (about 1.6 MB together) in OpenGL's face layout, and:
  - averages 3×3 samples per texel, so small stars don't disappear between samples;
  - takes off a black level of 0.008: the map's faint haze of unresolved stars read as grey
    behind the sunlit planet;
  - brightens what's left and applies a gamma curve, so the faint glow keeps its gradations
    in 8 bits. The shader dims it back with `SKY_BRIGHTNESS`.
- **The real sky, turned correctly.** Sky coordinates are laid out like Earth-fixed ones
  (+Y the celestial pole, +Z right ascension 0h), so Earth-fixed to sky is a single turn about
  the pole by the sidereal time. `SunPosition.siderealTime` is shared with the sun, so the
  two always agree. [`Sky.realViewToSky`](../app/src/main/java/io/github/evertoncustodio/overhead/render/Sky.kt)
  combines it with the globe's rotation into one matrix per frame, and the shader does one
  matrix multiply and one `textureLod` per space pixel.
- **What you see behind the planet** is the sky overhead on the other side of the world (your
  nadir). It sweeps a full circle of the sky each day, so the Milky Way, a band around the
  whole sky, crosses the background about twice a day.
- **"Keep the Milky Way in view"** ([`SkyStore`](../app/src/main/java/io/github/evertoncustodio/overhead/wallpaper/SkyStore.kt),
  off by default): a fixed sky with the galaxy's center 21° above the screen's center, past
  the top of the globe, and the band running along the screen's long side, tilted 20°. It
  still turns with home screen swipes, like the real sky does.
- **Mipmap level:** every pixel covers about the same angle of sky, so `GlobeRenderer` works
  out one level per frame (`uSkyLod`). At normal sizes it's 0: the sky is magnified about 3×,
  which suits the soft glow of the Milky Way.
- **The random stars stay** on top, in screen space: they keep single-pixel sharpness that the
  magnified map can't give.

## Problems found

- **The emulator's screen was asleep** after a headless start, so the first screenshots were
  all black. Wake it with `input keyevent KEYCODE_WAKEUP` and `wm dismiss-keyguard`.

- **A crash in the timing build** (not the app): a phase named "75%+sky" went into a format
  string, where `%+s` is a format code. Keep `%` out of text passed to `format`.

## Result

Checked on the emulator with both settings, and covered by unit tests in `SkyTest` (the
zenith lands at the place's latitude and local sidereal time; one sidereal day returns the
same sky; the galaxy's center lands where the option puts it).

Measured on the Mi A2 with a timing build that switched the sky lookup on and off every 300
frames (median interval between frames, two runs each):

| Frame | Without the sky | With the sky |
|---|---|---|
| Still, full resolution | 24.6 ms | 25.7 ms |
| Animation, 75% resolution | 16.7 ms | 16.7 ms (60 fps) |

The sky costs about 1 ms on still frames, and nothing visible during the unlock animation.
Still frames were already slower than the 22.6 ms measured in milestone 7 before the sky was
added, so both are over the 23 ms target. Still frames are drawn about once a minute, so this
doesn't show, but it's worth finding where the extra 2 ms came from.

**Not yet done:**
- Memory: the cube map takes about 33 MB of GPU memory per GL context (the wallpaper, and the
  settings preview while it's open). See the ETC2 item in [milestone 7](07-performance-and-release.md).
