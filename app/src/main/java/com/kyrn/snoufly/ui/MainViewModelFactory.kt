package com.kyrn.snoufly.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kyrn.snoufly.data.BackupManager
import com.kyrn.snoufly.data.MusicRepository
import com.kyrn.snoufly.data.SettingsManager

class MainViewModelFactory(
    private val application: Application,
    private val repository: MusicRepository,
    private val settingsManager: SettingsManager,
    private val backupManager: BackupManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository, settingsManager, backupManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
