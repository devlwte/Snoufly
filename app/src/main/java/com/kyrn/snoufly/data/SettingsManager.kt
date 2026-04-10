package com.kyrn.snoufly.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.kyrn.snoufly.ui.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class SettingsManager(private val context: Context) {

    private object PreferencesKeys {
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val MIN_DURATION = longPreferencesKey("min_duration")
        val FAVORITE_IDS = stringSetPreferencesKey("favorite_ids")
        val RECENTLY_PLAYED_IDS = stringPreferencesKey("recently_played_ids")
        val PLAY_COUNTS = stringPreferencesKey("play_counts")
        val CUSTOM_METADATA_MAP = stringPreferencesKey("custom_metadata_map")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_BANDS = stringPreferencesKey("eq_bands") 
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val PLAYBACK_PITCH = floatPreferencesKey("playback_pitch")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val themeName = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
            try { ThemeMode.valueOf(themeName) } catch (e: Exception) { ThemeMode.SYSTEM }
        }

    val sortOrderFlow: Flow<SortOrder> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val orderName = preferences[PreferencesKeys.SORT_ORDER] ?: SortOrder.RECENTLY_ADDED.name
            try { SortOrder.valueOf(orderName) } catch (e: Exception) { SortOrder.RECENTLY_ADDED }
        }

    val minDurationFlow: Flow<Long> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[PreferencesKeys.MIN_DURATION] ?: 30000L }

    val favoriteIdsFlow: Flow<Set<Long>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.FAVORITE_IDS]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        }

    val recentlyPlayedIdsFlow: Flow<List<Long>> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.RECENTLY_PLAYED_IDS]?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
        }

    val playCountsFlow: Flow<Map<Long, Int>> = context.dataStore.data
        .map { preferences ->
            val raw = preferences[PreferencesKeys.PLAY_COUNTS] ?: ""
            if (raw.isBlank()) emptyMap()
            else {
                raw.split(",").filter { it.contains(":") }.associate {
                    val parts = it.split(":")
                    parts[0].toLong() to parts[1].toInt()
                }
            }
        }

    val customMetadataFlow: Flow<Map<Long, CustomMetadata>> = context.dataStore.data
        .map { preferences ->
            val raw = preferences[PreferencesKeys.CUSTOM_METADATA_MAP] ?: ""
            if (raw.isBlank()) emptyMap()
            else {
                raw.split("||").filter { it.contains("::") }.associate {
                    val entryParts = it.split("::")
                    entryParts[0].toLong() to CustomMetadata.deserialize(entryParts[1])
                }
            }
        }

    val eqEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.EQ_ENABLED] ?: false }
    
    val eqBandsFlow: Flow<List<Int>> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.EQ_BANDS]?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
    }
    
    val playbackSpeedFlow: Flow<Float> = context.dataStore.data.map { it[PreferencesKeys.PLAYBACK_SPEED] ?: 1.0f }

    val playbackPitchFlow: Flow<Float> = context.dataStore.data.map { it[PreferencesKeys.PLAYBACK_PITCH] ?: 1.0f }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.THEME_MODE] = mode.name }
    }

    suspend fun updateSortOrder(order: SortOrder) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.SORT_ORDER] = order.name }
    }

    suspend fun updateMinDuration(durationMs: Long) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.MIN_DURATION] = durationMs }
    }

    suspend fun toggleFavorite(songId: Long) {
        context.dataStore.edit { preferences ->
            val currentSet = preferences[PreferencesKeys.FAVORITE_IDS] ?: emptySet()
            val newSet = currentSet.toMutableSet()
            val idStr = songId.toString()
            if (newSet.contains(idStr)) newSet.remove(idStr) else newSet.add(idStr)
            preferences[PreferencesKeys.FAVORITE_IDS] = newSet
        }
    }

    suspend fun updateRecentlyPlayed(ids: List<Long>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECENTLY_PLAYED_IDS] = ids.joinToString(",")
        }
    }

    suspend fun incrementPlayCount(songId: Long) {
        context.dataStore.edit { preferences ->
            val raw = preferences[PreferencesKeys.PLAY_COUNTS] ?: ""
            val currentMap = if (raw.isBlank()) mutableMapOf<Long, Int>()
            else {
                raw.split(",").filter { it.contains(":") }.associate {
                    val parts = it.split(":")
                    parts[0].toLong() to parts[1].toInt()
                }.toMutableMap()
            }
            currentMap[songId] = (currentMap[songId] ?: 0) + 1
            preferences[PreferencesKeys.PLAY_COUNTS] = currentMap.map { "${it.key}:${it.value}" }.joinToString(",")
        }
    }

    suspend fun updateCustomMetadata(songId: Long, metadata: CustomMetadata) {
        context.dataStore.edit { preferences ->
            val raw = preferences[PreferencesKeys.CUSTOM_METADATA_MAP] ?: ""
            val currentMap = if (raw.isBlank()) mutableMapOf<Long, CustomMetadata>()
            else {
                raw.split("||").filter { it.contains("::") }.associate {
                    val entryParts = it.split("::")
                    entryParts[0].toLong() to CustomMetadata.deserialize(entryParts[1])
                }.toMutableMap()
            }
            currentMap[songId] = metadata
            preferences[PreferencesKeys.CUSTOM_METADATA_MAP] = currentMap.map { "${it.key}::${it.value.serialize()}" }.joinToString("||")
        }
    }

    suspend fun updateEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.EQ_ENABLED] = enabled }
    }

    suspend fun updateEqBands(bands: List<Int>) {
        context.dataStore.edit { it[PreferencesKeys.EQ_BANDS] = bands.joinToString(",") }
    }

    suspend fun updatePlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[PreferencesKeys.PLAYBACK_SPEED] = speed }
    }

    suspend fun updatePlaybackPitch(pitch: Float) {
        context.dataStore.edit { it[PreferencesKeys.PLAYBACK_PITCH] = pitch }
    }
}
