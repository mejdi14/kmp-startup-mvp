package com.mejdi14.kmpstartupmvp

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mejdi14.kmpstartupmvp.auth.AuthStore
import com.mejdi14.kmpstartupmvp.auth.DeepLinks
import com.mejdi14.kmpstartupmvp.auth.GoogleSignIn
import com.mejdi14.kmpstartupmvp.auth.IdTokenProvider
import com.mejdi14.kmpstartupmvp.ui.logFailure
import kotlinx.coroutines.launch

/**
 * Thin Android adapter over the app-scoped shared [AuthStore] (D-001). State is
 * exposed straight from the store; actions run in [viewModelScope] and report
 * failures through callbacks so screens stay free of coroutine plumbing.
 */
class AuthViewModel(private val store: AuthStore) : ViewModel() {
    val state = store.state
    val authGate = store.authGate
    val pendingPasswordReset = store.pendingPasswordReset

    fun signInWithPassword(email: String, password: String, onResult: (Result<Unit>) -> Unit = {}) =
        run(onResult) { store.signInWithPassword(email, password) }

    /** Native Google sign-in (D-008): [activityContext] must be an Activity so the account picker can show. */
    fun signInWithGoogle(activityContext: Context, onResult: (Result<Unit>) -> Unit = {}) = run(onResult) {
        val google = GoogleSignIn.getIdToken(activityContext, googleWebClientId)
        store.signInWithIdToken(IdTokenProvider.Google, google.idToken, google.rawNonce)
    }

    /** GitHub browser flow; success returns through the deep link, so [onResult] only covers launching. */
    fun signInWithGithub(onResult: (Result<Unit>) -> Unit = {}) = run(onResult) { store.signInWithGithub() }

    fun signUp(email: String, password: String, onResult: (Result<Unit>) -> Unit = {}) =
        run(onResult) { store.signUp(email, password) }

    /** "Continue without login" (D-009): anonymous session, so the auth gate navigates as usual. */
    fun continueAsGuest(onResult: (Result<Unit>) -> Unit = {}) = run(onResult) { store.continueAsGuest() }

    fun recoverPassword(email: String, onResult: (Result<Unit>) -> Unit = {}) =
        run(onResult) { store.recoverPassword(email, DeepLinks.RESET_REDIRECT_URL) }

    fun updatePassword(password: String, onResult: (Result<Unit>) -> Unit = {}) =
        run(onResult) { store.updatePassword(password) }

    fun dismissPasswordReset() = store.dismissPasswordReset()

    /** Feeds an incoming D-003 deep link to the store; failures (e.g. expired link) go to [onResult]. */
    fun handleDeepLink(url: String, onResult: (Result<Unit>) -> Unit = {}) =
        run(onResult) { store.handleDeepLink(url) }

    fun signOut(onResult: (Result<Unit>) -> Unit = {}) = run(onResult) { store.signOut() }

    private fun run(onResult: (Result<Unit>) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            // Logged here, the one place every action funnels through (D-010): several call sites
            // pass no callback, so without this their failures would disappear entirely.
            onResult(runCatching { block() }.onFailure(::logFailure))
        }
    }

    companion object {
        fun factory(store: AuthStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(store) }
        }
    }
}
