package com.kyrn.snoufly.data

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

class BackupWorker(
    context: Context,
    params: WorkerParameters,
    private val backupManager: BackupManager,
    private val settingsManager: SettingsManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val backupUriString = settingsManager.backupUriFlow.first()
        if (backupUriString.isNullOrBlank()) return androidx.work.ListenableWorker.Result.failure()

        val backupUri = Uri.parse(backupUriString)
        val result = backupManager.autoBackup(backupUri)
        
        return if (result.isSuccess) {
            settingsManager.updateLastBackupTime(System.currentTimeMillis())
            androidx.work.ListenableWorker.Result.success()
        } else {
            androidx.work.ListenableWorker.Result.retry()
        }
    }
}
