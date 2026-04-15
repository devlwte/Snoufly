package com.kyrn.snoufly.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyrn.snoufly.data.Song
import com.kyrn.snoufly.ui.MainViewModel
import com.kyrn.snoufly.ui.components.SongItem
import com.kyrn.snoufly.utils.t

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: MainViewModel,
    onSongClick: (Int) -> Unit,
    onEditSong: (Song) -> Unit
) {
    val favorites by viewModel.favorites.collectAsState()

    // Atajo local para traducciones de favoritos
    @Composable
    fun tf(key: String, fallback: String) = t(key, fallback, "favorites")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tf("title", "Favorites"), fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tf("empty", "No favorite songs yet"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(favorites) { song ->
                    SongItem(
                        song = song,
                        isFavorite = true,
                        onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                        onClick = { 
                            val index = favorites.indexOf(song)
                            onSongClick(index)
                        },
                        onEditClick = { onEditSong(song) },
                        onSelectLrcClick = { /* Manual LRC */ }
                    )
                }
            }
        }
    }
}
