package com.mejdi14.kmpstartupmvp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mejdi14.kmpstartupmvp.metadata.MetadataStore

/** Thin Android adapter over the app-scoped shared [MetadataStore] (D-001). */
class MetadataViewModel(private val store: MetadataStore) : ViewModel() {
    val state = store.state

    fun refresh() = store.refresh()

    companion object {
        fun factory(store: MetadataStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { MetadataViewModel(store) }
        }
    }
}
