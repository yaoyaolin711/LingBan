package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.koin.compose.koinInject

@Composable
fun rememberUserSettingsState(): State<Settings> {
    val store = koinInject<SettingsStore>()
    // collectAsState (not WithLifecycle): RikkahubTheme is also used in Application overlays
    // (TaskBall) where LocalLifecycleOwner is absent.
    return store.settingsFlow.collectAsState(initial = Settings.dummy())
}
