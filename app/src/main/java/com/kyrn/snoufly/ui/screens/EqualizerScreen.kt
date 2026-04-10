package com.kyrn.snoufly.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyrn.snoufly.playback.PlaybackViewModel
import com.kyrn.snoufly.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    mainViewModel: MainViewModel,
    playbackViewModel: PlaybackViewModel,
    onBackClick: () -> Unit
) {
    val eqEnabled by mainViewModel.eqEnabledFlow.collectAsState()
    val playbackSpeed by mainViewModel.playbackSpeedFlow.collectAsState()
    val playbackPitch by mainViewModel.playbackPitchFlow.collectAsState()
    val eqBands by mainViewModel.eqBandsFlow.collectAsState()

    // Ensure we have 5 bands (standard for Android)
    val displayBands = remember(eqBands) {
        if (eqBands.size < 5) List(5) { 0 } else eqBands.take(5)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Audio Professional", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        mainViewModel.updatePlaybackSpeed(1.0f)
                        mainViewModel.updatePlaybackPitch(1.0f)
                        mainViewModel.updateEqBands(List(5) { 0 })
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Master Switch
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Equalizer Engine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Enable high-fidelity processing", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = eqEnabled,
                        onCheckedChange = { mainViewModel.updateEqEnabled(it) }
                    )
                }
            }

            // Playback Controls (Speed & Pitch)
            Text("Playback Engine", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            AudioControlSlider(
                label = "Speed",
                value = playbackSpeed,
                valueRange = 0.5f..2.0f,
                onValueChange = { mainViewModel.updatePlaybackSpeed(it) },
                formattedValue = String.format("%.2fx", playbackSpeed)
            )

            AudioControlSlider(
                label = "Pitch",
                value = playbackPitch,
                valueRange = 0.5f..2.0f,
                onValueChange = { mainViewModel.updatePlaybackPitch(it) },
                formattedValue = String.format("%.2fx", playbackPitch)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Frequency Bands
            Text("Frequency Bands", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val labels = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")
                displayBands.forEachIndexed { index, level ->
                    BandSlider(
                        label = labels.getOrElse(index) { "${index + 1}" },
                        level = level,
                        enabled = eqEnabled,
                        onLevelChange = { newLevel ->
                            val newBands = displayBands.toMutableList()
                            newBands[index] = newLevel
                            mainViewModel.updateEqBands(newBands)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AudioControlSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    formattedValue: String
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(formattedValue, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@Composable
fun RowScope.BandSlider(
    label: String,
    level: Int,
    enabled: Boolean,
    onLevelChange: (Int) -> Unit
) {
    // Level is in milliBels, typical range is -1500 to 1500
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${if (level > 0) "+" else ""}${level / 100}dB",
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = level.toFloat(),
            onValueChange = { if (enabled) onLevelChange(it.toInt()) },
            valueRange = -1500f..1500f,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer(rotationZ = 270f),
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
    }
}
