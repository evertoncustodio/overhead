# Milestone 5: Polish

**Goal:** the details that make the wallpaper feel alive: water, stars, motion, and later the
sun itself.

## What was built

- **Ocean glint.** Where sunlight reflects off the water toward the camera, the shader adds
  Blinn-Phong highlights, a sharp core and a soft halo, strengthened at grazing angles (a
  Fresnel term). They only appear over water, using a land/water mask
  ([`ocean_mask.png`](../app/src/main/assets/textures/ocean_mask.png), 2048×1024, 160 KB). The
  mask was made from the OpenStreetMap layer `OSM_Land_Water_Map` on NASA GIBS, mapped to
  white water and black land with slightly softened coastlines.
- **Starfield:** sparse single-pixel stars from a per-pixel hash, mostly faint, with slightly
  varied colors.
- **Unlock animation.** When the screen turns off (`ACTION_SCREEN_OFF`), the globe shrinks to
  78%, so the lock screen shows the whole planet from further away. On unlock
  (`ACTION_USER_PRESENT`), a 1.2-second decelerating `ValueAnimator` brings it back to full
  size.
- **Home screen swipes.** `onOffsetsChanged` turns the globe up to 8° east or west, but only
  when the launcher reports several pages.
- **Launcher zoom** (Android 11+): `onZoomChanged` shrinks the globe slightly when the launcher
  zooms the wallpaper, for example in the recent-apps view.

### Added later: the sun in space

- **The sun disk** at its real angular size (about 0.53°), with a glow, in its true direction.
  The planet hides it when it's behind.
- **Light from off-screen.** The sun is usually off-screen, so a soft glow is centered just
  past the screen edge in its direction. It's strongest around sunrise and sunset.
- **Forward scattering in the atmosphere.** Looking toward the sun through the air makes the
  rim glow orange. With the sun behind the Earth, the whole edge lights up, like an eclipse
  seen from space.

## Problems found

- **A white blob at the globe's edge.** With the sun just behind the planet, the glint formula
  peaked at the edge itself. The glint now fades out where the water is seen edge-on.
- **The unlock animation never ran.** The receiver was registered as `RECEIVER_NOT_EXPORTED`.
  `ACTION_SCREEN_OFF` comes from the system and arrived, but `ACTION_USER_PRESENT` is sent by
  System UI, which counts as another app, so it was silently dropped. `dumpsys activity
  broadcasts history` showed the difference. **Fix:** `RECEIVER_EXPORTED`. That's safe because
  both are protected broadcasts that only the system can send.
- **The first version of the off-screen sun light** made a hard vertical band. It was replaced
  by a radial glow from a point just outside the screen.
- **No sun behind the planet:** at first only the sunlit glow existed, so the "eclipse" case
  was nearly invisible. That's what led to the forward-scattering term.

## Not verified

- Swipe offsets: the emulator's launcher had only one page, and many recent launchers no
  longer report offsets to wallpapers at all.
- Launcher zoom.

## Result

On the emulator: the glint over the South Atlantic at noon, a smaller globe on the lock screen
growing to full size on unlock, and the sun at six moments of the day, including the eclipse
ring and a sunrise over the planet's edge.
