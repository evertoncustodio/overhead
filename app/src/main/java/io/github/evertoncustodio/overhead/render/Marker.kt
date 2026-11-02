package io.github.evertoncustodio.overhead.render

import kotlin.math.sqrt

/**
 * Where the dot that marks the centered place lands on screen. The shader draws it as a round
 * dot of fixed size, not painted on the ground, so all it needs is this position, worked out
 * once per frame here instead of for every pixel.
 */
object Marker {

    /**
     * Returns x and y in the shader's screen units (half the shorter side is 1, +Y up), and how
     * visible the place is, 0..1: it fades out as the place turns toward the planet's edge.
     * [shown] is the point facing the camera, which differs from [place] while the globe pans.
     */
    fun screenPosition(place: GeoPoint, shown: GeoPoint, cameraDistance: Float, globeScreenRadius: Float): FloatArray {
        // Turn the place into view space. The matrix maps view to Earth; a rotation's inverse
        // is its transpose, so each view axis is the dot product with one of its columns.
        val m = shown.viewToEarthMatrix()
        val p = place.toUnitVector()
        val x = m[0] * p[0] + m[1] * p[1] + m[2] * p[2]
        val y = m[3] * p[0] + m[4] * p[1] + m[5] * p[2]
        val z = m[6] * p[0] + m[7] * p[1] + m[8] * p[2]

        // The same perspective as the shader's camera, which sits on +Z looking at the center.
        val tanEdge = 1f / sqrt(cameraDistance * cameraDistance - 1f)
        val tanHalfFov = tanEdge / globeScreenRadius
        val depth = cameraDistance - z
        val screenX = x / (depth * tanHalfFov)
        val screenY = y / (depth * tanHalfFov)

        // Points with z below 1/distance are past the planet's edge, on the side we can't see.
        val horizon = 1f / cameraDistance
        val t = ((z - horizon) / EDGE_FADE).coerceIn(0f, 1f)
        val visibility = t * t * (3f - 2f * t)  // smoothstep
        return floatArrayOf(screenX, screenY, visibility)
    }

    // How far before the edge the dot starts fading, as a difference in z.
    private const val EDGE_FADE = 0.1f
}
