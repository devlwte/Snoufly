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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
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

    val translations by viewModel.translations.collectAsState()

    // Language states
    @Suppress("SpellCheckingInspection")
    val availableLangs by viewModel.availableLanguages.collectAsState()
    val repoUrl by viewModel.langRepoUrl.collectAsState()
    val isDownloading by viewModel.isDownloadingLang.collectAsState()
    val selectedCode by viewModel.selectedLangCode.collectAsState()

    // Backup & Lyrics states
    val backupUri by viewModel.backupUri.collectAsState()
    val backupInterval by viewModel.backupInterval.collectAsState()
    val lastBackupTime by viewModel.lastBackupTime.collectAsState()
    val lyricsTemplate by viewModel.lyricsApiTemplate.collectAsState()
    val lyricsUserAgent by viewModel.lyricsUserAgent.collectAsState()

    // Dialog visibility — using MutableState directly avoids "assigned value is never read" warnings
    val showRepoDialog = remember { mutableStateOf(false) }
    val showThemeDialog = remember { mutableStateOf(false) }
    val showBackupIntervalDialog = remember { mutableStateOf(false) }
    val showLyricsConfigDialog = remember { mutableStateOf(false) }

    // Dialog field states
    var tempUrl by remember { mutableStateOf(repoUrl) }
    var tempTemplate by remember { mutableStateOf(lyricsTemplate) }
    var tempUserAgent by remember { mutableStateOf(lyricsUserAgent) }

    // Reset dialog fields when dialogs open
    LaunchedEffect(showRepoDialog.value) {
        if (showRepoDialog.value) tempUrl = repoUrl
    }
    LaunchedEffect(showLyricsConfigDialog.value) {
        if (showLyricsConfigDialog.value) {
            tempTemplate = lyricsTemplate
            tempUserAgent = lyricsUserAgent
        }
    }

    // Translation helper
    fun t(section: String, key: String, fallback: String): String {
        val sectionMap = when (section) {
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
        val keys = key.split(".")
        var current: Any? = sectionMap
        for (part in keys) {
            current = if (current is Map<*, *>) current[part] else null
        }
        return current?.toString() ?: fallback
    }

    fun ts(key: String, fallback: String) = t("settings", key, fallback)
    fun tc(key: String, fallback: String) = t("common", key, fallback)

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                viewModel.exportBackup(it) { success ->
                    Toast.makeText(context, if (success) tc("done", "Done") else tc("error_occurred", "Error"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                viewModel.importBackup(it) { success ->
                    Toast.makeText(context, if (success) tc("done", "Done") else tc("error_occurred", "Error"), Toast.LENGTH_SHORT).show()
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

    Scaffold(
        topBar = { TopAppBar(title = { Text(ts("title", "Settings"), fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // --- LANGUAGE ---
            SettingsCategory(ts("cat_language", "Language"))

            SettingsItem(
                icon = Icons.Default.Language,
                title = ts("select_lang", "App Language"),
                subtitle = if (isDownloading) tc("loading", "Downloading...") else selectedCode.uppercase(),
                onClick = {}
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                    Text(
                        @Suppress("SpellCheckingInspection")
                        ts("no_langs", "Connect to repository"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            SettingsItem(
                icon = Icons.Default.Link,
                title = ts("repo_url", "Language Repository"),
                subtitle = repoUrl,
                onClick = { showRepoDialog.value = true }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 0.5.dp,
                color = Color.Gray.copy(alpha = 0.2f)
            )

            // --- LIBRARY ---
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
                    text = ts("hide_shorter", "Hide tracks shorter than {0}s").replace("{0}", (minDuration / 1000).toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // --- AUDIO ---
            SettingsCategory(ts("cat_audio", "Audio & Effects"))
            SettingsItem(
                icon = Icons.Default.GraphicEq,
                title = ts("eq_engine", "Equalizer & Sonic Engine"),
                subtitle = ts("eq_engine_sub", "Configure EQ, Speed and Pitch"),
                onClick = onEqualizerClick
            )

            // --- LYRICS ---
            SettingsCategory(ts("cat_lyrics", "Lyrics Engine"))
            SettingsItem(
                icon = Icons.Default.Language,
                title = ts("api_template", "Online Lyrics Provider"),
                subtitle = lyricsTemplate.ifBlank { @Suppress("SpellCheckingInspection") "Default (LRCLIB)" },
                onClick = { showLyricsConfigDialog.value = true }
            )

            // --- DATA & BACKUP ---
            SettingsCategory(ts("cat_data", "Data & Backup"))
            SettingsItem(
                icon = Icons.Default.CloudUpload,
                title = ts("export", "Manual Export"),
                subtitle = ts("export_sub", "Save metadata and favorites"),
                onClick = { exportLauncher.launch("snoufly_backup_${System.currentTimeMillis()}.json") }
            )
            SettingsItem(
                icon = Icons.Default.CloudDownload,
                title = ts("import", "Manual Import"),
                subtitle = ts("import_sub", "Restore from file"),
                onClick = { importLauncher.launch(arrayOf("application/json")) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 0.5.dp,
                color = Color.Gray.copy(alpha = 0.1f)
            )

            SettingsItem(
                icon = Icons.Default.Folder,
                title = ts("backup_folder", "Auto Backup Location"),
                subtitle = backupUri?.toUri()?.path ?: "Not set",
                onClick = { folderLauncher.launch(null) }
            )

            SettingsItem(
                icon = Icons.Default.Schedule,
                title = "Backup Frequency",
                subtitle = backupInterval.name.lowercase().replaceFirstChar { it.uppercase() },
                onClick = { showBackupIntervalDialog.value = true }
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

            // --- APPEARANCE ---
            SettingsCategory(ts("cat_appearance", "Appearance"))
            SettingsItem(
                icon = Icons.Default.Palette,
                title = ts("theme", "Theme Mode"),
                subtitle = when (themeMode) {
                    ThemeMode.SYSTEM -> ts("theme_system", "System Default")
                    ThemeMode.LIGHT -> ts("theme_light", "Light Mode")
                    ThemeMode.DARK -> ts("theme_dark", "Dark Mode")
                },
                onClick = { showThemeDialog.value = true }
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Dialogs
    if (showRepoDialog.value) {
        AlertDialog(
            onDismissRequest = { showRepoDialog.value = false },
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
                    showRepoDialog.value = false
                }) { Text(tc("save", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { showRepoDialog.value = false }) { Text(tc("cancel", "Cancel")) }
            }
        )
    }

    if (showLyricsConfigDialog.value) {
        AlertDialog(
            onDismissRequest = { showLyricsConfigDialog.value = false },
            title = { Text("Lyrics API Configuration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = tempTemplate,
                        onValueChange = { tempTemplate = it },
                        label = { Text(ts("api_template", "API URL Template")) },
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
                    showLyricsConfigDialog.value = false
                }) { Text(tc("save", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { showLyricsConfigDialog.value = false }) { Text(tc("cancel", "Cancel")) }
            }
        )
    }

    if (showThemeDialog.value) {
        AlertDialog(
            onDismissRequest = { showThemeDialog.value = false },
            title = { Text(ts("theme", "Theme Mode")) },
            text = {
                Column {
                    ThemeOption(ts("theme_system", "System Default"), themeMode == ThemeMode.SYSTEM) {
                        viewModel.updateThemeMode(ThemeMode.SYSTEM)
                        showThemeDialog.value = false
                    }
                    ThemeOption(ts("theme_light", "Light Mode"), themeMode == ThemeMode.LIGHT) {
                        viewModel.updateThemeMode(ThemeMode.LIGHT)
                        showThemeDialog.value = false
                    }
                    ThemeOption(ts("theme_dark", "Dark Mode"), themeMode == ThemeMode.DARK) {
                        viewModel.updateThemeMode(ThemeMode.DARK)
                        showThemeDialog.value = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog.value = false }) { Text(tc("cancel", "Cancel")) }
            }
        )
    }

    if (showBackupIntervalDialog.value) {
        AlertDialog(
            onDismissRequest = { showBackupIntervalDialog.value = false },
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
                                    showBackupIntervalDialog.value = false
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
                TextButton(onClick = { showBackupIntervalDialog.value = false }) {
                    Text(tc("cancel", "Cancel"))
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
        Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun SettingsCategory(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}