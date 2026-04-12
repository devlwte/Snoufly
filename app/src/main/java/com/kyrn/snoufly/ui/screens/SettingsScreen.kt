package com.kyrn.snoufly.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.kyrn.snoufly.R
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
    val lyricsTemplate by viewModel.lyricsApiTemplate.collectAsState()
    val lyricsUserAgent by viewModel.lyricsUserAgent.collectAsState()
    
    var showThemeDialog by remember { mutableStateOf(false) }
    var showBackupIntervalDialog by remember { mutableStateOf(false) }
    var showLyricsConfigDialog by remember { mutableStateOf(false) }

    val backupExportedMsg = stringResource(R.string.backup_exported)
    val exportFailedMsg = stringResource(R.string.export_failed)
    val dataRestoredMsg = stringResource(R.string.data_restored)
    val restoreFailedMsg = stringResource(R.string.restore_failed)

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                viewModel.exportBackup(it) { success ->
                    Toast.makeText(context, if (success) backupExportedMsg else exportFailedMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                viewModel.importBackup(it) { success ->
                    Toast.makeText(context, if (success) dataRestoredMsg else restoreFailedMsg, Toast.LENGTH_SHORT).show()
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
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsCategory(title = stringResource(R.string.category_library))
            SettingsItem(
                icon = Icons.Default.Refresh,
                title = stringResource(R.string.rescan_library),
                subtitle = stringResource(R.string.rescan_library_subtitle),
                onClick = { viewModel.loadSongs() }
            )
            
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = stringResource(R.string.minimum_duration), style = MaterialTheme.typography.bodyLarge)
                }
                Slider(
                    value = minDuration.toFloat(),
                    onValueChange = { viewModel.updateMinDuration(it.toLong()) },
                    valueRange = 0f..60000f,
                    steps = 11
                )
                Text(
                    text = stringResource(R.string.hide_tracks_shorter_than, minDuration / 1000),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 40.dp)
                )
            }

            SettingsCategory(title = "Lyrics Engine")
            SettingsItem(
                icon = Icons.Default.Language,
                title = "Online Lyrics Provider",
                subtitle = "Configure API templates and User-Agent",
                onClick = { showLyricsConfigDialog = true }
            )

            SettingsCategory(title = stringResource(R.string.category_data_backup))
            SettingsItem(
                icon = Icons.Default.CloudUpload,
                title = stringResource(R.string.manual_export),
                subtitle = stringResource(R.string.manual_export_subtitle),
                onClick = { exportLauncher.launch("snoufly_backup_${System.currentTimeMillis()}.json") }
            )
            SettingsItem(
                icon = Icons.Default.CloudDownload,
                title = stringResource(R.string.manual_import),
                subtitle = stringResource(R.string.manual_import_subtitle),
                onClick = { importLauncher.launch(arrayOf("application/json")) }
            )
            
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), thickness = 0.5.dp)
            
            SettingsItem(
                icon = Icons.Default.Folder,
                title = stringResource(R.string.auto_backup_location),
                subtitle = backupUri?.toUri()?.path ?: stringResource(R.string.not_set_select_folder),
                onClick = { folderLauncher.launch(null) }
            )
            
            SettingsItem(
                icon = Icons.Default.Schedule,
                title = stringResource(R.string.backup_frequency),
                subtitle = backupInterval.name.lowercase().replaceFirstChar { it.uppercase() },
                onClick = { showBackupIntervalDialog = true }
            )
            
            if (lastBackupTime > 0) {
                val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(lastBackupTime))
                Text(
                    text = stringResource(R.string.last_backup, dateStr),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 56.dp, bottom = 8.dp)
                )
            }

            SettingsCategory(title = stringResource(R.string.category_appearance))
            SettingsItem(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.theme),
                subtitle = when(themeMode) {
                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                },
                onClick = { showThemeDialog = true }
            )

            SettingsCategory(title = stringResource(R.string.category_audio))
            SettingsItem(
                icon = Icons.Default.Equalizer,
                title = stringResource(R.string.equalizer),
                subtitle = stringResource(R.string.equalizer_subtitle),
                onClick = onEqualizerClick
            )

            SettingsCategory(title = stringResource(R.string.category_about))
            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.version),
                subtitle = stringResource(R.string.version_subtitle),
                onClick = {}
            )
        }
    }

    if (showLyricsConfigDialog) {
        var tempTemplate by remember { mutableStateOf(lyricsTemplate) }
        var tempUserAgent by remember { mutableStateOf(lyricsUserAgent) }

        AlertDialog(
            onDismissRequest = { showLyricsConfigDialog = false },
            title = { Text("Lyrics API Configuration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = tempTemplate,
                        onValueChange = { tempTemplate = it },
                        label = { Text("API URL Template") },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("Wildcards: %TRACK%, %ARTIST%, %ALBUM%, %DURATION%", fontSize = 10.sp)
                        }
                    )
                    OutlinedTextField(
                        value = tempUserAgent,
                        onValueChange = { tempUserAgent = it },
                        label = { Text("User-Agent") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Note: Your provider must return a JSON with 'syncedLyrics' or 'plainLyrics' fields (LRCLIB standard).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateLyricsSettings(tempTemplate, tempUserAgent)
                    showLyricsConfigDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLyricsConfigDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.choose_theme)) },
            text = {
                Column {
                    ThemeOption(stringResource(R.string.theme_system), themeMode == ThemeMode.SYSTEM) {
                        viewModel.updateThemeMode(ThemeMode.SYSTEM)
                        showThemeDialog = false
                    }
                    ThemeOption(stringResource(R.string.theme_light), themeMode == ThemeMode.LIGHT) {
                        viewModel.updateThemeMode(ThemeMode.LIGHT)
                        showThemeDialog = false
                    }
                    ThemeOption(stringResource(R.string.theme_dark), themeMode == ThemeMode.DARK) {
                        viewModel.updateThemeMode(ThemeMode.DARK)
                        showThemeDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showBackupIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showBackupIntervalDialog = false },
            title = { Text(stringResource(R.string.backup_frequency)) },
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
                    Text(stringResource(R.string.cancel))
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
