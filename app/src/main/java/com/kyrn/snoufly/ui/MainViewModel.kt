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

    private val _recentlyPlayedIds = MutableStateFlow<List<Long>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = combine(_rawSongs, _recentlyPlayedIds) { raw, ids ->
        ids.mapNotNull { id -> raw.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIds: StateFlow<Set<Long>> = settingsManager.favoriteIdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val customMetadataMap: StateFlow<Map<Long, CustomMetadata>> = settingsManager.customMetadataFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val manualLrcMap: StateFlow<Map<Long, String>> = customMetadataMap
        .map { map -> 
            map.entries.mapNotNull { entry ->
                entry.value.lrcUri?.let { entry.key to it }
            }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val eqEnabledFlow: Flow<Boolean> = settingsManager.eqEnabledFlow
    val eqBandsFlow: Flow<List<Int>> = settingsManager.eqBandsFlow
    val playbackSpeedFlow: Flow<Float> = settingsManager.playbackSpeedFlow
    val playbackPitchFlowFixed: Flow<Float> = settingsManager.playbackPitchFlow

    val favorites: StateFlow<List<Song>> = combine(_rawSongs, favoriteIds) { raw, ids ->
        raw.filter { it.id in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val songs: StateFlow<List<Song>> = combine(_rawSongs, _searchQuery, sortOrder, minDuration, customMetadataMap) { raw, query, sort, minDur, customs ->
        val enriched = raw.map { song ->
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

        val filteredByDuration = enriched.filter { it.duration >= minDur }
        
        val filteredBySearch = if (query.isBlank()) {
            filteredByDuration
        } else {
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

    fun loadSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _rawSongs.value = repository.getAllSongs()
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addToRecentlyPlayed(songId: Long) {
        val currentList = _recentlyPlayedIds.value.toMutableList()
        currentList.remove(songId)
        currentList.add(0, songId)
        if (currentList.size > 15) {
            _recentlyPlayedIds.value = currentList.take(15)
        } else {
            _recentlyPlayedIds.value = currentList
        }
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            settingsManager.toggleFavorite(songId)
        }
    }

    fun isFavorite(songId: Long): Boolean = favoriteIds.value.contains(songId)

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsManager.updateThemeMode(mode)
        }
    }

    fun updateSortOrder(order: SortOrder) {
        viewModelScope.launch {
            settingsManager.updateSortOrder(order)
        }
    }

    fun updateMinDuration(durationMs: Long) {
        viewModelScope.launch {
            settingsManager.updateMinDuration(durationMs)
        }
    }

    fun updateManualLrc(songId: Long, lrcUri: String) {
        viewModelScope.launch {
            val currentCustom = customMetadataMap.value[songId] ?: CustomMetadata()
            settingsManager.updateCustomMetadata(songId, currentCustom.copy(lrcUri = lrcUri))
        }
    }

    fun updateSongMetadata(songId: Long, newTitle: String, newArtist: String, newAlbum: String, newCoverUri: String?) {
        viewModelScope.launch {
            val currentCustom = customMetadataMap.value[songId] ?: CustomMetadata()
            settingsManager.updateCustomMetadata(songId, currentCustom.copy(
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                coverUri = newCoverUri ?: currentCustom.coverUri
            ))
        }
    }

    fun updateEqEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateEqEnabled(enabled)
        }
    }

    fun updateEqBands(bands: List<Int>) {
        viewModelScope.launch {
            settingsManager.updateEqBands(bands)
        }
    }

    fun updatePlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            settingsManager.updatePlaybackSpeed(speed)
        }
    }

    fun updatePlaybackPitch(pitch: Float) {
        viewModelScope.launch {
            settingsManager.updatePlaybackPitch(pitch)
        }
    }

    fun exportSettings(): String {
        // Implement full export logic here
        return "SNFLY_BACKUP_V1..."
    }
}
