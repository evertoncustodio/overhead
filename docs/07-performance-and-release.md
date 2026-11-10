# Milestone 7: Performance and release

**Goal:** run smoothly on modest phones, use little battery, and produce a release APK to
share. **Status: in progress.**

## Done: performance on a real phone

Measured on a **Xiaomi Mi A2**: Android 10, Adreno 512 GPU (2018), 1080×2160 screen.
Temporary test builds drew continuously and logged frame times.

| Change | Time per frame | Frame rate |
|---|---|---|
| Starting point | 60 ms | ~16 fps |
| Skip the Earth math for pixels in space | 40 ms | ~25 fps |
| `textureLod` instead of `textureGrad` | 30.6 ms | ~33 fps |
| Mipmap levels from geometry, cheaper star hash | 22.6 ms | ~44 fps |
| 75% resolution during animations | 16.7 ms | **60 fps** |

The first rows were measured with `glFinish()` after each frame. The last two are the real
interval between frames, because forcing the GPU idle after each frame can slow a phone GPU
down.

What each change did:

- **Branches in [`globe.frag`](../app/src/main/assets/shaders/globe.frag):** about 70% of the
  screen is black space, but every pixel ran the full Earth shading. The shader now has
  `shadeSurface` and `shadeSpace` functions and only runs what's visible.
- **`textureLod`:** `textureGrad` is slow on many mobile GPUs, and each planet pixel made five
  of those reads.
- **Mipmap levels from geometry:** the level is worked out from how much of the planet one
  pixel covers (`hitDistance × pixelSpread / cos(view angle)`), instead of comparing
  neighboring pixels with `dFdx`. Space pixels can then skip the map math entirely, and the
  longitude-180° seam stays fixed. The planet's edge smoothing uses the same pixel size,
  instead of `fwidth`.
- **Cheaper details:**
  - The cloud shadows shift map coordinates directly, instead of calling `atan`/`asin` again.
    Their offset now also grows with the sun's angle, like real shadows.
  - The stars use Dave Hoskins' "hash without sine".
- **Reduced resolution while animating.** During the unlock animation,
  [`GlobeRenderer`](../app/src/main/java/io/github/evertoncustodio/overhead/render/GlobeRenderer.kt)
  draws into an off-screen renderbuffer at 75% per side, then stretches it to the screen with
  `glBlitFramebuffer`. Still frames stay at full resolution.

### Lessons

- **Measure on the slowest device you care about.** On the emulator, which used the laptop's
  GPU, none of this showed.
- **Texture memory wasn't the bottleneck.** Shrinking every texture to 64×32 barely changed the
  frame time, so texture compression is for saving memory, not for speed.
- **Live wallpapers can't use `SurfaceHolder.setFixedSize()`:** it throws "Wallpapers currently
  only support sizing from layout". Lower-resolution drawing needs an off-screen framebuffer.

## Done: first shared build

[`dist/Overhead-0.1.0.apk`](../dist/) is a debug build (4.5 MB) signed with the development
key, shared with one tester. A release-key build can't be installed over it: uninstall first.

`dist/Overhead-0.2.0.apk` (version code 2, 4.5 MB) follows with the
[location dot](08-location-marker.md) and [globe size](09-globe-size.md) features. It uses the
same development key, so it installs over 0.1.0 as an update.

`dist/Overhead-0.3.0.apk` (version code 3, 6.1 MB) adds the [Milky Way](10-milky-way.md).
The sky's six images make it 1.6 MB bigger. Same development key, so it installs over 0.2.0.

`dist/Overhead-0.4.0.apk` (version code 4, 7.1 MB) adds [terrain relief](11-terrain-relief.md).
The slopes in `terrain.png` make it about 1 MB bigger. Same development key, so it installs
over 0.3.0.

`dist/Overhead-0.5.0.apk` (version code 5, 7.1 MB) adds the [screen-on animation](12-screen-on-animation.md).
Same development key, so it installs over 0.4.0.

## To do

1. **Battery check.**
   - Confirm nothing draws while the screen is off. The once-a-minute redraw should stop with
     the visibility change.
   - Confirm no location request lingers. On the Mi A2, Android's location history once
     showed Overhead's GPS request as still active.
2. **A smaller release APK.**
   - Turn on code shrinking (R8).
   - Store the day and night images as ETC2-compressed textures, and the single-channel maps
     as `R8`, to cut GPU memory from about 45 MB of uncompressed textures. The ocean mask is
     now part of the three-channel `terrain.png` ([11-terrain-relief.md](11-terrain-relief.md)),
     so only the clouds stay single-channel.
   - The sky's cube map ([10-milky-way.md](10-milky-way.md)) adds about 33 MB more: six
     1024×1024 faces stored as RGBA, plus mipmaps. ETC2 would cut that too.
   - ~~The day/night images only need 2048×1024~~: dropped. At the default zoom the 4096-wide
     top mipmap level is never read, but the adjustable globe size (up to 200%, see
     [09-globe-size.md](09-globe-size.md)) needs it.
3. **A release signing key** kept outside the project folder, with a release build
   configuration that uses it, then the final APK.
