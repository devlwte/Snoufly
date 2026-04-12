package com.kyrn.snoufly.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.net.toUri
import com.kyrn.snoufly.data.BackupInterval
import com.kyrn.snoufly.data.ThemeMode
import com.kyrn.snoufly.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onEqualizerClick: () -> Unit
) {
    val context = LocalContext.current
    val minDuration by viewModel.minDuration.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val backupUri by viewModel.backupUri.collectAsState()
    val backupInterval by viewModel.backupInterval.collectAsState()
    val lastBackupTime by viewModel.lastBackupTime.collectAsState()
    
    var showThemeDialog by remember { mutableStateOf(false) }
    var showBackupIntervalDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                viewModel.exportBackup(it) { success ->
                    Toast.makeText(context, if (success) "Backup exported" else "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                viewModel.importBackup(it) { success ->
                    Toast.makeText(context, if (success) "Data restored" else "Restore failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                viewModel.updateBackupSettings(it.toString(), backupInterval)
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
                    valueRange = 0f..60000f,
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
                icon = Icons.Default.CloudUpload,
                title = "Manual Export",
                subtitle = "Save all metadata, favorites, and history to a file",
                onClick = { exportLauncher.launch("snoufly_backup_${System.currentTimeMillis()}.json") }
            )
            SettingsItem(
                icon = Icons.Default.CloudDownload,
                title = "Manual Import",
                subtitle = "Restore data from a backup file",
                onClick = { importLauncher.launch(arrayOf("application/json")) }
            )
            
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), thickness = 0.5.dp)
            
            SettingsItem(
                icon = Icons.Default.Folder,
                title = "Auto Backup Location",
                subtitle = backupUri?.toUri()?.path ?: "Not set (Select folder)",
                onClick = { folderLauncher.launch(null) }
            )
            
            SettingsItem(
                icon = Icons.Default.Schedule,
                title = "Backup Frequency",
                subtitle = backupInterval.name.lowercase().replaceFirstChar { it.uppercase() },
                onClick = { showBackupIntervalDialog = true }
            )
            
            if (lastBackupTime > 0) {
                val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(lastBackupTime))
                Text(
                    text = "Last backup: $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 56.dp, bottom = 8.dp)
                )
            }

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

    if (showBackupIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showBackupIntervalDialog = false },
            title = { Text("Backup Frequency") },
            text = {
                Column {
                    BackupInterval.entries.forEach { interval ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = backupInterval == interval,
                                onClick = {
                                    viewModel.updateBackupSettings(backupUri, interval)
                                    showBackupIntervalDialog = false
                                }
                            )
                            Text(
                                text = interval.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupIntervalDialog = false }) {
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
