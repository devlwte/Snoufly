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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun AutoCover(
    name: String,
    modifier: Modifier = Modifier
) {
    // 1. Limpiar el nombre (quitar símbolos y paréntesis)
    val cleanName = name.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
    val initial = if (cleanName.isNotEmpty()) cleanName.first().uppercase() else "?"

    // 2. Generar color persistente basado en el nombre
    val hash = abs(name.hashCode())
    val baseColor = songColors[hash % songColors.size]
    val darkColor = baseColor.copy(alpha = 0.8f)

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(baseColor, darkColor)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

// Paleta de colores profesionales para los covers
private val songColors = listOf(
    Color(0xFFE57373), // Red
    Color(0xFFF06292), // Pink
    Color(0xFFBA68C8), // Purple
    Color(0xFF9575CD), // Deep Purple
    Color(0xFF7986CB), // Indigo
    Color(0xFF64B5F6), // Blue
    Color(0xFF4FC3F7), // Light Blue
    Color(0xFF4DD0E1), // Cyan
    Color(0xFF4DB6AC), // Teal
    Color(0xFF81C784), // Green
    Color(0xFFAED581), // Light Green
    Color(0xFFFFD54F), // Amber
    Color(0xFFFFB74D), // Orange
    Color(0xFFFF8A65), // Deep Orange
    Color(0xFFA1887F), // Brown
    Color(0xFF90A4AE)  // Blue Grey
)
