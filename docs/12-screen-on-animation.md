# Feature: Screen-on animation

**Goal:** a short animation when the screen turns on, before the phone is unlocked.

## What was built

- **Three effects, played together over 1.6 s.** The planet arrives turned 10° east, 6%
  smaller and at 30% brightness:
  - It glides onto your place and grows to its size.
  - The sunlight comes up over the first 70% of the animation.
  - The city lights switch on patch by patch from 25% on.
  [`WakeAnimation`](../app/src/main/java/io/github/evertoncustodio/overhead/render/WakeAnimation.kt)
  turns the progress (0..1) into each effect, with cubic ease-outs, so the globe glides to a
  stop. At 1 everything is back to normal.
- **The turn follows the real Earth.** Settling from the east moves the surface eastward, the
  way the planet spins. The first try turned 20°, which looked like too much on the Mi A2.
- **City lights come on in patches.** A hash of the map position, on a 1024×512 grid (about
  40 km), gives each patch its own moment to switch on, so towns light up at different moments
  instead of all fading in together.
- **The first frame is set up while the screen is off.** On `ACTION_SCREEN_OFF` the renderer
  is set to progress 0, so the frame drawn when the wallpaper becomes visible again is already
  dim and turned. Starting only when the screen turns on would first show the settled globe
  for a moment. The animation starts in `onVisibilityChanged(true)`, or on
  `ACTION_SCREEN_ON` for phones where the wallpaper stays "visible" while the screen is off.
- **Plays alongside the unlock zoom.** A quick fingerprint unlock runs both together. Frames
  are drawn at 75% resolution while either one runs (`updateResolution()`).
- **Hidden halfway** (an app opened over the wallpaper): the animation jumps to its end, so
  the wallpaper comes back settled.
- **A setting,** "Animate when the screen turns on", on by default.
  [`AnimationStore`](../app/src/main/java/io/github/evertoncustodio/overhead/wallpaper/AnimationStore.kt)
  keeps it; the wallpaper reads it each time the screen turns off, so it needs no listener.
  The preview in the wallpaper picker never animates.
- **Shader cost:** one `vec2` uniform, `uWake` (brightness, city-light progress). The patch
  hash runs inside a branch on that uniform, so settled frames skip it; otherwise they cost
  one extra multiply.

## Result

- **Checked on the emulator** with a scratchpad copy that froze the progress at 0, 0.45, 0.65
  and 1 in the wallpaper preview, over São Paulo at dusk: dim and turned, then sunlit with
  the first lights coming on, more lights, and finally the usual frame.
- **Tried on the Mi A2** by turning the screen off and on: smooth.
- **Frame times weren't measured.** Animation frames do the same work as the unlock animation
  (which reaches 60 fps at 75%), plus one hash per planet pixel.

## Later

- **Tilt parallax** (globe and stars shifting as the phone moves, using the gyroscope) was
  discussed and put on hold: it needs continuous sensor readings and redraws, so it should
  be measured together with the milestone 7 battery check.
