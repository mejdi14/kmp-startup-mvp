package com.mejdi14.kmpstartupmvp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mejdi14.kmpstartupmvp.metadata.MetadataStore
import kotlinx.coroutines.launch

/** Thin Android adapter over the app-scoped shared [MetadataStore] (D-001). */
class MetadataViewModel(private val store: MetadataStore) : ViewModel() {
    val state = store.state

    fun refresh() = store.refresh()

    fun updateProfile(fullName: String, avatarUrl: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onResult(runCatching { store.updateProfile(fullName, avatarUrl) }) }
    }

    companion object {
        fun factory(store: MetadataStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { MetadataViewModel(store) }
        }
    }
}
