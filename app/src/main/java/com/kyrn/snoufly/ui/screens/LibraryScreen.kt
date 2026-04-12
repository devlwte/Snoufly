package com.kyrn.snoufly.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyrn.snoufly.data.Song
import com.kyrn.snoufly.ui.MainViewModel
import com.kyrn.snoufly.ui.SortOrder
import com.kyrn.snoufly.ui.components.RecentlyPlayedCarousel
import com.kyrn.snoufly.ui.components.SongItem
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onSongClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onSeeAllListenAgain: () -> Unit,
    onEditSong: (Song) -> Unit
) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val listenAgain by viewModel.listenAgain.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentSortOrder by viewModel.sortOrder.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var selectingLrcForSongId by remember { mutableStateOf<Long?>(null) }

    val lrcPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) { e.printStackTrace() }
                
                selectingLrcForSongId?.let { songId ->
                    viewModel.updateManualLrc(songId, it.toString())
                }
            }
            selectingLrcForSongId = null
        }
    )

    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) { 
                AnimatedContent(
                    targetState = isSearchActive,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "TopBarAnimation"
                ) { searchActive ->
                    if (searchActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { 
                                isSearchActive = false
                                viewModel.updateSearchQuery("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Search songs, artists...") },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(32.dp).clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Icon(Icons.Default.MusicNote, null, Modifier.padding(6.dp), MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Snoufly",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row {
                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                }
                                IconButton(onClick = onSettingsClick) {
                                    Icon(Icons.Default.AccountCircle, "Settings", Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sortOrders = SortOrder.values()
                    items(sortOrders) { order ->
                        val displayName = order.name.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() }
                        
                        FilterChip(
                            selected = currentSortOrder == order,
                            onClick = { viewModel.updateSortOrder(order) },
                            label = { Text(displayName, fontSize = 12.sp) },
                            border = null
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (isLoading && songs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (searchQuery.isEmpty()) {
                        if (listenAgain.isNotEmpty()) {
                            item {
                                RecentlyPlayedCarousel(
                                    title = "Listen Again",
                                    songs = listenAgain.take(10),
                                    onSongClick = { song ->
                                        val index = songs.indexOf(song)
                                        if (index != -1) onSongClick(index)
                                    },
                                    onHeaderClick = if (listenAgain.size > 10) onSeeAllListenAgain else null,
                                    showSeeAllButton = listenAgain.size > 10,
                                    onSeeAllClick = onSeeAllListenAgain,
                                    itemWidth = 140.dp
                                )
                            }
                        }

                        if (recentlyPlayed.isNotEmpty()) {
                            item {
                                RecentlyPlayedCarousel(
                                    title = "Recently Played",
                                    songs = recentlyPlayed,
                                    onSongClick = { song -> 
                                        val index = songs.indexOf(song)
                                        if (index != -1) onSongClick(index)
                                    },
                                    itemWidth = 110.dp
                                )
                            }
                        }
                        
                        if (recentlyPlayed.isNotEmpty() || listenAgain.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Your Library",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    items(
                        items = songs,
                        key = { it.id }
                    ) { song ->
                        SongItem(
                            song = song,
                            isFavorite = favoriteIds.contains(song.id),
                            onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                            onClick = { 
                                val index = songs.indexOf(song)
                                onSongClick(index)
                            },
                            onEditClick = { onEditSong(song) },
                            onSelectLrcClick = {
                                selectingLrcForSongId = song.id
                                lrcPickerLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }
            }
        }
    }
}
