package com.kyrn.snoufly.ui

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kyrn.snoufly.data.BackupInterval
import com.kyrn.snoufly.data.BackupManager
import com.kyrn.snoufly.data.BackupWorker
import com.kyrn.snoufly.data.CustomMetadata
import com.kyrn.snoufly.data.MusicRepository
import com.kyrn.snoufly.data.SettingsManager
import com.kyrn.snoufly.data.Song
import com.kyrn.snoufly.data.ThemeMode
import com.kyrn.snoufly.data.LrcParser
import com.kyrn.snoufly.data.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import com.google.gson.JsonParser
import com.google.gson.JsonArray

enum class SortOrder {
    RECENTLY_ADDED, OLDEST_FIRST, ALPHABETICAL, ARTIST, ALBUM, DURATION
}

class MainViewModel(
    application: Application,
    private val repository: MusicRepository,
    private val settingsManager: SettingsManager,
    private val backupManager: BackupManager
) : AndroidViewModel(application) {

    private val _rawSongs = MutableStateFlow<List<Song>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = settingsManager.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val sortOrder: StateFlow<SortOrder> = settingsManager.sortOrderFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOrder.RECENTLY_ADDED)

    val minDuration: StateFlow<Long> = settingsManager.minDurationFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30000L)

    val favoriteIds: StateFlow<Set<Long>> = settingsManager.favoriteIdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val customMetadataMap: StateFlow<Map<Long, CustomMetadata>> = settingsManager.customMetadataFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val playCounts: StateFlow<Map<Long, Int>> = settingsManager.playCountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val recentlyPlayedIds: StateFlow<List<Long>> = settingsManager.recentlyPlayedIdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backupUri: StateFlow<String?> = settingsManager.backupUriFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val backupInterval: StateFlow<BackupInterval> = settingsManager.backupIntervalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackupInterval.NEVER)

    val lastBackupTime: StateFlow<Long> = settingsManager.lastBackupTimeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val lyricsApiTemplate: StateFlow<String> = settingsManager.lyricsApiTemplateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val lyricsUserAgent: StateFlow<String> = settingsManager.lyricsUserAgentFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // SINGLE SOURCE OF TRUTH
    private val enrichedSongs: StateFlow<List<Song>> = combine(_rawSongs, customMetadataMap) { raw, customs ->
        raw.map { song ->
            val custom = customs[song.id]
            if (custom != null) {
                song.copy(
                    title = if (!custom.title.isNullOrBlank()) custom.title else song.title,
                    artist = if (!custom.artist.isNullOrBlank()) custom.artist else song.artist,
                    album = if (!custom.album.isNullOrBlank()) custom.album else song.album,
                    albumArtUri = custom.coverUri?.toUri() ?: song.albumArtUri
                )
            } else song
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val songs: StateFlow<List<Song>> = combine(enrichedSongs, _searchQuery, sortOrder, minDuration) { enriched, query, sort, minDur ->
        val filteredByDuration = enriched.filter { it.duration >= minDur }
        val filteredBySearch = if (query.isBlank()) filteredByDuration else {
            filteredByDuration.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
        when (sort) {
            SortOrder.RECENTLY_ADDED -> filteredBySearch.sortedByDescending { it.dateAdded }
            SortOrder.OLDEST_FIRST -> filteredBySearch.sortedBy { it.dateAdded }
            SortOrder.ALPHABETICAL -> filteredBySearch.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> filteredBySearch.sortedBy { it.artist.lowercase() }
            SortOrder.ALBUM -> filteredBySearch.sortedBy { it.album.lowercase() }
            SortOrder.DURATION -> filteredBySearch.sortedByDescending { it.duration }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<Song>> = combine(enrichedSongs, recentlyPlayedIds) { enriched, ids ->
        ids.mapNotNull { id -> enriched.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listenAgain: StateFlow<List<Song>> = combine(enrichedSongs, playCounts) { enriched, counts ->
        counts.entries.sortedByDescending { it.value }.take(35).mapNotNull { entry -> enriched.find { it.id == entry.key } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<Song>> = combine(enrichedSongs, favoriteIds) { enriched, ids ->
        enriched.filter { it.id in ids }.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val manualLrcMap: StateFlow<Map<Long, String>> = customMetadataMap
        .map { map -> map.entries.mapNotNull { entry -> entry.value.lrcUri?.let { entry.key to it } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // LÓGICA DE AUDIO HI-FI PRO
    val eqEnabledFlow: StateFlow<Boolean> = settingsManager.eqEnabledFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val eqBandsFlow: StateFlow<List<Int>> = settingsManager.eqBandsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playbackSpeedFlow: StateFlow<Float> = settingsManager.playbackSpeedFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
    val playbackPitchFlow: StateFlow<Float> = settingsManager.playbackPitchFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    // Categorized Presets
    val audioCategories = mapOf(
        "Standard" to listOf("Normal", "Balanced", "Loudness"),
        "Music Genre" to listOf("Rock", "Pop", "Jazz", "Classical", "Electronic"),
        "Vocal & Arts" to listOf("Clear Voice", "Podcast"),
        "Pro Effects" to listOf("Bass Max", "Treble Boost", "Cinematic"),
        "Character FX" to listOf("Anime", "Droid", "Deep Bot", "Chipmunk")
    )

    data class AudioConfig(val bands: List<Int>, val speed: Float = 1.0f, val pitch: Float = 1.0f)

    val audioPresets = mapOf(
        "Normal" to AudioConfig(listOf(0, 0, 0, 0, 0)),
        "Balanced" to AudioConfig(listOf(200, 100, 0, 100, 200)),
        "Loudness" to AudioConfig(listOf(400, 0, -200, 0, 400)),
        "Rock" to AudioConfig(listOf(600, 400, -200, 300, 600)),
        "Pop" to AudioConfig(listOf(-200, 300, 600, 200, -300)),
        "Jazz" to AudioConfig(listOf(500, 200, -100, 400, 200)),
        "Classical" to AudioConfig(listOf(400, 300, 0, 300, 500)),
        "Electronic" to AudioConfig(listOf(700, 300, 0, 400, 700)),
        "Clear Voice" to AudioConfig(listOf(-400, -200, 800, 400, -200)),
        "Podcast" to AudioConfig(listOf(-500, 0, 1000, 200, -500)),
        "Bass Max" to AudioConfig(listOf(1200, 800, 0, 0, 0)),
        "Treble Boost" to AudioConfig(listOf(0, 0, 0, 600, 1200)),
        "Cinematic" to AudioConfig(listOf(600, 200, -200, 200, 800)),
        
        // Character FX Presets
        "Anime" to AudioConfig(listOf(300, 100, -100, 900, 1500), speed = 1.05f, pitch = 1.25f), 
        "Droid" to AudioConfig(listOf(200, -300, 1000, -300, 800), speed = 0.95f, pitch = 0.85f),
        "Deep Bot" to AudioConfig(listOf(800, 400, -500, -500, -800), speed = 0.90f, pitch = 0.70f),
        "Chipmunk" to AudioConfig(listOf(-500, -200, 0, 500, 1200), speed = 1.15f, pitch = 1.50f)
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            try { _rawSongs.value = repository.getAllSongs() } catch (_: Exception) { } finally { _isLoading.value = false }
        }
    }

    fun addToRecentlyPlayed(songId: Long) {
        viewModelScope.launch {
            val currentList = recentlyPlayedIds.value.toMutableList()
            currentList.remove(songId)
            currentList.add(0, songId)
            settingsManager.updateRecentlyPlayed(if (currentList.size > 30) currentList.take(30) else currentList)
            settingsManager.incrementPlayCount(songId)
        }
    }

    fun toggleFavorite(songId: Long) = viewModelScope.launch { settingsManager.toggleFavorite(songId) }
    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun updateThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsManager.updateThemeMode(mode) }
    fun updateSortOrder(order: SortOrder) = viewModelScope.launch { settingsManager.updateSortOrder(order) }
    fun updateMinDuration(durationMs: Long) = viewModelScope.launch { settingsManager.updateMinDuration(durationMs) }
    fun updateManualLrc(songId: Long, lrcUri: String) = viewModelScope.launch {
        val currentCustom = customMetadataMap.value[songId] ?: CustomMetadata()
        settingsManager.updateCustomMetadata(songId, currentCustom.copy(lrcUri = lrcUri))
    }
    fun updateSongMetadata(songId: Long, title: String, artist: String, album: String, coverUri: String?) = viewModelScope.launch {
        val currentCustom = customMetadataMap.value[songId] ?: CustomMetadata()
        settingsManager.updateCustomMetadata(songId, currentCustom.copy(
            title = title, 
            artist = artist, 
            album = album, 
            coverUri = coverUri ?: currentCustom.coverUri
        ))
    }

    fun exportBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.createBackup(uri)
            onResult(result.isSuccess)
        }
    }

    fun importBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.restoreBackup(uri)
            onResult(result.isSuccess)
        }
    }

    fun updateBackupSettings(uri: String?, interval: BackupInterval) {
        viewModelScope.launch {
            settingsManager.updateBackupSettings(uri, interval)
            scheduleBackup(interval)
        }
    }

    private fun scheduleBackup(interval: BackupInterval) {
        val workManager = WorkManager.getInstance(getApplication())
        if (interval == BackupInterval.NEVER) {
            workManager.cancelUniqueWork("auto_backup")
            return
        }

        val repeatIntervalDays = when (interval) {
            BackupInterval.DAILY -> 1L
            BackupInterval.WEEKLY -> 7L
            BackupInterval.MONTHLY -> 30L
            BackupInterval.NEVER -> return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()

        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(repeatIntervalDays, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "auto_backup",
            ExistingPeriodicWorkPolicy.REPLACE,
            backupRequest
        )
    }

    fun applyPreset(presetName: String) {
        val config = audioPresets[presetName] ?: return
        viewModelScope.launch {
            settingsManager.updateEqEnabled(true)
            settingsManager.updateEqBands(config.bands)
            settingsManager.updatePlaybackSpeed(config.speed)
            settingsManager.updatePlaybackPitch(config.pitch)
        }
    }

    fun updateEqEnabled(enabled: Boolean) = viewModelScope.launch { settingsManager.updateEqEnabled(enabled) }
    fun updateEqBands(bands: List<Int>) = viewModelScope.launch { settingsManager.updateEqBands(bands) }
    fun updatePlaybackSpeed(speed: Float) = viewModelScope.launch { settingsManager.updatePlaybackSpeed(speed) }
    fun updatePlaybackPitch(pitch: Float) = viewModelScope.launch { settingsManager.updatePlaybackPitch(pitch) }

    // --- LYRICS API LOGIC ---
    fun updateLyricsSettings(template: String, userAgent: String) = viewModelScope.launch {
        settingsManager.updateLyricsSettings(template, userAgent)
    }

    private fun cleanMetadata(text: String): String {
        return text.replace(Regex("\\(.*?\\)"), "") 
                   .replace(Regex("\\[.*?]"), "")   
                   .replace(Regex("(?i)official video|official audio|video|lyrics|ft\\.|feat\\.|remastered|remix|\\d{4}"), "") 
                   .trim()
    }

    suspend fun fetchOnlineLyrics(track: String, artist: String, album: String, duration: Long): List<LyricLine> = withContext(Dispatchers.IO) {
        val cleanTrack = cleanMetadata(track)
        val cleanArtist = cleanMetadata(artist)
        
        // --- NUEVA ESTRATEGIA: BÚSQUEDA POR TÍTULO Y ARTISTA (MÁS ROBUSTA) ---
        // LRCLIB /api/search?q=artist+track
        val searchQuery = Uri.encode("$cleanArtist $cleanTrack")
        val searchUrl = "https://lrclib.net/api/search?q=$searchQuery"
        
        try {
            val url = URL(searchUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", lyricsUserAgent.value)
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val results = JsonParser.parseString(response).asJsonArray
                
                if (results.size() > 0) {
                    // Seleccionar el mejor resultado: buscamos el que tenga syncedLyrics, sino plainLyrics
                    var bestMatch = results.get(0).asJsonObject
                    for (element in results) {
                        val obj = element.asJsonObject
                        if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull) {
                            bestMatch = obj
                            break
                        }
                    }
                    
                    val lrcContent = bestMatch.get("syncedLyrics")?.let { if (it.isJsonNull) null else it.asString }
                        ?: bestMatch.get("plainLyrics")?.let { if (it.isJsonNull) null else it.asString }
                        ?: ""
                    
                    return@withContext LrcParser.parse(lrcContent)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // --- FALLBACK: EL MÉTODO GET ORIGINAL SI LA BÚSQUEDA FALLA ---
        val lyrics = performLyricsRequest(mapOf("TRACK" to cleanTrack, "ARTIST" to cleanArtist, "ALBUM" to "", "DURATION" to ""))
        if (lyrics.isNotEmpty()) return@withContext lyrics
        
        emptyList()
    }

    private suspend fun performLyricsRequest(params: Map<String, String>): List<LyricLine> {
        return try {
            val template = settingsManager.lyricsApiTemplateFlow.first()
            val userAgent = settingsManager.lyricsUserAgentFlow.first()
            
            var urlString = template
            params.forEach { (key, value) ->
                if (value.isEmpty() || value.lowercase() == "unknown") {
                    urlString = urlString.replace(Regex("[&?]?[^&?]+?=%$key%"), "")
                } else {
                    urlString = urlString.replace("%$key%", Uri.encode(value))
                }
            }

            urlString = urlString.replace("&&", "&").replace("?&", "?").trimEnd('&', '?')

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", userAgent)
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JsonParser.parseString(response).asJsonObject
                
                val lrcContent = jsonObject.get("syncedLyrics")?.let { if (it.isJsonNull) null else it.asString }
                    ?: jsonObject.get("plainLyrics")?.let { if (it.isJsonNull) null else it.asString }
                    ?: ""

                LrcParser.parse(lrcContent)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
