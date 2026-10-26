package com.hisbaan.notiq.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisbaan.notiq.core.FillMode
import com.hisbaan.notiq.core.SourcePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class NotiqSettings(
    /** Copy each code to the clipboard as it arrives. */
    val copyCodes: Boolean = true,
    /** Post Notiq's own notification with the code and a Copy button. */
    val showNotification: Boolean = true,
    /** Clear the clipboard this long after copying a code, if it still holds it; 0 never. */
    val clearAfterSeconds: Int = 0,
    // Sources, see SourcePolicy.
    val messagingApps: Boolean = true,
    val emailApps: Boolean = false,
    val systemFlagged: Boolean = true,
    val allApps: Boolean = false,
    /** How a likely code field gets the code, when the fill service is on. */
    val fillMode: FillMode = FillMode.CHIP,
    /** After filling, press Enter if the app hasn't moved on by itself. */
    val submitAfterFill: Boolean = true,
) {
    val sources: SourcePolicy get() = SourcePolicy(messagingApps, emailApps, systemFlagged, allApps)
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    /** Null until first read. */
    val settings: StateFlow<NotiqSettings?> = store.data
        .map { decode(it) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Waits for the first read. The listener often starts the process itself, and a notification
     * can arrive before DataStore has loaded.
     */
    suspend fun current(): NotiqSettings = settings.filterNotNull().first()

    suspend fun update(transform: (NotiqSettings) -> NotiqSettings) {
        store.edit { it[SETTINGS] = json.encodeToString(transform(decode(it))) }
    }

    private fun decode(prefs: Preferences): NotiqSettings =
        prefs[SETTINGS]?.let {
            try {
                json.decodeFromString<NotiqSettings>(it)
            } catch (_: SerializationException) {
                null
            }
        } ?: NotiqSettings()

    private companion object {
        val SETTINGS = stringPreferencesKey("settings")
    }
}
