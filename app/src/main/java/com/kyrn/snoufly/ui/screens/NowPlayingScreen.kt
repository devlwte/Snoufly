package com.kyrn.snoufly.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.kyrn.snoufly.playback.PlaybackViewModel
import com.kyrn.snoufly.ui.MainViewModel
import com.kyrn.snoufly.ui.components.LyricsView
import com.kyrn.snoufly.ui.components.SnouflyImage
import com.kyrn.snoufly.ui.components.EditSongDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlaybackViewModel,
    mainViewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleMode by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val favoriteIds by mainViewModel.favoriteIds.collectAsState()
    
    val lyrics by mainViewModel.currentLyrics.collectAsState()
    val isFetchingLyrics by mainViewModel.isFetchingLyrics.collectAsState()
    val enrichedSongs by mainViewModel.enrichedSongs.collectAsState()

    var showLyricsOverlay by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Observar traducciones
    val translations by mainViewModel.translations.collectAsState()
    fun t(key: String, fallback: String) = mainViewModel.t("player", key, fallback)
    // fun t(key: String, fallback: String) = mainViewModel.t("player", key, fallback)

    if (currentMediaItem == null) return

    val songId = currentMediaItem!!.mediaId.toLongOrNull() ?: -1L
    val currentSong = enrichedSongs.find { it.id == songId } ?: return
    
    val isFavorite = favoriteIds.contains(songId)
    val title = currentSong.title
    val artist = currentSong.artist
    val albumArtUri = currentSong.albumArtUri

    LaunchedEffect(currentSong) {
        mainViewModel.loadLyricsForSong(songId, title, artist, currentSong.album, duration)
    }

    val lrcPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
                mainViewModel.updateManualLrc(songId, it.toString())
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        SnouflyImage(
            model = albumArtUri,
            modifier = Modifier.fillMaxSize().blur(60.dp).graphicsLayer(alpha = 0.5f),
            contentScale = ContentScale.Crop
        )
        
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f)))
        ))

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Text(t("title","NOW PLAYING"), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), letterSpacing = 1.sp)
                Box {
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = Color.White)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(t("menu_edit", "Edit Song Info")) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { showMenu = false; showEditDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text(t("menu_resync","Resync Online")) },
                            leadingIcon = { Icon(Icons.Default.CloudDownload, null) },
                            onClick = { showMenu = false; mainViewModel.loadLyricsForSong(songId, title, artist, currentSong.album, duration) }
                        )
                        DropdownMenuItem(
                            text = { Text(t("menu_choose_lrc", "Choose .lrc file")) },
                            leadingIcon = { Icon(Icons.Default.Description, null) },
                            onClick = { showMenu = false; lrcPickerLauncher.launch(arrayOf("*/*")) }
                        )
                    }
                }
            }

            // Central Area (Lyrics vs Art)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !showLyricsOverlay,
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                        Spacer(Modifier.weight(0.2f))
                        SnouflyImage(
                            model = albumArtUri,
                            modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(1f).clip(RoundedCornerShape(28.dp))
                                .background(Color.White.copy(alpha = 0.05f)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(48.dp))
                        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                        Text(artist, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                        Spacer(Modifier.weight(0.3f))
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showLyricsOverlay,
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    LyricsView(
                        lyrics = lyrics,
                        currentPosition = currentPosition,
                        isFetching = isFetchingLyrics,
                        onLyricClick = { viewModel.seekTo(it) },
                        modifier = Modifier.fillMaxSize(),
                        translate = { key, fallback ->
                            mainViewModel.t("player", key, fallback)
                        }
                    )
                }
            }

            // Bottom Area
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (showLyricsOverlay) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                            Text(artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Row {
                        IconButton(onClick = { mainViewModel.toggleFavorite(songId) }) {
                            Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White)
                        }
                        IconButton(onClick = { showLyricsOverlay = !showLyricsOverlay }) {
                            Icon(Icons.AutoMirrored.Filled.List, null, tint = if (showLyricsOverlay) MaterialTheme.colorScheme.primary else Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Slider(
                    value = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { viewModel.seekTo((it * duration).toLong()) },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.2f))
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(currentPosition), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                    Text(formatTime(duration), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(Icons.Default.Shuffle, null, tint = if (shuffleMode) MaterialTheme.colorScheme.primary else Color.White)
                    }
                    IconButton(onClick = { viewModel.seekToPrevious() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, modifier = Modifier.size(36.dp), tint = Color.White)
                    }
                    Surface(
                        onClick = { viewModel.togglePlayPause() },
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(40.dp), tint = Color.Black)
                        }
                    }
                    IconButton(onClick = { viewModel.seekToNext() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(36.dp), tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.toggleRepeat() }) {
                        Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, null, tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showEditDialog) {
        EditSongDialog(
            song = currentSong,
            onDismiss = { showEditDialog = false },
            onConfirm = { t, a, al ->
                mainViewModel.updateSongMetadata(songId, t, a, al, null)
                showEditDialog = false
            }
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
