package dev.raul.tvhome

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap

const val TWITCH_PACKAGE = "tv.twitch.android.app"

/** The streamers the Twitch card checks, from `twitch.streamers` in local.properties. */
val FAVOURITE_STREAMERS: List<String> = csv(BuildConfig.TWITCH_STREAMERS).map { it.lowercase() }

private fun csv(value: String) = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** The dock holds at most this many apps, so tiles stay readable from the sofa. */
const val MAX_DOCK = 8

private data class Known(val tagline: String, val accent: Color)

/** Taglines and tint colours for apps we know; anything else gets a neutral tint. */
private val KNOWN = mapOf(
    "com.google.android.youtube.tv" to Known("Pick up where you left off", Color(0xFFFF3B30)),
    "com.google.android.youtube.tvmusic" to Known("Your mixes and playlists", Color(0xFFFF2D55)),
    TWITCH_PACKAGE to Known("See who is live", Color(0xFF8A4BFF)),
    "com.plexapp.android" to Known("Your library, ready to stream", Color(0xFFFFB800)),
    "com.netflix.ninja" to Known("Continue watching", Color(0xFFE50914)),
    "com.comedybox.app" to Known("Stand-up, on demand", Color(0xFF2DD4BF)),
    "com.disney.disneyplus" to Known("Movies and series", Color(0xFF1E3E9E)),
    "com.amazon.amazonvideo.livingroom" to Known("Movies and series", Color(0xFF22B8F0)),
)

/**
 * The dock on first launch, left to right: `dock.apps` in local.properties, or this list.
 * Apps that aren't installed are skipped. After first launch the dock is edited on the TV.
 */
val DEFAULT_DOCK: List<String> = csv(BuildConfig.DOCK_APPS).ifEmpty {
    listOf(
        "com.google.android.youtube.tv",
        "com.google.android.youtube.tvmusic",
        TWITCH_PACKAGE,
        "com.plexapp.android",
        "com.netflix.ninja",
    )
}

data class DockApp(
    val packageName: String,
    val label: String,
    val tagline: String,
    val accent: Color,
    /** The app's own 16:9 TV banner, or its square icon when it has none. */
    val art: ImageBitmap?,
    val artIsBanner: Boolean,
    val launch: Intent,
)

/** Every app that can be opened from a TV home screen, keyed by package name. */
fun loadAllApps(context: Context): Map<String, DockApp> {
    val pm = context.packageManager
    val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    val packages = (pm.queryIntentActivities(leanback, 0).map { it.activityInfo.packageName } + DEFAULT_DOCK)
        .distinct()
        .filter { it != context.packageName }
    return packages.mapNotNull { loadApp(pm, it) }.associateBy { it.packageName }
}

private fun loadApp(pm: PackageManager, pkg: String): DockApp? {
    val launch = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return null
    val info = try {
        pm.getApplicationInfo(pkg, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        return null
    }
    val known = KNOWN[pkg]
    val banner = runCatching { pm.getActivityBanner(launch) ?: pm.getApplicationBanner(info) }.getOrNull()
    val art = banner ?: runCatching { pm.getApplicationIcon(info) }.getOrNull()
    return DockApp(
        packageName = pkg,
        label = info.loadLabel(pm).toString(),
        tagline = known?.tagline ?: "Press OK to open",
        accent = known?.accent ?: Color(0xFF8E8E93),
        art = art?.let {
            val w = it.intrinsicWidth.takeIf { w -> w > 0 } ?: 320
            val h = it.intrinsicHeight.takeIf { h -> h > 0 } ?: 180
            it.toBitmap(w, h).asImageBitmap()
        },
        artIsBanner = banner != null,
        launch = launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Remembers the dock order on the TV, so it survives restarts. */
class DockStore(context: Context) {
    private val prefs = context.getSharedPreferences("dock", Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getString(KEY, null)?.split(',')?.filter { it.isNotBlank() } ?: DEFAULT_DOCK

    fun save(packages: List<String>) = prefs.edit { putString(KEY, packages.joinToString(",")) }

    private companion object {
        const val KEY = "order"
    }
}
