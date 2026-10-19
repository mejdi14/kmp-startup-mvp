package com.mejdi14.kmpstartupmvp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mejdi14.kmpstartupmvp.payments.PaymentsStore
import com.mejdi14.kmpstartupmvp.ui.logFailure
import kotlinx.coroutines.CancellationException

/** Thin Android adapter over the app-scoped shared [PaymentsStore] (D-001). */
class PaymentsViewModel(private val store: PaymentsStore) : ViewModel() {
    val pendingRequest = store.pendingRequest

    /** Throws for the Payments screen to show; the raw failure is logged on the way out (D-010). */
    suspend fun getStripeUrl(price: String?): String = try {
        store.getStripeUrl(price)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logFailure(e)
        throw e
    }

    fun markLaunched(price: String?) = store.markLaunched(price)
    fun onReturned() = store.onReturned()
    fun consumeRequest() = store.consumeRequest()

    companion object {
        fun factory(store: PaymentsStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { PaymentsViewModel(store) }
        }
    }
}
