package com.example.reelsblock

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal val Context.guardDataStore by preferencesDataStore(name = "guard_settings")

data class GuardSettings(
    val disclosureAccepted: Boolean = false,
    val debugDiagnostics: Boolean = false,
    val autoSkipBlockedItems: Boolean = false,
    val stopAtEndOfFollowedContent: Boolean = true,
    val consecutiveBlockedGroups: Int = 3,
    val blockExplore: Boolean = true,
    val showToast: Boolean = false,
)

class GuardSettingsStore(private val dataStore: DataStore<Preferences>) {
    private val disclosureAccepted = booleanPreferencesKey("disclosure_accepted")
    private val debugDiagnostics = booleanPreferencesKey("debug_diagnostics")
    private val autoSkipBlockedItems = booleanPreferencesKey("auto_skip_blocked_items")
    private val stopAtEnd = booleanPreferencesKey("stop_at_end_of_followed_content")
    private val blockedGroups = intPreferencesKey("consecutive_blocked_groups")
    private val blockExplore = booleanPreferencesKey("block_explore")
    private val showToast = booleanPreferencesKey("show_toast")

    val settings = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences ->
        GuardSettings(
            disclosureAccepted = preferences[disclosureAccepted] ?: false,
            debugDiagnostics = preferences[debugDiagnostics] ?: false,
            autoSkipBlockedItems = preferences[autoSkipBlockedItems] ?: false,
            stopAtEndOfFollowedContent = preferences[stopAtEnd] ?: true,
            consecutiveBlockedGroups = (preferences[blockedGroups] ?: 3).coerceAtLeast(1),
            blockExplore = preferences[blockExplore] ?: true,
            showToast = preferences[showToast] ?: false,
        )
    }

    suspend fun acceptDisclosure() {
        dataStore.edit { it[disclosureAccepted] = true }
    }

    suspend fun setDebugDiagnostics(enabled: Boolean) {
        dataStore.edit { it[debugDiagnostics] = enabled }
    }

    suspend fun setAutoSkipBlockedItems(enabled: Boolean) {
        dataStore.edit { it[autoSkipBlockedItems] = enabled }
    }

    suspend fun setStopAtEndOfFollowedContent(enabled: Boolean) {
        dataStore.edit { it[stopAtEnd] = enabled }
    }

    suspend fun setConsecutiveBlockedGroups(count: Int) {
        require(count >= 1)
        dataStore.edit { it[blockedGroups] = count }
    }

    suspend fun setBlockExplore(enabled: Boolean) {
        dataStore.edit { it[blockExplore] = enabled }
    }

    suspend fun setShowToast(enabled: Boolean) {
        dataStore.edit { it[showToast] = enabled }
    }
}
