# Feature: Location marker

**Goal:** mark the centered place on the globe with a small dot.

## What was built

- **A still, round dot** in light cyan, with a soft dark rim so it shows over white clouds.
  It marks the place the globe is centered on: the device location in "Use my location"
  mode, or the chosen place otherwise.
- **Fixed size on screen, not painted on the ground.** A dot painted on the surface would
  flatten into an oval toward the planet's edge. The globe is centered on the place, so the
  dot only moves off-center while swiping between home screens, and a round dot looks cleaner.
- **[`Marker`](../app/src/main/java/io/github/evertoncustodio/overhead/render/Marker.kt)**
  works out the dot's screen position once per frame on the CPU, using the same perspective
  as the shader. The shader only measures each pixel's distance to it: no texture reads, no
  transcendental functions. The dot fades out if the place turns toward the planet's edge.
- **Smaller on the lock screen, but less than the globe:** its radius scales with the square
  root of the zoom.
- **A setting** on the settings screen, "Mark the place with a dot", on by default and
  saved in `LocationStore`. The wallpaper's existing preference listener picks it up.
- **No animation:** a pulsing dot would need continuous redraws, undoing the
  once-a-minute redraw that keeps the wallpaper light on battery.

## Result

Tested on the emulator: the dot shows on São Paulo, shifts with the launcher's page offset,
and disappears immediately when the setting is turned off.
