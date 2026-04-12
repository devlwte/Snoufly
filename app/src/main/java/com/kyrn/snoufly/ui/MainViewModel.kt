package com.kyrn.snoufly.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyrn.snoufly.data.CustomMetadata
import com.kyrn.snoufly.data.MusicRepository
import com.kyrn.snoufly.data.SettingsManager
import com.kyrn.snoufly.data.Song
import com.kyrn.snoufly.data.ThemeMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder {
    RECENTLY_ADDED, OLDEST_FIRST, ALPHABETICAL, ARTIST, ALBUM, DURATION
}

class MainViewModel(
    private val repository: MusicRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

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

    // SINGLE SOURCE OF TRUTH
    private val enrichedSongs: StateFlow<List<Song>> = combine(_rawSongs, customMetadataMap) { raw, customs ->
        raw.map { song ->
            val custom = customs[song.id]
            if (custom != null) {
                song.copy(
                    title = custom.title ?: song.title,
                    artist = custom.artist ?: song.artist,
                    album = custom.album ?: song.album,
                    albumArtUri = custom.coverUri?.let { android.net.Uri.parse(it) } ?: song.albumArtUri
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
        enriched.filter { it.id in ids }
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
            try { _rawSongs.value = repository.getAllSongs() } catch (e: Exception) { } finally { _isLoading.value = false }
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
    fun isFavorite(songId: Long): Boolean = favoriteIds.value.contains(songId)
    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun updateThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsManager.updateThemeMode(mode) }
    fun updateSortOrder(order: SortOrder) = viewModelScope.launch { settingsManager.updateSortOrder(order) }
    fun updateMinDuration(durationMs: Long) = viewModelScope.launch { settingsManager.updateMinDuration(durationMs) }
    fun updateManualLrc(songId: Long, lrcUri: String) = viewModelScope.launch {
        val currentCustom = customMetadataMap.value[songId] ?: CustomMetadata()
        settingsManager.updateCustomMetadata(songId, currentCustom.copy(lrcUri = lrcUri))
    }
    fun updateSongMetadata(songId: Long, t: String, a: String, al: String, c: String?) = viewModelScope.launch {
        val currentCustom = customMetadataMap.value[songId] ?: CustomMetadata()
        settingsManager.updateCustomMetadata(songId, currentCustom.copy(title = t, artist = a, album = al, coverUri = c ?: currentCustom.coverUri))
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
}
