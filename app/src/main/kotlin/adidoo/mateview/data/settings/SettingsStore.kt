// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ConnectionMode { HOME_ASSISTANT, DIRECT }

/** What the user configured. Tokens are never part of it: only whether one is stored. */
data class ConnectionSettings(
    val mode: ConnectionMode = ConnectionMode.HOME_ASSISTANT,
    val haUrl: String = "",
    val haPanel: String = DEFAULT_PANEL,
    val hasHaToken: Boolean = false,
    val grafanaUrl: String = "",
    val hasGrafanaToken: Boolean = false,
    /** True once a connection test succeeded with these settings. */
    val verified: Boolean = false,
    val carId: Int? = null,
    val carName: String? = null,
) {
    companion object {
        const val DEFAULT_PANEL = "teslamate_grafana"
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context, private val secrets: SecretStore) {
    private val store = context.applicationContext.dataStore

    val connection: Flow<ConnectionSettings> = store.data.map { prefs ->
        ConnectionSettings(
            mode = prefs[MODE]?.let { name -> ConnectionMode.entries.firstOrNull { it.name == name } }
                ?: ConnectionMode.HOME_ASSISTANT,
            haUrl = prefs[HA_URL].orEmpty(),
            haPanel = prefs[HA_PANEL] ?: ConnectionSettings.DEFAULT_PANEL,
            hasHaToken = prefs[HA_TOKEN] != null,
            grafanaUrl = prefs[GRAFANA_URL].orEmpty(),
            hasGrafanaToken = prefs[GRAFANA_TOKEN] != null,
            verified = prefs[VERIFIED] ?: false,
            carId = prefs[CAR_ID],
            carName = prefs[CAR_NAME],
        )
    }

    /**
     * Saves the connection fields. A null token keeps the stored one, an empty
     * one removes it. Any change invalidates the last successful test.
     */
    suspend fun saveConnection(
        mode: ConnectionMode,
        haUrl: String,
        haPanel: String,
        haToken: String?,
        grafanaUrl: String,
        grafanaToken: String?,
    ) {
        store.edit { prefs ->
            prefs[MODE] = mode.name
            prefs[HA_URL] = haUrl.trim()
            prefs[HA_PANEL] = haPanel.trim().ifEmpty { ConnectionSettings.DEFAULT_PANEL }
            prefs[GRAFANA_URL] = grafanaUrl.trim()
            prefs.putToken(HA_TOKEN, haToken)
            prefs.putToken(GRAFANA_TOKEN, grafanaToken)
            prefs[VERIFIED] = false
        }
    }

    suspend fun haToken(): String? = token(HA_TOKEN)
    suspend fun grafanaToken(): String? = token(GRAFANA_TOKEN)

    suspend fun setVerified(verified: Boolean) {
        store.edit { it[VERIFIED] = verified }
    }

    suspend fun selectCar(id: Int, name: String) {
        store.edit {
            it[CAR_ID] = id
            it[CAR_NAME] = name
        }
    }

    private suspend fun token(key: Preferences.Key<String>): String? =
        store.data.first()[key]?.let(secrets::decrypt)

    private fun androidx.datastore.preferences.core.MutablePreferences.putToken(
        key: Preferences.Key<String>,
        value: String?,
    ) {
        when {
            value == null -> Unit
            value.isBlank() -> remove(key)
            else -> this[key] = secrets.encrypt(value.trim())
        }
    }

    private companion object {
        val MODE = stringPreferencesKey("connection_mode")
        val HA_URL = stringPreferencesKey("ha_url")
        val HA_PANEL = stringPreferencesKey("ha_panel")
        val HA_TOKEN = stringPreferencesKey("ha_token")
        val GRAFANA_URL = stringPreferencesKey("grafana_url")
        val GRAFANA_TOKEN = stringPreferencesKey("grafana_token")
        val VERIFIED = booleanPreferencesKey("connection_verified")
        val CAR_ID = intPreferencesKey("car_id")
        val CAR_NAME = stringPreferencesKey("car_name")
    }
}
