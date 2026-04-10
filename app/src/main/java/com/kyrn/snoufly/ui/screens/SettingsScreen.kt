package com.kyrn.snoufly.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyrn.snoufly.data.BackupManager
import com.kyrn.snoufly.data.SettingsManager
import com.kyrn.snoufly.data.ThemeMode
import com.kyrn.snoufly.ui.MainViewModel
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onEqualizerClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val minDuration by viewModel.minDuration.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    
    var showThemeDialog by remember { mutableStateOf(false) }

    // Backup Management
    val settingsManager = remember { SettingsManager(context) }
    val backupManager = remember { BackupManager(context, settingsManager) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val json = backupManager.createBackup()
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        stream.write(json.toByteArray())
                    }
                }
            }
        }
    )

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val json = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { it.readText() } ?: ""
                    if (backupManager.restoreBackup(json)) {
                        // Success toast or refresh
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsCategory(title = "Library")
            SettingsItem(
                icon = Icons.Default.Refresh,
                title = "Rescan Library",
                subtitle = "Look for new music files",
                onClick = { viewModel.loadSongs() }
            )
            
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Minimum Duration", style = MaterialTheme.typography.bodyLarge)
                }
                Slider(
                    value = minDuration.toFloat(),
                    onValueChange = { viewModel.updateMinDuration(it.toLong()) },
                    valueRange = 0f..60000f, // 0 to 60 seconds
                    steps = 11
                )
                Text(
                    text = "Hide tracks shorter than ${minDuration / 1000}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 40.dp)
                )
            }

            SettingsCategory(title = "Data & Backup")
            SettingsItem(
                icon = Icons.Default.FileUpload,
                title = "Export Settings",
                subtitle = "Save all your song edits and favorites to a .json file",
                onClick = { createBackupLauncher.launch("snoufly_backup_${System.currentTimeMillis()}.json") }
            )
            SettingsItem(
                icon = Icons.Default.FileDownload,
                title = "Import Settings",
                subtitle = "Restore your previous edits from a backup file",
                onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            )

            SettingsCategory(title = "Appearance")
            SettingsItem(
                icon = Icons.Default.Palette,
                title = "Theme",
                subtitle = when(themeMode) {
                    ThemeMode.SYSTEM -> "System Default"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
                onClick = { showThemeDialog = true }
            )

            SettingsCategory(title = "Audio")
            SettingsItem(
                icon = Icons.Default.Equalizer,
                title = "Equalizer",
                subtitle = "In-app professional audio engine",
                onClick = onEqualizerClick
            )

            SettingsCategory(title = "About")
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Version",
                subtitle = "1.0.0 (Stable)",
                onClick = {}
            )
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme") },
            text = {
                Column {
                    ThemeOption("System Default", themeMode == ThemeMode.SYSTEM) {
                        viewModel.updateThemeMode(ThemeMode.SYSTEM)
                        showThemeDialog = false
                    }
                    ThemeOption("Light", themeMode == ThemeMode.LIGHT) {
                        viewModel.updateThemeMode(ThemeMode.LIGHT)
                        showThemeDialog = false
                    }
                    ThemeOption("Dark", themeMode == ThemeMode.DARK) {
                        viewModel.updateThemeMode(ThemeMode.DARK)
                        showThemeDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ThemeOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun SettingsCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {},
    isSwitch: Boolean = false,
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    Surface(
        onClick = if (!isSwitch) onClick else ({}),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSwitch) {
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
    }
}
