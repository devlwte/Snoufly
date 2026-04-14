package com.kyrn.snoufly.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupData(
    val favoriteIds: Set<Long>,
    val recentlyPlayedIds: List<Long>,
    val playCounts: Map<Long, Int>,
    val customMetadata: Map<Long, CustomMetadata>,
    val settings: Map<String, Any>
)

class BackupManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    private val gson = Gson()

    suspend fun createBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(getBackupData())
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            } ?: return@withContext Result.failure(Exception("Could not open output stream"))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun autoBackup(treeUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val directory = DocumentFile.fromTreeUri(context, treeUri)
            if (directory == null || !directory.canWrite()) {
                return@withContext Result.failure(Exception("Cannot write to directory"))
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(Date())
            val file = directory.createFile("application/json", "snoufly_auto_$timestamp.json")
                ?: return@withContext Result.failure(Exception("Could not create backup file"))

            val json = gson.toJson(getBackupData())
            context.contentResolver.openOutputStream(file.uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
            
            settingsManager.updateLastBackupTime(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    private suspend fun getBackupData(): BackupData {
        return BackupData(
            favoriteIds = settingsManager.favoriteIdsFlow.first(),
            recentlyPlayedIds = settingsManager.recentlyPlayedIdsFlow.first(),
            playCounts = settingsManager.playCountsFlow.first(),
            customMetadata = settingsManager.customMetadataFlow.first(),
            settings = mapOf(
                "theme" to settingsManager.themeModeFlow.first().name,
                "sortOrder" to settingsManager.sortOrderFlow.first().name,
                "minDuration" to settingsManager.minDurationFlow.first()
            )
        )
    }

    suspend fun restoreBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) 
                ?: return@withContext Result.failure(Exception("Could not open input stream"))
            
            val backupData = InputStreamReader(inputStream).use { reader ->
                gson.fromJson(reader, BackupData::class.java)
            }

            settingsManager.restoreAllData(
                favorites = backupData.favoriteIds,
                recent = backupData.recentlyPlayedIds,
                counts = backupData.playCounts,
                metadata = backupData.customMetadata,
                theme = backupData.settings["theme"] as? String,
                sort = backupData.settings["sortOrder"] as? String,
                minDur = (backupData.settings["minDuration"] as? Double)?.toLong()
            )

            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
