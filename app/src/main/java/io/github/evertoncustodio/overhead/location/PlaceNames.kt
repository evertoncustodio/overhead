package io.github.evertoncustodio.overhead.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.evertoncustodio.overhead.render.GeoPoint
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Turns place names into coordinates and back, using the phone's built-in Geocoder.
 * The Geocoder needs a network connection and isn't available on every phone, so
 * every lookup may come back empty. Callbacks always run on the main thread.
 */
object PlaceNames {

    data class Result(val point: GeoPoint, val name: String)

    private val background = Executors.newSingleThreadExecutor()
    private val mainThread = Handler(Looper.getMainLooper())

    /** Finds up to five places matching [query], e.g. "Lisbon" or "Tokyo, Japan". */
    fun search(context: Context, query: String, callback: (List<Result>) -> Unit) = lookUp(
        context, callback,
        asyncQuery = { geocoder, listener -> geocoder.getFromLocationName(query, MAX_RESULTS, listener) },
        blockingQuery = @Suppress("DEPRECATION") { geocoder -> geocoder.getFromLocationName(query, MAX_RESULTS).orEmpty() },
    )

    /** Finds a short name like "Lisbon, Portugal" for a point, or null. */
    fun nameOf(context: Context, point: GeoPoint, callback: (String?) -> Unit) = lookUp(
        context, { results -> callback(results.firstOrNull()?.name) },
        asyncQuery = { geocoder, listener -> geocoder.getFromLocation(point.latitude, point.longitude, 1, listener) },
        blockingQuery = @Suppress("DEPRECATION") { geocoder -> geocoder.getFromLocation(point.latitude, point.longitude, 1).orEmpty() },
    )

    /**
     * Runs a Geocoder query. Android 13 added an asynchronous version of each call;
     * older versions only have a blocking one, which must run off the main thread.
     */
    private fun lookUp(
        context: Context,
        callback: (List<Result>) -> Unit,
        asyncQuery: (Geocoder, Geocoder.GeocodeListener) -> Unit,
        blockingQuery: (Geocoder) -> List<Address>,
    ) {
        if (!Geocoder.isPresent()) return callback(emptyList())
        val geocoder = Geocoder(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            asyncQuery(geocoder, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) =
                    deliver(callback, addresses.mapNotNull { it.toResult() })

                override fun onError(errorMessage: String?) = deliver(callback, emptyList())
            })
        } else {
            background.execute {
                val results = try {
                    blockingQuery(geocoder).mapNotNull { it.toResult() }
                } catch (_: IOException) {
                    emptyList()
                } catch (_: IllegalArgumentException) {
                    emptyList()
                }
                deliver(callback, results)
            }
        }
    }

    private fun deliver(callback: (List<Result>) -> Unit, results: List<Result>) {
        mainThread.post { callback(results) }
    }

    private fun Address.toResult(): Result? {
        if (!hasLatitude() || !hasLongitude()) return null
        val place = locality ?: subAdminArea ?: adminArea ?: featureName
        val name = listOfNotNull(place, countryName).distinct().joinToString(", ")
        return if (name.isEmpty()) null else Result(GeoPoint(latitude, longitude), name)
    }

    private const val MAX_RESULTS = 5
}
