package com.kyrn.snoufly.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyrn.snoufly.data.LyricLine
import com.kyrn.snoufly.ui.MainViewModel
import kotlinx.coroutines.launch
import com.kyrn.snoufly.utils.t

@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    currentPosition: Long,
    isFetching: Boolean,
    onLyricClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    val currentIndex = lyrics.indexOfLast { it.timeStamp <= currentPosition }.coerceAtLeast(0)

    // REINICIO DE SCROLL: Cuando cambian las letras (nueva canción), volvemos arriba instantáneamente
    LaunchedEffect(lyrics) {
        if (lyrics.isEmpty()) {
            listState.scrollToItem(0)
        }
    }

    val topFocusOffsetPx = with(density) { 60.dp.toPx() }

    LaunchedEffect(currentIndex) {
        if (lyrics.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    index = currentIndex,
                    scrollOffset = -topFocusOffsetPx.toInt()
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isFetching) {
            CircularProgressIndicator(color = Color.White)
        } else if (lyrics.isEmpty()) {
            Text(
                text = t("no_lyrics", "No lyrics found online", "player"),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.5f)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 20.dp, bottom = 400.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(lyrics) { index, lyric ->
                    val isCurrent = index == currentIndex
                    
                    val color by animateColorAsState(
                        targetValue = if (isCurrent) Color.White else Color.White.copy(alpha = 0.35f),
                        animationSpec = tween(500),
                        label = "LyricColor"
                    )
                    
                    val scale by animateFloatAsState(
                        targetValue = if (isCurrent) 1.05f else 1f,
                        animationSpec = tween(500),
                        label = "LyricScale"
                    )

                    Text(
                        text = lyric.content,
                        color = color,
                        fontSize = if (isCurrent) 20.sp else 20.sp,
                        lineHeight = if (isCurrent) 32.sp else 26.sp,
                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLyricClick(lyric.timeStamp) }
                            .padding(vertical = 10.dp, horizontal = 24.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    )
                }
            }
        }
    }
}
