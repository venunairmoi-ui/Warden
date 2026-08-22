package com.venunair.warden.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venunair.warden.data.Item
import com.venunair.warden.data.ItemRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repository: ItemRepository) : ViewModel() {
    val items: StateFlow<List<Item>> = repository.observeActiveItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
