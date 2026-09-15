package com.venunair.wisma.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venunair.wisma.data.DigestFrequency
import com.venunair.wisma.data.Region
import com.venunair.wisma.data.SettingsRepository
import com.venunair.wisma.data.ThemeMode
import com.venunair.wisma.data.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    fun setDefaultReminderOffsets(offsets: List<Int>) {
        viewModelScope.launch { repository.setDefaultReminderOffsets(offsets) }
    }

    fun setDigestFrequency(frequency: DigestFrequency) {
        viewModelScope.launch { repository.setDigestFrequency(frequency) }
    }

    fun setAutoDetectEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoDetectEnabled(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setRegion(region: Region) {
        viewModelScope.launch { repository.setRegion(region) }
    }

    // Phase 2: stamped after a real Google Drive backup succeeds
    // (DriveBackupManager.backupNow) -- see settings_backup_last's
    // display of this value.
    fun setLastBackupAtMillis(millis: Long) {
        viewModelScope.launch { repository.setLastBackupAtMillis(millis) }
    }
}
