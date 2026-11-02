# Feature: Globe size

**Goal:** choose how big the globe is on the home screen and on the lock screen.

## What was built

- **[`ZoomStore`](../app/src/main/java/io/github/evertoncustodio/overhead/wallpaper/ZoomStore.kt)**
  saves the two sizes, relative to the original size (the globe spanning 90% of the screen
  width). Defaults are the previous fixed values: 100% at home, 78% locked.
- **Range 60% to 200%.** Zooming out is free. Zooming in fills more of the screen with
  planet pixels, which cost more than space, and needs detailed images: the 4096×2048
  day/night maps stay sharp up to about 2x. This replaces the milestone 7 plan to shrink
  them to 2048×1024.
- **Two sliders** on the settings screen, in whole percent.
- **A live preview** above them: a `GLSurfaceView` with its own `GlobeRenderer`, shaped like
  the screen, showing whichever size was touched last. Only one preview, because each GL
  context loads its own copy of the textures.
- **The wallpaper** reads the sizes through a preference listener. The unlock animation goes
  from the lock size to the home size, so it zooms out if the lock size is bigger, and is
  skipped when both are equal. The launcher's own zoom-out still applies on top.

## Result

Tested on the emulator: the sliders and preview, 150% at home, and unlocking from a 200% lock
screen (the globe shrinks to the home size). Not yet measured on the Mi A2 at 200%.
