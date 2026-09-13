package com.venunair.warden.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venunair.warden.data.DigestFrequency
import com.venunair.warden.data.Region
import com.venunair.warden.data.SettingsRepository
import com.venunair.warden.data.ThemeMode
import com.venunair.warden.data.UserPreferences
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
}
