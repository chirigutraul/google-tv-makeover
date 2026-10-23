package dev.raul.tvhome

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private lateinit var store: DockStore
    private var allApps by mutableStateOf(emptyMap<String, DockApp>())
    private var dock by mutableStateOf(emptyList<String>())
    private var wakeSignal by mutableIntStateOf(0)
    private val twitch = TwitchRepo(BuildConfig.TWITCH_CLIENT_ID, BuildConfig.TWITCH_CLIENT_SECRET, FAVOURITE_STREAMERS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DockStore(this)
        dock = store.load()
        allApps = loadAllApps(this)
        setContent {
            DesignScale {
                HomeScreen(
                    apps = dock.mapNotNull { allApps[it] },
                    allApps = allApps.values.sortedBy { it.label.lowercase() },
                    onDockChange = { packages ->
                        dock = packages
                        store.save(packages)
                    },
                    twitch = twitch,
                    wakeSignal = wakeSignal,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up apps installed or removed while we were in the background.
        allApps = loadAllApps(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The Home button while already home.
        wakeSignal++
    }
}
