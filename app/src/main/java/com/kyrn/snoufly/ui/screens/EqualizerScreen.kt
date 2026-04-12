package com.kyrn.snoufly.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyrn.snoufly.playback.PlaybackViewModel
import com.kyrn.snoufly.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    var selectedTabIndex by remember { mutableStateOf(0) }
    val categories = mainViewModel.audioCategories.keys.toList()

    val displayBands = remember(eqBands) {
        if (eqBands.size < 5) List(5) { 0 } else eqBands.take(5)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Sound Laboratory", fontWeight = FontWeight.ExtraBold) },
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
            // Master Engine Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Engine Snoufly v5.0", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Laboratory Quality Audio", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = eqEnabled,
                        onCheckedChange = { mainViewModel.updateEqEnabled(it) }
                    )
                }
            }

            // Anime Mode Special Button
            Text("Featured Style", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Button(
                onClick = {
                    mainViewModel.updateEqEnabled(true)
                    mainViewModel.audioPresets["Anime"]?.let { mainViewModel.updateEqBands(it) }
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (eqBands == mainViewModel.audioPresets["Anime"]) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (eqBands == mainViewModel.audioPresets["Anime"]) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("🌸 ANIME AUTHENTIC MODE", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }

            // Category Navigation
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {},
                indicator = {}
            ) {
                categories.forEachIndexed { index, category ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(category, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Style Grid for Selected Category
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val currentCategoryPresets = mainViewModel.audioCategories[categories[selectedTabIndex]] ?: emptyList()
                currentCategoryPresets.filter { it != "Anime" }.forEach { preset ->
                    val isSelected = eqBands == mainViewModel.audioPresets[preset]
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            mainViewModel.updateEqEnabled(true)
                            mainViewModel.audioPresets[preset]?.let { mainViewModel.updateEqBands(it) }
                        },
                        label = { Text(preset) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Sonic Engine (Pitch & Speed)
            Text("Sonic Engine (PurePitch™)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            AudioControlSlider(
                label = "Speed",
                value = playbackSpeed,
                valueRange = 0.85f..1.3f, 
                onValueChange = { mainViewModel.updatePlaybackSpeed(it) },
                formattedValue = String.format("%.2fx", playbackSpeed)
            )
            AudioControlSlider(
                label = "Pitch",
                value = playbackPitch,
                valueRange = 0.85f..1.3f, 
                onValueChange = { mainViewModel.updatePlaybackPitch(it) },
                formattedValue = String.format("%.2fx", playbackPitch)
            )

            // High-Precision Bands
            Text("Studio Frequency Bands", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val labels = listOf("BASS", "LOW", "MID", "HIGH", "AIR")
                displayBands.forEachIndexed { index, level ->
                    BandSlider(
                        label = labels[index],
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
            
            Spacer(modifier = Modifier.height(40.dp))
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
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(formattedValue, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@Composable
fun RowScope.BandSlider(
    label: String,
    level: Int,
    enabled: Boolean,
    onLevelChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${if (level > 0) "+" else ""}${level / 100}dB",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = level.toFloat(),
            onValueChange = { if (enabled) onLevelChange(it.toInt()) },
            valueRange = -1500f..1500f,
            modifier = Modifier.weight(1f).graphicsLayer(rotationZ = 270f),
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
    }
}
