package dev.raul.tvhome

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LiveStream(
    val login: String,
    val displayName: String,
    val title: String,
    val game: String,
    val viewers: Int,
    val thumbnail: ImageBitmap?,
    val avatar: ImageBitmap?,
)

data class OfflineStreamer(val login: String, val displayName: String, val avatar: ImageBitmap?)

data class TwitchState(
    /** False until Twitch keys are in local.properties; the card and count stay hidden. */
    val configured: Boolean,
    val loaded: Boolean = false,
    val live: List<LiveStream> = emptyList(),
    val offline: List<OfflineStreamer> = emptyList(),
)

/**
 * Asks Twitch which of [logins] are live, using an app token (no Twitch sign-in needed).
 * Only runs when the Twitch tile is selected, and at most once a minute.
 */
class TwitchRepo(
    private val clientId: String,
    private val clientSecret: String,
    private val logins: List<String>,
) {
    private val _state = MutableStateFlow(
        TwitchState(configured = clientId.isNotBlank() && clientSecret.isNotBlank() && logins.isNotEmpty())
    )
    val state: StateFlow<TwitchState> = _state

    private val mutex = Mutex()
    private var token: String? = null
    private var lastFetch = 0L
    private val avatarCache = mutableMapOf<String, ImageBitmap?>()

    suspend fun refreshIfStale(maxAgeMs: Long = 60_000) {
        if (!_state.value.configured) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val now = SystemClock.elapsedRealtime()
                if (lastFetch != 0L && now - lastFetch < maxAgeMs) return@withLock
                runCatching { fetch() }
                    .onSuccess { lastFetch = now }
                    .onFailure { Log.w(TAG, "Twitch check failed", it) }
            }
        }
    }

    private fun fetch() {
        val users = helix("users?" + logins.joinToString("&") { "login=" + enc(it) }).getJSONArray("data")
        val names = mutableMapOf<String, String>()
        for (i in 0 until users.length()) {
            val u = users.getJSONObject(i)
            val login = u.getString("login")
            names[login] = u.optString("display_name", login)
            if (login !in avatarCache) avatarCache[login] = loadImage(u.optString("profile_image_url"))
        }

        val streams = helix("streams?" + logins.joinToString("&") { "user_login=" + enc(it) }).getJSONArray("data")
        // Twitch refreshes previews about every 5 minutes; the suffix stops a stale cached copy.
        val bucket = System.currentTimeMillis() / 300_000
        val live = (0 until streams.length()).map { i ->
            val s = streams.getJSONObject(i)
            val login = s.getString("user_login")
            LiveStream(
                login = login,
                displayName = s.optString("user_name", login),
                title = s.optString("title"),
                game = s.optString("game_name"),
                viewers = s.optInt("viewer_count"),
                thumbnail = loadImage(
                    s.optString("thumbnail_url").replace("{width}", "640").replace("{height}", "360") + "?b=$bucket"
                ),
                avatar = avatarCache[login],
            )
        }
        val liveLogins = live.map { it.login }.toSet()
        val offline = logins.filter { it !in liveLogins }
            .map { OfflineStreamer(it, names[it] ?: it, avatarCache[it]) }

        _state.value = _state.value.copy(loaded = true, live = live, offline = offline)
    }

    private fun helix(path: String): JSONObject {
        repeat(2) { attempt ->
            val t = token ?: appToken().also { token = it }
            val conn = (URL("https://api.twitch.tv/helix/$path").openConnection() as HttpURLConnection).apply {
                setRequestProperty("Client-Id", clientId)
                setRequestProperty("Authorization", "Bearer $t")
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            try {
                when (conn.responseCode) {
                    200 -> return JSONObject(conn.inputStream.bufferedReader().readText())
                    401 -> if (attempt == 0) token = null else error("Twitch rejected the keys (401)")
                    else -> error("Twitch returned ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }
        }
        error("Twitch request failed")
    }

    private fun appToken(): String {
        val body = "client_id=${enc(clientId)}&client_secret=${enc(clientSecret)}&grant_type=client_credentials"
        val conn = (URL("https://id.twitch.tv/oauth2/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode != 200) error("Twitch token request returned ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().readText()).getString("access_token")
        } finally {
            conn.disconnect()
        }
    }

    private fun loadImage(url: String): ImageBitmap? {
        if (url.isBlank()) return null
        return runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            try {
                conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val TAG = "TwitchRepo"
    }
}

/** Opens [login]'s stream directly in the Twitch app, falling back to the app's start screen. */
fun openTwitchStream(context: Context, login: String) {
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("twitch://stream/$login")).setPackage(TWITCH_PACKAGE),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.twitch.tv/$login")).setPackage(TWITCH_PACKAGE),
    )
    for (intent in attempts) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // try the next form
        }
    }
    context.packageManager.getLeanbackLaunchIntentForPackage(TWITCH_PACKAGE)?.let {
        context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
