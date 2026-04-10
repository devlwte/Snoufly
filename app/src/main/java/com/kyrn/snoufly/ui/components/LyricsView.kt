package com.kyrn.snoufly.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyrn.snoufly.data.LyricLine
import kotlinx.coroutines.launch

@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    currentPosition: Long,
    onLyricClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    val currentIndex = lyrics.indexOfLast { it.timeStamp <= currentPosition }.coerceAtLeast(0)

    LaunchedEffect(currentIndex) {
        if (lyrics.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(currentIndex, scrollOffset = -200)
            }
        }
    }

    if (lyrics.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No lyrics available",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 200.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lyrics) { index, lyric ->
                val isCurrent = index == currentIndex
                val color by animateColorAsState(
                    targetValue = if (isCurrent) Color.White else Color.White.copy(alpha = 0.4f),
                    label = "LyricColor"
                )
                val fontSize by animateFloatAsState(
                    targetValue = if (isCurrent) 24f else 18f,
                    label = "LyricFontSize"
                )

                Text(
                    text = lyric.content,
                    color = color,
                    fontSize = fontSize.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLyricClick(lyric.timeStamp) }
                        .padding(vertical = 12.dp, horizontal = 24.dp)
                )
            }
        }
    }
}
