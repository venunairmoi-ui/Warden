package com.venunair.warden.ui.autodetect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venunair.warden.data.PendingAutoDetectSuggestion
import com.venunair.warden.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AutoDetectSuggestionsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val suggestions: StateFlow<List<PendingAutoDetectSuggestion>> = repository.preferences
        .map { it.pendingAutoDetectSuggestions }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Removes one suggestion from the queue -- called for BOTH outcomes
     *  (added to the database, or dismissed as not a receipt). The caller
     *  decides which by whether it also invokes onAddSuggestion; either way
     *  the underlying fingerprint stays recorded in
     *  autoDetectSuggestedFingerprints (AutoDetectWorker), so this exact
     *  photo is never re-suggested regardless of which button was tapped. */
    fun consume(fingerprint: String) {
        viewModelScope.launch { repository.removePendingAutoDetectSuggestion(fingerprint) }
    }
}
