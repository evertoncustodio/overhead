# Milestone 3: Real lighting

**Goal:** light the globe with the real sun: day, night, twilight, city lights and an
atmosphere.

## What was built

- **[`SunPosition`](../app/src/main/java/io/github/evertoncustodio/overhead/render/SunPosition.kt)**
  computes the subsolar point (where the sun is directly overhead) for any moment. It uses the
  low-precision formulas from the Astronomical Almanac, accurate to about 0.01° between 1950
  and 2050:
  - days since J2000
  - the sun's ecliptic longitude
  - declination and right ascension
  - Greenwich sidereal time, which turns those into a longitude
- **Unit tests** ([`SunPositionTest`](../app/src/test/java/io/github/evertoncustodio/overhead/render/SunPositionTest.kt))
  check both solstices, the March equinox, solar noon at Greenwich (allowing for the equation
  of time) and midnight.
- **Lighting in the shader,** done in Earth-fixed coordinates with the sun's direction as a
  uniform:
  - **Day side:** the Blue Marble image shaded by the sun's angle.
  - **Twilight band:** a smooth fade with a warm tint, instead of a hard line between day and
    night.
  - **Night side:** city lights that fade in as the sun sets, plus faint moonlit ground.
  - **Atmosphere:** a shell slightly bigger than the planet. Its glow is blue on the day side,
    orange-purple at twilight and dark at night. Near the edge, the day side also gets a light
    blue haze.
- **A once-a-minute redraw** while the wallpaper is visible. The sun moves 0.25° per minute,
  so this keeps the day/night line current at almost no cost.
- A `clock` setting on the renderer, so test builds can show any time of day.

## Data

- **City lights:** NASA **Black Marble 2012** (`VIIRS_CityLights_2012` on GIBS). The 2016
  version has dimmer lights. The image includes moonlit land in blue-purple, so each color
  channel was darkened with ImageMagick to leave only the lights on black. The shader adds its
  own moonlight instead.

## Problems found

- **The Sahara looked grey at night.** The moonlight term multiplied the daytime colors, and
  bright deserts stayed too visible. It was turned down from ×6 to ×2.5.
- **The glow seemed blue all the way round** in small screenshots. Seen at full size it was
  correct: the night-side glow was a dim purple twilight.

## Result

Checked at three times of day on the emulator: 08:00, 17:30 and 22:00 UTC. The day/night line,
the city lights and the glow all matched where the sun really was.
