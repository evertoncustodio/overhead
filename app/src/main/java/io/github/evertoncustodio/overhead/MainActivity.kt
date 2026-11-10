package io.github.evertoncustodio.overhead

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.evertoncustodio.overhead.clouds.CloudStore
import io.github.evertoncustodio.overhead.clouds.CloudUpdateJob
import io.github.evertoncustodio.overhead.location.DeviceLocation
import io.github.evertoncustodio.overhead.location.LocationStore
import io.github.evertoncustodio.overhead.location.PlaceNames
import io.github.evertoncustodio.overhead.render.GeoPoint
import io.github.evertoncustodio.overhead.render.GlobeRenderer
import io.github.evertoncustodio.overhead.wallpaper.OverheadWallpaperService
import io.github.evertoncustodio.overhead.wallpaper.AnimationStore
import io.github.evertoncustodio.overhead.wallpaper.SkyStore
import io.github.evertoncustodio.overhead.wallpaper.ZoomStore
import kotlin.math.roundToInt

/**
 * Settings screen: where the globe is centered, how big it is, how the sky is turned, whether
 * it animates as the screen turns on, and a button to apply the wallpaper.
 */
class MainActivity : Activity() {

    private lateinit var store: LocationStore
    private lateinit var clouds: CloudStore
    private lateinit var zoom: ZoomStore
    private lateinit var sky: SkyStore
    private lateinit var preview: GLSurfaceView
    private lateinit var previewRenderer: GlobeRenderer
    private lateinit var previewCaption: TextView
    private lateinit var placeName: TextView
    private lateinit var placeCoordinates: TextView
    private lateinit var placeSource: TextView
    private lateinit var cloudsStatus: TextView
    private lateinit var status: TextView

    // Kept in a field: SharedPreferences only holds listeners weakly.
    private val locationChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> showPlace() }
    private val cloudsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        showClouds()
        if (key == CloudStore.MAP_VERSION_KEY) {
            preview.queueEvent { previewRenderer.reloadClouds() }
            preview.requestRender()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = LocationStore(this)
        clouds = CloudStore(this)
        zoom = ZoomStore(this)
        sky = SkyStore(this)
        placeName = findViewById(R.id.place_name)
        placeCoordinates = findViewById(R.id.place_coordinates)
        placeSource = findViewById(R.id.place_source)
        cloudsStatus = findViewById(R.id.clouds_status)
        status = findViewById(R.id.status)

        findViewById<Button>(R.id.use_my_location).setOnClickListener { useMyLocation() }
        findViewById<Button>(R.id.choose_place).setOnClickListener { showChoosePlaceDialog() }
        findViewById<Button>(R.id.set_wallpaper).setOnClickListener { openWallpaperPicker() }
        findViewById<Switch>(R.id.show_marker).apply {
            isChecked = store.showMarker
            setOnCheckedChangeListener { _, checked -> store.showMarker = checked }
        }
        setUpPreview()
        setUpZoomSlider(R.id.home_zoom, R.id.home_zoom_label, R.string.home_zoom, lockScreen = false)
        setUpZoomSlider(R.id.lock_zoom, R.id.lock_zoom_label, R.string.lock_zoom, lockScreen = true)
        findViewById<Switch>(R.id.keep_milky_way_in_view).apply {
            isChecked = sky.keepMilkyWayInView
            setOnCheckedChangeListener { _, checked ->
                sky.keepMilkyWayInView = checked
                previewRenderer.keepMilkyWayInView = checked
                preview.requestRender()
            }
        }

        findViewById<Switch>(R.id.screen_on_animation).apply {
            val animations = AnimationStore(this@MainActivity)
            isChecked = animations.screenOn
            setOnCheckedChangeListener { _, checked -> animations.screenOn = checked }
        }

        CloudUpdateJob.schedulePeriodic(this)
    }

    override fun onResume() {
        super.onResume()
        store.registerListener(locationChanged)
        clouds.registerListener(cloudsChanged)
        preview.onResume()
        showPlace()
        showClouds()
        if (clouds.isStale()) CloudUpdateJob.requestNow(this)
        status.setText(if (isOverheadActive()) R.string.status_active else R.string.status_inactive)

        // Opening the app is a good moment to refresh the location, if we're allowed to.
        if (store.mode == LocationStore.Mode.DEVICE && DeviceLocation.hasPermission(this)) {
            refreshDeviceLocation(quiet = true)
        }
    }

    override fun onPause() {
        super.onPause()
        store.unregisterListener(locationChanged)
        clouds.unregisterListener(cloudsChanged)
        preview.onPause()
    }

    private fun showPlace() {
        val place = store.current()
        placeName.text = place.name ?: place.point.format()
        placeCoordinates.text = if (place.name != null) place.point.format() else ""
        placeSource.setText(
            when {
                place.isGuess -> R.string.source_guess
                place.mode == LocationStore.Mode.MANUAL -> R.string.source_manual
                else -> R.string.source_device
            }
        )
        previewRenderer.center = place.point
        previewRenderer.showMarker = store.showMarker
        preview.requestRender()
    }

    private fun showClouds() {
        val updatedAt = clouds.updatedAt
        val now = System.currentTimeMillis()
        cloudsStatus.text = when {
            updatedAt == 0L -> getString(R.string.clouds_waiting)
            now - updatedAt < DateUtils.MINUTE_IN_MILLIS -> getString(R.string.clouds_updated_now)
            else -> {
                val ago = DateUtils.getRelativeTimeSpanString(updatedAt, now, DateUtils.MINUTE_IN_MILLIS)
                getString(R.string.clouds_updated, ago.toString().lowercase())
            }
        }
    }

    // --- Globe size ----------------------------------------------------------------

    /** A small live globe, drawn the same way as the wallpaper, to try sizes without leaving. */
    private fun setUpPreview() {
        previewCaption = findViewById(R.id.preview_caption)
        previewRenderer = GlobeRenderer(assets, clouds.file)
        previewRenderer.keepMilkyWayInView = sky.keepMilkyWayInView
        preview = findViewById<GLSurfaceView>(R.id.globe_preview).apply {
            setEGLContextClientVersion(3)
            setRenderer(previewRenderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            // Shaped like the screen, so the globe fills it the way it fills the real one.
            val metrics = resources.displayMetrics
            val shape = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat() /
                minOf(metrics.widthPixels, metrics.heightPixels)
            layoutParams.height = (resources.getDimensionPixelSize(R.dimen.preview_width) * shape).roundToInt()
        }
        showInPreview(lockScreen = false)
    }

    private fun setUpZoomSlider(sliderId: Int, labelId: Int, labelText: Int, lockScreen: Boolean) {
        val label = findViewById<TextView>(labelId)
        findViewById<SeekBar>(sliderId).apply {
            // Whole percentages from the smallest to the largest size.
            min = percent(ZoomStore.MIN)
            max = percent(ZoomStore.MAX)
            progress = percent(if (lockScreen) zoom.lock else zoom.home)
            label.text = getString(labelText, progress)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    label.text = getString(labelText, progress)
                    if (!fromUser) return
                    if (lockScreen) zoom.lock = progress / 100f else zoom.home = progress / 100f
                    showInPreview(lockScreen)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = showInPreview(lockScreen)

                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
    }

    /** Shows the home or lock screen size in the preview, whichever slider was touched last. */
    private fun showInPreview(lockScreen: Boolean) {
        previewRenderer.zoom = if (lockScreen) zoom.lock else zoom.home
        previewCaption.setText(if (lockScreen) R.string.preview_lock else R.string.preview_home)
        preview.requestRender()
    }

    private fun percent(value: Float): Int = (value * 100).roundToInt()

    // --- Device location -------------------------------------------------------

    private fun useMyLocation() {
        store.useDeviceLocation()
        if (DeviceLocation.hasAllPermissions(this)) {
            refreshDeviceLocation(quiet = false)
        } else {
            requestPermissions(DeviceLocation.PERMISSIONS_TO_REQUEST, REQUEST_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_LOCATION) return

        when {
            DeviceLocation.hasPermission(this) -> refreshDeviceLocation(quiet = false)
            // Android stops showing the permission prompt after the user denies it twice.
            !shouldShowRequestPermissionRationale(DeviceLocation.PERMISSION) -> showPermissionBlockedDialog()
            else -> toast(getString(R.string.permission_denied))
        }
    }

    private fun refreshDeviceLocation(quiet: Boolean) {
        if (!DeviceLocation.isEnabled(this)) {
            if (!quiet) toast(getString(R.string.location_off))
            return
        }
        if (!quiet) placeSource.setText(R.string.finding_location)
        DeviceLocation.request(this) { point ->
            if (point != null) {
                store.updateDeviceLocation(point)
            } else if (!quiet) {
                toast(getString(R.string.location_failed))
            }
            showPlace()
        }
    }

    private fun showPermissionBlockedDialog() {
        AlertDialog.Builder(this)
            .setMessage(R.string.permission_blocked)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // --- Choosing a place by hand ----------------------------------------------

    private fun showChoosePlaceDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.choose_place_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            isSingleLine = true
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.choose_place)
            .setView(container)
            .setPositiveButton(R.string.search) { _, _ -> searchPlace(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            dialog.dismiss()
            searchPlace(input.text.toString())
            true
        }
        dialog.show()
    }

    private fun searchPlace(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return

        GeoPoint.parse(text)?.let { point ->
            store.saveManualPlace(point, name = null)
            return
        }

        toast(getString(R.string.searching))
        PlaceNames.search(this, text) { results ->
            when (results.size) {
                0 -> toast(getString(R.string.no_places_found, text))
                1 -> store.saveManualPlace(results[0].point, results[0].name)
                else -> AlertDialog.Builder(this)
                    .setTitle(R.string.pick_a_place)
                    .setItems(results.map { it.name }.toTypedArray()) { _, which ->
                        store.saveManualPlace(results[which].point, results[which].name)
                    }
                    .show()
            }
        }
    }

    // --- Wallpaper ---------------------------------------------------------------

    private fun isOverheadActive(): Boolean =
        WallpaperManager.getInstance(this).wallpaperInfo?.packageName == packageName

    private fun openWallpaperPicker() {
        val preview = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, OverheadWallpaperService::class.java),
        )
        try {
            startActivity(preview)
        } catch (_: ActivityNotFoundException) {
            // Some launchers don't support opening a specific wallpaper; fall back to the full list.
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private companion object {
        const val REQUEST_LOCATION = 1
    }
}
