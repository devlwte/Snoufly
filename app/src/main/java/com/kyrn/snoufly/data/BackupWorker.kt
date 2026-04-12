package com.kyrn.snoufly.data

import android.content.Context
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

class BackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settingsManager = SettingsManager(applicationContext)
        val backupManager = BackupManager(applicationContext, settingsManager)
        
        val backupUri = settingsManager.backupUriFlow.first()
        
        return if (!backupUri.isNullOrEmpty()) {
            val result = backupManager.autoBackup(backupUri.toUri())
            if (result.isSuccess) Result.success() else Result.retry()
        } else {
            Result.failure()
        }
    }
}
