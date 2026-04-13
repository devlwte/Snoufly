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
import androidx.compose.ui.graphics.Color
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
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onEqualizerClick: () -> Unit
) {
    val context = LocalContext.current
    val minDuration by viewModel.minDuration.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    
    // IMPORTANTE: Observar las traducciones para que la UI se actualice
    val translations by viewModel.translations.collectAsState()

    // Estados de Idioma OTA
    val repoUrl by viewModel.langRepoUrl.collectAsState()
    val availableLangs by viewModel.availableLanguages.collectAsState()
    val isDownloading by viewModel.isDownloadingLang.collectAsState()
    val selectedCode by viewModel.selectedLangCode.collectAsState()

    // Estados de Backup
    val backupUri by viewModel.backupUri.collectAsState()
    val backupInterval by viewModel.backupInterval.collectAsState()
    val lastBackupTime by viewModel.lastBackupTime.collectAsState()
    val lyricsTemplate by viewModel.lyricsApiTemplate.collectAsState()
    val lyricsUserAgent by viewModel.lyricsUserAgent.collectAsState()

    var showRepoDialog by remember { mutableStateOf(false) }
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
                viewModel.updateBackupSettings(it.toString(), backupInterval)
            }
        }
    )

    // Helpers de traducción mejorados (ahora dependen de 'translations')
    fun t(section: String, key: String, fallback: String): String {
        val map = when(section) {
            "common" -> translations.common
            "library" -> translations.library
            "player" -> translations.player
            "favorites" -> translations.favorites
            "equalizer" -> translations.equalizer
            "settings" -> translations.settings
            "edit_dialog" -> translations.edit_dialog
            "song_item" -> translations.song_item
            else -> emptyMap()
        }
        return map[key] ?: fallback
    }
    fun ts(key: String, fallback: String) = t("settings", key, fallback)
    fun tc(key: String, fallback: String) = t("common", key, fallback)

    Scaffold(
        topBar = { TopAppBar(title = { Text(ts("title", "Settings"), fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            // --- SECCIÓN IDIOMA ---
            SettingsCategory(ts("cat_language", "Language"))
            
            SettingsItem(
                icon = Icons.Default.Language,
                title = ts("select_lang", "App Language"),
                subtitle = if (isDownloading) tc("loading", "Downloading...") else selectedCode.uppercase(),
                onClick = {}
            )

            // Selector horizontal de idiomas disponibles del repo
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableLangs.forEach { lang ->
                    FilterChip(
                        selected = selectedCode == lang.code,
                        onClick = { if (!isDownloading) viewModel.selectLanguage(lang) },
                        label = { Text(lang.nativeName) },
                        enabled = !isDownloading
                    )
                }
                if (availableLangs.isEmpty()) {
                    Text(ts("no_langs", "Connect to repository to load more languages"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }

            SettingsItem(
                icon = Icons.Default.Link,
                title = ts("repo_url", "Language Repository"),
                subtitle = repoUrl,
                onClick = { showRepoDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.2f))

            // --- SECCIÓN LIBRERÍA ---
            SettingsCategory(ts("cat_library", "Library & Content"))
            SettingsItem(
                icon = Icons.Default.Refresh,
                title = ts("rescan", "Rescan Library"),
                subtitle = ts("rescan_sub", "Scan for new music files"),
                onClick = { viewModel.loadSongs() }
            )
            
            Column(modifier = Modifier.padding(16.dp)) {
                Text(ts("min_duration", "Minimum Song Duration"), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = minDuration.toFloat(),
                    onValueChange = { viewModel.updateMinDuration(it.toLong()) },
                    valueRange = 0f..60000f,
                    steps = 11
                )
                Text(
                    text = ts("hide_shorter", "Hide tracks shorter than {0}s").replace("{0}", (minDuration/1000).toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // --- SECCIÓN AUDIO ---
            SettingsCategory(ts("cat_audio", "Audio & Effects"))
            SettingsItem(
                icon = Icons.Default.GraphicEq,
                title = ts("eq_engine", "Equalizer & Sonic Engine"),
                subtitle = ts("eq_engine_sub", "Configure EQ, Speed and Pitch"),
                onClick = onEqualizerClick
            )

            // --- SECCIÓN LYRICS ENGINE ---
            SettingsCategory(title = ts("cat_lyrics", "Lyrics Engine"))
            SettingsItem(
                icon = Icons.Default.Language,
                title = ts("lyrics_provider", "Online Lyrics Provider"),
                subtitle = ts("lyrics_provider_sub", "Configure API templates and User-Agent"),
                onClick = { showLyricsConfigDialog = true }
            )

            // --- SECCIÓN DATA & BACKUP ---
            SettingsCategory(title = ts("cat_backup", "Data & Backup"))
            SettingsItem(
                icon = Icons.Default.CloudUpload,
                title = ts("manual_export", "Manual Export"),
                subtitle = ts("manual_export_sub", "Save all metadata, favorites, and history"),
                onClick = { exportLauncher.launch("snoufly_backup_${System.currentTimeMillis()}.json") }
            )
            SettingsItem(
                icon = Icons.Default.CloudDownload,
                title = ts("manual_import", "Manual Import"),
                subtitle = ts("manual_import_sub", "Restore data from a backup file"),
                onClick = { importLauncher.launch(arrayOf("application/json")) }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.1f))
            
            SettingsItem(
                icon = Icons.Default.Folder,
                title = ts("auto_backup_loc", "Auto Backup Location"),
                subtitle = backupUri?.toUri()?.path ?: ts("not_set", "Not set (Select folder)"),
                onClick = { folderLauncher.launch(null) }
            )
            
            SettingsItem(
                icon = Icons.Default.Schedule,
                title = ts("backup_freq", "Backup Frequency"),
                subtitle = backupInterval.name.lowercase().replaceFirstChar { it.uppercase() },
                onClick = { showBackupIntervalDialog = true }
            )

            if (lastBackupTime > 0) {
                val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(lastBackupTime))
                Text(
                    text = ts("last_backup", "Last backup: {0}").replace("{0}", dateStr),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 56.dp, bottom = 8.dp)
                )
            }

            // --- SECCIÓN APARIENCIA ---
            SettingsCategory(ts("cat_appearance", "Appearance"))
            SettingsItem(
                icon = Icons.Default.Palette,
                title = ts("theme", "Theme Mode"),
                subtitle = when(themeMode) {
                    ThemeMode.SYSTEM -> ts("theme_system", "System Default")
                    ThemeMode.LIGHT -> ts("theme_light", "Light Mode")
                    ThemeMode.DARK -> ts("theme_dark", "Dark Mode")
                },
                onClick = { showThemeDialog = true }
            )

            // --- SECCIÓN ABOUT (RESTAURADA) ---
            SettingsCategory(ts("cat_about", "About"))
            SettingsItem(
                icon = Icons.Default.Info,
                title = ts("version", "Version"),
                subtitle = "1.0.0 (Stable)",
                onClick = {}
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Modales (Repo, Letras, Tema, Backup...)
    if (showRepoDialog) {
        var tempUrl by remember { mutableStateOf(repoUrl) }
        AlertDialog(
            onDismissRequest = { showRepoDialog = false },
            title = { Text(ts("repo_url", "Repository URL")) },
            text = {
                OutlinedTextField(
                    value = tempUrl,
                    onValueChange = { tempUrl = it },
                    label = { Text("URL (GitHub Raw)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateLangRepoUrl(tempUrl)
                    showRepoDialog = false
                }) { Text(tc("save", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { showRepoDialog = false }) { Text(tc("cancel", "Cancel")) }
            }
        )
    }

    if (showLyricsConfigDialog) {
        var tempTemplate by remember { mutableStateOf(lyricsTemplate) }
        var tempUserAgent by remember { mutableStateOf(lyricsUserAgent) }
        AlertDialog(
            onDismissRequest = { showLyricsConfigDialog = false },
            title = { Text(ts("lyrics_config", "Lyrics API Configuration")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = tempTemplate,
                        onValueChange = { tempTemplate = it },
                        label = { Text("API URL Template") },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("%TRACK%, %ARTIST%, %ALBUM%", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = tempUserAgent,
                        onValueChange = { tempUserAgent = it },
                        label = { Text("User-Agent") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateLyricsSettings(tempTemplate, tempUserAgent)
                    showLyricsConfigDialog = false
                }) { Text(tc("save", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { showLyricsConfigDialog = false }) { Text(tc("cancel", "Cancel")) }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(ts("theme", "Theme Mode")) },
            text = {
                Column {
                    ThemeOption(ts("theme_system", "System Default"), themeMode == ThemeMode.SYSTEM) {
                        viewModel.updateThemeMode(ThemeMode.SYSTEM)
                        showThemeDialog = false
                    }
                    ThemeOption(ts("theme_light", "Light Mode"), themeMode == ThemeMode.LIGHT) {
                        viewModel.updateThemeMode(ThemeMode.LIGHT)
                        showThemeDialog = false
                    }
                    ThemeOption(ts("theme_dark", "Dark Mode"), themeMode == ThemeMode.DARK) {
                        viewModel.updateThemeMode(ThemeMode.DARK)
                        showThemeDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text(tc("cancel", "Cancel")) }
            }
        )
    }

    if (showBackupIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showBackupIntervalDialog = false },
            title = { Text(ts("backup_freq", "Backup Frequency")) },
            text = {
                Column {
                    BackupInterval.entries.forEach { interval ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
                    Text(tc("cancel", "Cancel"))
                }
            }
        )
    }
}

@Composable
fun ThemeOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun SettingsCategory(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
