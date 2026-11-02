package io.github.evertoncustodio.overhead.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import io.github.evertoncustodio.overhead.render.GeoPoint

/**
 * Reads the device's approximate location through Android's own LocationManager
 * (no Google Play services needed, so it also works on phones without them).
 */
object DeviceLocation {

    /** The permission whose answer decides whether Overhead may follow the phone. */
    const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

    /**
     * What to ask for. Up to Android 11, apps with only approximate access can't use GPS at all,
     * so phones with only GPS turned on would never get a location; those versions also don't
     * offer an approximate/precise choice in the prompt. Either way the location is rounded
     * to about 11 km before it is saved (see LocationStore).
     */
    val PERMISSIONS_TO_REQUEST: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    fun hasPermission(context: Context): Boolean = isGranted(context, PERMISSION)

    /** False if anything in [PERMISSIONS_TO_REQUEST] is still missing, so it's worth asking. */
    fun hasAllPermissions(context: Context): Boolean = PERMISSIONS_TO_REQUEST.all { isGranted(context, it) }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** True when the phone's location setting (the quick-settings toggle) is on. */
    fun isEnabled(context: Context): Boolean =
        LocationManagerCompat.isLocationEnabled(locationManager(context))

    /**
     * The newest location any app on the phone received, without waiting for a new one.
     * Returns null when there is none, or when Android doesn't allow it right now
     * (for example while the app is in the background).
     */
    fun lastKnown(context: Context): GeoPoint? = newestLastKnown(context)?.toGeoPoint()

    /**
     * Gets a current location and calls [callback] on the main thread, with null if no
     * location could be found. Reuses a recent location when there is one.
     */
    fun request(context: Context, callback: (GeoPoint?) -> Unit) {
        if (!hasPermission(context)) return callback(null)

        val recent = newestLastKnown(context)
        if (recent != null && System.currentTimeMillis() - recent.time < RECENT_ENOUGH_MILLIS) {
            return callback(recent.toGeoPoint())
        }

        val manager = locationManager(context)
        val provider = bestProvider(context, manager) ?: return callback(recent?.toGeoPoint())
        try {
            LocationManagerCompat.getCurrentLocation(
                manager, provider, CancellationSignal(), ContextCompat.getMainExecutor(context),
            ) { location ->
                // If no fresh fix arrived in time, an older one is still better than nothing.
                callback((location ?: recent)?.toGeoPoint())
            }
        } catch (_: SecurityException) {
            callback(null)
        }
    }

    private fun newestLastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = locationManager(context)
        return try {
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun bestProvider(context: Context, manager: LocationManager): String? {
        val canUseGps = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ||
            isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (canUseGps) add(LocationManager.GPS_PROVIDER)
        }
        return candidates.firstOrNull { manager.isProviderEnabled(it) }
    }

    private fun locationManager(context: Context): LocationManager =
        context.getSystemService(LocationManager::class.java)

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)

    private const val RECENT_ENOUGH_MILLIS = 15 * 60 * 1000L
}
