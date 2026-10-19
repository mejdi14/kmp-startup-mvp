package com.mejdi14.kmpstartupmvp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mejdi14.kmpstartupmvp.auth.AuthStore
import kotlinx.coroutines.launch

/**
 * Thin Android adapter over the app-scoped shared [AuthStore] (D-001). State is
 * exposed straight from the store; actions run in [viewModelScope] and report
 * failures through callbacks so screens stay free of coroutine plumbing.
 */
class AuthViewModel(private val store: AuthStore) : ViewModel() {
    val state = store.state
    val authGate = store.authGate

    fun signInWithPassword(email: String, password: String, onResult: (Result<Unit>) -> Unit = {}) =
        run(onResult) { store.signInWithPassword(email, password) }

    fun signUp(email: String, password: String, onResult: (Result<Unit>) -> Unit = {}) =
        run(onResult) { store.signUp(email, password) }

    fun recoverPassword(email: String, onResult: (Result<Unit>) -> Unit = {}) =
        run(onResult) { store.recoverPassword(email) }

    fun updatePassword(password: String, onResult: (Result<Unit>) -> Unit = {}) =
        run(onResult) { store.updatePassword(password) }

    fun signOut(onResult: (Result<Unit>) -> Unit = {}) = run(onResult) { store.signOut() }

    private fun run(onResult: (Result<Unit>) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch { onResult(runCatching { block() }) }
    }

    companion object {
        fun factory(store: AuthStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(store) }
        }
    }
}
