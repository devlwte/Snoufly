package com.kyrn.snoufly.ui

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kyrn.snoufly.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import com.google.gson.JsonParser

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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // --- PREFERENCIAS ---
    val themeMode = settingsManager.themeModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)
    val sortOrder = settingsManager.sortOrderFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOrder.RECENTLY_ADDED)
    val minDuration = settingsManager.minDurationFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30000L)
    val favoriteIds = settingsManager.favoriteIdsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val customMetadataMap = settingsManager.customMetadataFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val playCounts = settingsManager.playCountsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val recentlyPlayedIds = settingsManager.recentlyPlayedIdsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- BACKUP & LYRICS SETTINGS ---
    val backupUri = settingsManager.backupUriFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val backupInterval = settingsManager.backupIntervalFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackupInterval.NEVER)
    val lastBackupTime = settingsManager.lastBackupTimeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val lyricsApiTemplate = settingsManager.lyricsApiTemplateFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val lyricsUserAgent = settingsManager.lyricsUserAgentFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // --- AUDIO ENGINE FLOWS ---
    val eqEnabledFlow = settingsManager.eqEnabledFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val eqBandsFlow = settingsManager.eqBandsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playbackSpeedFlow = settingsManager.playbackSpeedFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
    val playbackPitchFlow = settingsManager.playbackPitchFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

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
        "Anime" to AudioConfig(listOf(300, 100, -100, 900, 1500), 1.05f, 1.25f),
        "Droid" to AudioConfig(listOf(200, -300, 1000, -300, 800), 0.95f, 0.85f),
        "Deep Bot" to AudioConfig(listOf(800, 400, -500, -500, -800), 0.90f, 0.70f),
        "Chipmunk" to AudioConfig(listOf(-500, -200, 0, 500, 1200), 1.15f, 1.50f)
    )

    // --- CANCIONES ENRIQUECIDAS (Fuente global) ---
    val enrichedSongs: StateFlow<List<Song>> = combine(_rawSongs, customMetadataMap) { raw, customs ->
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
        val filtered = enriched.filter { it.duration >= minDur }
        val searched = if (query.isBlank()) filtered else filtered.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
        when (sort) {
            SortOrder.RECENTLY_ADDED -> searched.sortedByDescending { it.dateAdded }
            SortOrder.OLDEST_FIRST -> searched.sortedBy { it.dateAdded }
            SortOrder.ALPHABETICAL -> searched.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> searched.sortedBy { it.artist.lowercase() }
            SortOrder.ALBUM -> searched.sortedBy { it.album.lowercase() }
            SortOrder.DURATION -> searched.sortedByDescending { it.duration }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<Song>> = combine(enrichedSongs, recentlyPlayedIds) { s, ids -> 
        ids.mapNotNull { id -> s.find { it.id == id } } 
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<Song>> = combine(enrichedSongs, favoriteIds) { s, ids -> 
        s.filter { it.id in ids } 
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listenAgain: StateFlow<List<Song>> = combine(enrichedSongs, playCounts) { s, counts -> 
        counts.entries.sortedByDescending { it.value }.take(30).mapNotNull { e -> s.find { it.id == e.key } } 
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- LETRAS STATE ---
    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics = _currentLyrics.asStateFlow()
    private val _isFetchingLyrics = MutableStateFlow(false)
    val isFetchingLyrics = _isFetchingLyrics.asStateFlow()
    private var _currentLyricsSongId = -1L

    // --- ACCIONES ---
    fun loadSongs() = viewModelScope.launch { 
        _isLoading.value = true
        try { _rawSongs.value = repository.getAllSongs() } catch (_: Exception) {} finally { _isLoading.value = false }
    }
    fun updateSongMetadata(id: Long, t: String, a: String, al: String, c: String?) = viewModelScope.launch {
        val cur = customMetadataMap.value[id] ?: CustomMetadata()
        settingsManager.updateCustomMetadata(id, cur.copy(title = t, artist = a, album = al, coverUri = c ?: cur.coverUri))
        _currentLyricsSongId = -1L 
        loadLyricsForSong(id, t, a, al, 0)
    }
    fun updateManualLrc(id: Long, uri: String) = viewModelScope.launch {
        val cur = customMetadataMap.value[id] ?: CustomMetadata()
        settingsManager.updateCustomMetadata(id, cur.copy(lrcUri = uri))
        _currentLyricsSongId = -1L 
        loadLyricsForSong(id, null, null, null, 0)
    }
    fun toggleFavorite(id: Long) = viewModelScope.launch { settingsManager.toggleFavorite(id) }
    fun addToRecentlyPlayed(id: Long) = viewModelScope.launch {
        val list = recentlyPlayedIds.value.toMutableList().apply { remove(id); add(0, id) }
        settingsManager.updateRecentlyPlayed(if (list.size > 30) list.take(30) else list)
        settingsManager.incrementPlayCount(id)
    }
    fun updateSearchQuery(q: String) { _searchQuery.value = q }
    fun updateThemeMode(m: ThemeMode) = viewModelScope.launch { settingsManager.updateThemeMode(m) }
    fun updateSortOrder(o: SortOrder) = viewModelScope.launch { settingsManager.updateSortOrder(o) }
    fun updateMinDuration(d: Long) = viewModelScope.launch { settingsManager.updateMinDuration(d) }

    // --- AUDIO & ENGINE ---
    fun applyPreset(name: String) {
        val cfg = audioPresets[name] ?: return
        viewModelScope.launch {
            settingsManager.updateEqEnabled(true)
            settingsManager.updateEqBands(cfg.bands)
            settingsManager.updatePlaybackSpeed(cfg.speed)
            settingsManager.updatePlaybackPitch(cfg.pitch)
        }
    }
    fun updateEqEnabled(e: Boolean) = viewModelScope.launch { settingsManager.updateEqEnabled(e) }
    fun updateEqBands(b: List<Int>) = viewModelScope.launch { settingsManager.updateEqBands(b) }
    fun updatePlaybackSpeed(s: Float) = viewModelScope.launch { settingsManager.updatePlaybackSpeed(s) }
    fun updatePlaybackPitch(p: Float) = viewModelScope.launch { settingsManager.updatePlaybackPitch(p) }
    fun updateLyricsSettings(t: String, u: String) = viewModelScope.launch { settingsManager.updateLyricsSettings(t, u) }

    // --- BACKUP ---
    fun exportBackup(uri: Uri, onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(backupManager.createBackup(uri).isSuccess) }
    fun importBackup(uri: Uri, onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(backupManager.restoreBackup(uri).isSuccess) }
    fun updateBackupSettings(u: String?, i: BackupInterval) = viewModelScope.launch { settingsManager.updateBackupSettings(u, i) }

    // --- MOTOR DE LETRAS ---
    fun loadLyricsForSong(id: Long, title: String?, artist: String?, album: String?, duration: Long) {
        if (_currentLyricsSongId == id && _currentLyrics.value.isNotEmpty()) return
        viewModelScope.launch {
            _currentLyrics.value = emptyList() 
            _isFetchingLyrics.value = true
            _currentLyricsSongId = id
            val file = File(getApplication<Application>().filesDir, "lyrics/$id.lrc")
            if (file.exists()) {
                _currentLyrics.value = LrcParser.parse(file.readText())
            } else if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
                val lrc = fetchOnlineLyrics(title, artist, album ?: "", duration)
                if (lrc != null) {
                    _currentLyrics.value = LrcParser.parse(lrc)
                    withContext(Dispatchers.IO) { try { val d = File(getApplication<Application>().filesDir, "lyrics"); if (!d.exists()) d.mkdirs(); File(d, "$id.lrc").writeText(lrc) } catch (_: Exception) {} }
                }
            }
            _isFetchingLyrics.value = false
        }
    }

    private suspend fun fetchOnlineLyrics(t: String, a: String, al: String, d: Long): String? = withContext(Dispatchers.IO) {
        val ct = t.replace(Regex("\\(.*?\\)"), "").trim()
        val ca = a.replace(Regex("\\(.*?\\)"), "").trim()
        val url = "https://lrclib.net/api/search?track_name=${Uri.encode(ct)}&artist_name=${Uri.encode(ca)}"
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", lyricsUserAgent.value)
            if (conn.responseCode == 200) {
                val res = JsonParser.parseString(conn.inputStream.bufferedReader().use { it.readText() }).asJsonArray
                if (res.size() > 0) {
                    for (el in res) { val obj = el.asJsonObject; if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull) return@withContext obj.get("syncedLyrics").asString }
                    return@withContext res.get(0).asJsonObject.get("plainLyrics")?.let { if (it.isJsonNull) null else it.asString }
                }
            }
        } catch (_: Exception) {}
        null
    }
}
