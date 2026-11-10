package io.github.evertoncustodio.overhead.render

/**
 * The screen-on animation: the globe arrives turned a little to the east, a little smaller and
 * dim, then settles on your place as the sunlight comes up and city lights switch on.
 *
 * Each function takes the animation's progress, 0 as the screen turns on and 1 when settled,
 * and returns 1 (or no change) when it is finished, so a settled frame is drawn as before.
 */
object WakeAnimation {

    /** Extra turn of the globe in degrees of longitude, toward the east. */
    fun turnDegrees(progress: Float): Double = START_TURN_DEGREES * remaining(progress, 1f)

    /** Globe size relative to settled. */
    fun scale(progress: Float): Float = 1f - START_SHRINK * remaining(progress, 1f)

    /** Brightness of the planet and its glow, from dim to full in the first part. */
    fun brightness(progress: Float): Float = 1f - (1f - START_BRIGHTNESS) * remaining(progress, BRIGHTEN_END)

    /**
     * How far the city lights have got switching on, 0..1. The shader turns each town on at
     * its own moment within this, so they come on in scattered patches, not all at once.
     */
    fun cityLights(progress: Float): Float =
        ((progress - LIGHTS_START) / (1f - LIGHTS_START)).coerceIn(0f, 1f)

    /**
     * How much of a movement is still to go at [progress], for a movement that ends at [end]:
     * 1 at the start, then fast at first and slowing down to 0 (a cubic ease-out), so the
     * globe glides to a stop instead of halting.
     */
    private fun remaining(progress: Float, end: Float): Float {
        val left = 1f - (progress / end).coerceIn(0f, 1f)
        return left * left * left
    }

    // Turning from the east means the surface moves east as it settles, like the real Earth.
    private const val START_TURN_DEGREES = 10.0
    private const val START_SHRINK = 0.06f
    private const val START_BRIGHTNESS = 0.3f
    private const val BRIGHTEN_END = 0.7f
    private const val LIGHTS_START = 0.25f
}
