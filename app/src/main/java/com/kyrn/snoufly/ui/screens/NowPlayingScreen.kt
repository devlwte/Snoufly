package com.kyrn.snoufly.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import com.kyrn.snoufly.data.LrcParser
import com.kyrn.snoufly.playback.PlaybackViewModel
import com.kyrn.snoufly.ui.MainViewModel
import com.kyrn.snoufly.ui.components.LyricsView
import com.kyrn.snoufly.ui.components.SnouflyImage
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlaybackViewModel,
    mainViewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleMode by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val manualLrcMap by mainViewModel.manualLrcMap.collectAsState()
    val favoriteIds by mainViewModel.favoriteIds.collectAsState()
    val allSongs by mainViewModel.songs.collectAsState()

    if (currentMediaItem == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No song playing", style = MaterialTheme.typography.headlineSmall)
        }
        return
    }

    val songId = currentMediaItem!!.mediaId.toLongOrNull() ?: -1L
    val isFavorite = favoriteIds.contains(songId)
    
    val metadata = currentMediaItem!!.mediaMetadata
    val title = metadata.title?.toString() ?: "Unknown"
    val artist = metadata.artist?.toString() ?: "Unknown Artist"
    val albumName = metadata.albumTitle?.toString() ?: ""
    val albumArtUri = metadata.artworkUri

    val lrcPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                mainViewModel.updateManualLrc(songId, it.toString())
            }
        }
    )

    var onlineLyricsContent by remember(currentMediaItem) { mutableStateOf<String?>(null) }

    val lyrics = remember(currentMediaItem, manualLrcMap, allSongs, onlineLyricsContent) {
        if (onlineLyricsContent != null) {
            LrcParser.parse(onlineLyricsContent!!)
        } else {
            val manualUriStr = manualLrcMap[songId]
            val loadedLyrics = if (!manualUriStr.isNullOrEmpty()) {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(Uri.parse(manualUriStr))
                    val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    LrcParser.parse(content)
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            } else {
                val currentSong = allSongs.find { it.id == songId }
                val autoLrcContent = currentSong?.path?.let { path ->
                    try {
                        val audioFile = File(path)
                        val lrcFile = File(audioFile.parent, audioFile.nameWithoutExtension + ".lrc")
                        if (lrcFile.exists()) lrcFile.readText() else null
                    } catch (e: Exception) { null }
                }
                if (!autoLrcContent.isNullOrEmpty()) {
                    LrcParser.parse(autoLrcContent)
                } else {
                    null
                }
            }
            loadedLyrics ?: LrcParser.parse("[00:00.00]Welcome to Snoufly\n[00:05.00]No .lrc file found\n[00:10.00]Tap 'More' to search online")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnouflyImage(
            model = albumArtUri,
            modifier = Modifier
                .fillMaxSize()
                .blur(50.dp)
                .graphicsLayer(alpha = 0.5f),
            contentScale = ContentScale.Crop
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
                Box {
                    var showMenu by remember { mutableStateOf(false) }
                    var isSearching by remember { mutableStateOf(false) }

                    IconButton(onClick = { showMenu = true }) {
                        if (isSearching) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                        else Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Search Lyrics Online") },
                            leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                isSearching = true
                                scope.launch {
                                    val result = mainViewModel.fetchOnlineLyrics(title, artist, albumName, duration)
                                    if (result.isNotEmpty()) {
                                        val sb = StringBuilder()
                                        result.forEach { sb.append("[${formatTimeLrc(it.timeStamp)}]${it.content}\n") }
                                        onlineLyricsContent = sb.toString()
                                        Toast.makeText(context, "Lyrics found!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No lyrics found online", Toast.LENGTH_SHORT).show()
                                    }
                                    isSearching = false
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Select .lrc File") },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                lrcPickerLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Art
            SnouflyImage(
                model = albumArtUri,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.DarkGray.copy(alpha = 0.2f)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.weight(1f))

            // Metadata & Controls
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                    
                    IconButton(onClick = { mainViewModel.toggleFavorite(songId) }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                var sliderPosition by remember { mutableStateOf<Float?>(null) }
                val currentSliderValue = sliderPosition ?: currentPosition.toFloat()
                
                Slider(
                    value = if (duration > 0) (currentSliderValue / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { sliderPosition = it * duration },
                    onValueChangeFinished = {
                        sliderPosition?.let { viewModel.seekTo(it.toLong()) }
                        sliderPosition = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatTime(currentSliderValue.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        formatTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (shuffleMode) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    IconButton(onClick = { viewModel.seekToPrevious() }, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Prev",
                            modifier = Modifier.size(40.dp),
                            tint = Color.White
                        )
                    }
                    FloatingActionButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(24.dp),
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.seekToNext() }, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next",
                            modifier = Modifier.size(40.dp),
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { viewModel.toggleRepeat() }) {
                        Icon(
                            imageVector = when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Botón de letras siempre visible si no está vacío
            if (lyrics.isNotEmpty()) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                var showLyrics by remember { mutableStateOf(false) }
                
                Button(
                    onClick = { showLyrics = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("LYRICS", color = Color.White, fontWeight = FontWeight.Bold)
                }

                if (showLyrics) {
                    ModalBottomSheet(
                        onDismissRequest = { showLyrics = false },
                        sheetState = sheetState,
                        containerColor = Color.Black.copy(alpha = 0.95f),
                        scrimColor = Color.Black.copy(alpha = 0.7f)
                    ) {
                        LyricsView(
                            lyrics = lyrics,
                            currentPosition = currentPosition,
                            onLyricClick = { time -> viewModel.seekTo(time) },
                            modifier = Modifier.fillMaxHeight(0.9f).padding(bottom = 32.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

private fun formatTimeLrc(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (ms % 1000) / 10
    return String.format("%02d:%02d.%02d", minutes, seconds, hundredths)
}
