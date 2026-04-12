package com.kyrn.snoufly.playback

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.kyrn.snoufly.data.Song
import com.kyrn.snoufly.ui.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackViewModel : ViewModel() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private var progressJob: Job? = null
    private var mainViewModel: MainViewModel? = null

    fun initController(context: Context, vm: MainViewModel? = null) {
        if (vm != null) this.mainViewModel = vm
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            setupPlayer()
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayer() {
        val player = controller ?: return
        _isPlaying.value = player.isPlaying
        _currentMediaItem.value = enrichMediaItem(player.currentMediaItem)
        _duration.value = player.duration.coerceAtLeast(0L)
        _shuffleModeEnabled.value = player.shuffleModeEnabled
        _repeatMode.value = player.repeatMode
        
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentMediaItem.value = enrichMediaItem(mediaItem)
                _duration.value = player.duration.coerceAtLeast(0L)
                mediaItem?.mediaId?.toLongOrNull()?.let { id ->
                    mainViewModel?.addToRecentlyPlayed(id)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                _currentPosition.value = new.positionMs
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) { _shuffleModeEnabled.value = enabled }
            override fun onRepeatModeChanged(mode: Int) { _repeatMode.value = mode }
        })

        if (player.isPlaying) startProgressUpdate()
    }

    private fun enrichMediaItem(item: MediaItem?): MediaItem? {
        if (item == null) return null
        val id = item.mediaId.toLongOrNull()
        val custom = mainViewModel?.customMetadataMap?.value?.get(id)
        return if (custom != null) {
            item.buildUpon()
                .setMediaMetadata(
                    item.mediaMetadata.buildUpon()
                        .setTitle(custom.title ?: item.mediaMetadata.title)
                        .setArtist(custom.artist ?: item.mediaMetadata.artist)
                        .setAlbumTitle(custom.album ?: item.mediaMetadata.albumTitle)
                        .build()
                ).build()
        } else item
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                controller?.let {
                    _currentPosition.value = it.currentPosition
                    if (_duration.value <= 0L && it.duration > 0L) _duration.value = it.duration
                }
                delay(500)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }

    fun playSongs(songs: List<Song>, startIndex: Int) {
        val player = controller ?: return
        val mediaItems = songs.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.albumArtUri)
                        .build()
                )
                .build()
        }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun toggleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
    fun seekToNext() = controller?.seekToNext()
    fun seekToPrevious() = controller?.seekToPrevious()
    fun seekTo(position: Long) = controller?.seekTo(position)

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdate()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
