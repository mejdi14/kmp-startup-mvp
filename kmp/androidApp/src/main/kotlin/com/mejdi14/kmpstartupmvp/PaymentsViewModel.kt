package com.mejdi14.kmpstartupmvp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mejdi14.kmpstartupmvp.payments.PaymentsStore

/** Thin Android adapter over the app-scoped shared [PaymentsStore] (D-001). */
class PaymentsViewModel(private val store: PaymentsStore) : ViewModel() {
    val pendingRequest = store.pendingRequest

    suspend fun getStripeUrl(price: String?): String = store.getStripeUrl(price)
    fun markLaunched(price: String?) = store.markLaunched(price)
    fun onReturned() = store.onReturned()
    fun consumeRequest() = store.consumeRequest()

    companion object {
        fun factory(store: PaymentsStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { PaymentsViewModel(store) }
        }
    }
}
