package com.kyrn.snoufly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlin.math.abs

/**
 * SnouflyImage: El componente profesional para cargar carátulas.
 * Maneja automáticamente el fallback al generador de covers sin parpadeos.
 */
@Composable
fun SnouflyImage(
    model: Any?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale
    ) {
        val state = painter.state
        when (state) {
            is AsyncImagePainter.State.Loading, 
            is AsyncImagePainter.State.Error -> {
                // Si está cargando o falla, mostramos el cover generado de inmediato
                AutoCover(name = title, modifier = Modifier.fillMaxSize())
            }
            else -> {
                SubcomposeAsyncImageContent()
            }
        }
    }
}

@Composable
fun AutoCover(
    name: String,
    modifier: Modifier = Modifier
) {
    // Limpieza de nombre profesional (quita etiquetas como [Official Video], (2024), etc.)
    val cleanName = name.replace(Regex("\\([^)]*\\)|\\[[^]]*\\]"), "")
        .replace(Regex("[^a-zA-Z0-9 ]"), "")
        .trim()
    
    val initial = if (cleanName.isNotEmpty()) cleanName.first().uppercase() else "?"

    // Color persistente basado en el nombre
    val hash = abs(name.hashCode())
    val baseColor = songColors[hash % songColors.size]
    
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(baseColor, baseColor.copy(alpha = 0.7f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

private val songColors = listOf(
    Color(0xFFEF5350), Color(0xFFEC407A), Color(0xFFAB47BC),
    Color(0xFF7E57C2), Color(0xFF5C6BC0), Color(0xFF42A5F5),
    Color(0xFF29B6F6), Color(0xFF26C6DA), Color(0xFF26A69A),
    Color(0xFF66BB6A), Color(0xFF9CCC65), Color(0xFFD4E157),
    Color(0xFFFFEE58), Color(0xFFFFCA28), Color(0xFFFFA726),
    Color(0xFFFF7043), Color(0xFF8D6E63), Color(0xFFBDBDBD), Color(0xFF78909C)
)
