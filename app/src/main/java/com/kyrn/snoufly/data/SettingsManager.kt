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

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class BackupInterval { DAILY, WEEKLY, MONTHLY, NEVER }

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
        val BACKUP_URI = stringPreferencesKey("backup_uri")
        val BACKUP_INTERVAL = stringPreferencesKey("backup_interval")
        val LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")
        val LYRICS_API_TEMPLATE = stringPreferencesKey("lyrics_api_template")
        val LYRICS_USER_AGENT = stringPreferencesKey("lyrics_user_agent")
        
        val LANG_REPO_URL = stringPreferencesKey("lang_repo_url")
        val SELECTED_LANG_CODE = stringPreferencesKey("selected_lang_code")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { p -> 
        val name = p[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        try { ThemeMode.valueOf(name) } catch (e: Exception) { ThemeMode.SYSTEM }
    }

    val sortOrderFlow: Flow<SortOrder> = context.dataStore.data.map { p ->
        val name = p[PreferencesKeys.SORT_ORDER] ?: SortOrder.RECENTLY_ADDED.name
        try { SortOrder.valueOf(name) } catch (e: Exception) { SortOrder.RECENTLY_ADDED }
    }

    val minDurationFlow: Flow<Long> = context.dataStore.data.map { it[PreferencesKeys.MIN_DURATION] ?: 30000L }
    val favoriteIdsFlow: Flow<Set<Long>> = context.dataStore.data.map { it[PreferencesKeys.FAVORITE_IDS]?.mapNotNull { s -> s.toLongOrNull() }?.toSet() ?: emptySet() }
    val recentlyPlayedIdsFlow: Flow<List<Long>> = context.dataStore.data.map { it[PreferencesKeys.RECENTLY_PLAYED_IDS]?.split(",")?.mapNotNull { s -> s.toLongOrNull() } ?: emptyList() }
    val playCountsFlow: Flow<Map<Long, Int>> = context.dataStore.data.map { p -> 
        val raw = p[PreferencesKeys.PLAY_COUNTS] ?: ""
        if (raw.isBlank()) emptyMap() else raw.split(",").filter { it.contains(":") }.associate { val pts = it.split(":"); pts[0].toLong() to pts[1].toInt() }
    }
    val customMetadataFlow: Flow<Map<Long, CustomMetadata>> = context.dataStore.data.map { p ->
        val raw = p[PreferencesKeys.CUSTOM_METADATA_MAP] ?: ""
        if (raw.isBlank()) emptyMap() else raw.split("||").filter { it.contains("::") }.associate { val pts = it.split("::"); pts[0].toLong() to CustomMetadata.deserialize(pts[1]) }
    }

    val eqEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.EQ_ENABLED] ?: false }
    val eqBandsFlow: Flow<List<Int>> = context.dataStore.data.map { it[PreferencesKeys.EQ_BANDS]?.split(",")?.mapNotNull { s -> s.toIntOrNull() } ?: emptyList() }
    val playbackSpeedFlow: Flow<Float> = context.dataStore.data.map { it[PreferencesKeys.PLAYBACK_SPEED] ?: 1.0f }
    val playbackPitchFlow: Flow<Float> = context.dataStore.data.map { it[PreferencesKeys.PLAYBACK_PITCH] ?: 1.0f }
    val backupUriFlow: Flow<String?> = context.dataStore.data.map { it[PreferencesKeys.BACKUP_URI] }
    val backupIntervalFlow: Flow<BackupInterval> = context.dataStore.data.map { 
        val name = it[PreferencesKeys.BACKUP_INTERVAL] ?: BackupInterval.NEVER.name
        try { BackupInterval.valueOf(name) } catch (e: Exception) { BackupInterval.NEVER }
    }
    val lastBackupTimeFlow: Flow<Long> = context.dataStore.data.map { it[PreferencesKeys.LAST_BACKUP_TIME] ?: 0L }
    val lyricsApiTemplateFlow: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.LYRICS_API_TEMPLATE] ?: "" }
    val lyricsUserAgentFlow: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.LYRICS_USER_AGENT] ?: "" }

    val langRepoUrlFlow: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.LANG_REPO_URL] ?: "https://raw.githubusercontent.com/devlwte/Snoufly/language" }
    val selectedLangCodeFlow: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.SELECTED_LANG_CODE] ?: "en" }

    suspend fun updateThemeMode(m: ThemeMode) { context.dataStore.edit { it[PreferencesKeys.THEME_MODE] = m.name } }
    suspend fun updateSortOrder(o: SortOrder) { context.dataStore.edit { it[PreferencesKeys.SORT_ORDER] = o.name } }
    suspend fun updateMinDuration(d: Long) { context.dataStore.edit { it[PreferencesKeys.MIN_DURATION] = d } }
    suspend fun toggleFavorite(id: Long) {
        context.dataStore.edit { p ->
            val set = (p[PreferencesKeys.FAVORITE_IDS] ?: emptySet()).toMutableSet()
            if (!set.add(id.toString())) set.remove(id.toString())
            p[PreferencesKeys.FAVORITE_IDS] = set
        }
    }
    suspend fun updateRecentlyPlayed(ids: List<Long>) { context.dataStore.edit { it[PreferencesKeys.RECENTLY_PLAYED_IDS] = ids.joinToString(",") } }
    suspend fun incrementPlayCount(id: Long) {
        context.dataStore.edit { p ->
            val raw = p[PreferencesKeys.PLAY_COUNTS] ?: ""
            val map = if (raw.isBlank()) mutableMapOf<Long, Int>() else raw.split(",").associate { val pts = it.split(":"); pts[0].toLong() to pts[1].toInt() }.toMutableMap()
            map[id] = (map[id] ?: 0) + 1
            p[PreferencesKeys.PLAY_COUNTS] = map.entries.joinToString(",") { "${it.key}:${it.value}" }
        }
    }
    suspend fun updateCustomMetadata(id: Long, meta: CustomMetadata) {
        context.dataStore.edit { p ->
            val raw = p[PreferencesKeys.CUSTOM_METADATA_MAP] ?: ""
            val map = if (raw.isBlank()) mutableMapOf<Long, CustomMetadata>() else raw.split("||").associate { val pts = it.split("::"); pts[0].toLong() to CustomMetadata.deserialize(pts[1]) }.toMutableMap()
            map[id] = meta
            p[PreferencesKeys.CUSTOM_METADATA_MAP] = map.entries.joinToString("||") { "${it.key}::${it.value.serialize()}" }
        }
    }
    suspend fun updateEqEnabled(e: Boolean) { context.dataStore.edit { it[PreferencesKeys.EQ_ENABLED] = e } }
    suspend fun updateEqBands(b: List<Int>) { context.dataStore.edit { it[PreferencesKeys.EQ_BANDS] = b.joinToString(",") } }
    suspend fun updatePlaybackSpeed(s: Float) { context.dataStore.edit { it[PreferencesKeys.PLAYBACK_SPEED] = s } }
    suspend fun updatePlaybackPitch(p: Float) { context.dataStore.edit { it[PreferencesKeys.PLAYBACK_PITCH] = p } }
    suspend fun updateBackupSettings(u: String?, i: BackupInterval) { context.dataStore.edit { it[PreferencesKeys.BACKUP_URI] = u ?: ""; it[PreferencesKeys.BACKUP_INTERVAL] = i.name } }
    suspend fun updateLastBackupTime(t: Long) { context.dataStore.edit { it[PreferencesKeys.LAST_BACKUP_TIME] = t } }
    suspend fun updateLyricsSettings(t: String, u: String) { context.dataStore.edit { it[PreferencesKeys.LYRICS_API_TEMPLATE] = t; it[PreferencesKeys.LYRICS_USER_AGENT] = u } }
    suspend fun updateLangRepoUrl(u: String) { context.dataStore.edit { it[PreferencesKeys.LANG_REPO_URL] = u } }
    suspend fun updateSelectedLang(c: String) { context.dataStore.edit { it[PreferencesKeys.SELECTED_LANG_CODE] = c } }

    suspend fun restoreAllData(
        favorites: Set<Long>,
        recent: List<Long>,
        counts: Map<Long, Int>,
        metadata: Map<Long, CustomMetadata>,
        theme: String?,
        sort: String?,
        minDur: Long?
    ) {
        context.dataStore.edit { p ->
            p[PreferencesKeys.FAVORITE_IDS] = favorites.map { it.toString() }.toSet()
            p[PreferencesKeys.RECENTLY_PLAYED_IDS] = recent.joinToString(",")
            p[PreferencesKeys.PLAY_COUNTS] = counts.entries.joinToString(",") { "${it.key}:${it.value}" }
            p[PreferencesKeys.CUSTOM_METADATA_MAP] = metadata.entries.joinToString("||") { "${it.key}::${it.value.serialize()}" }
            theme?.let { p[PreferencesKeys.THEME_MODE] = it }
            sort?.let { p[PreferencesKeys.SORT_ORDER] = it }
            minDur?.let { p[PreferencesKeys.MIN_DURATION] = it }
        }
    }
}
