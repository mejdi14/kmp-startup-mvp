package com.mejdi14.kmpstartupmvp.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Port of the Flutter `Auth` notifier (PORTING.md §1.2) minus PostHog (task 12),
 * the dead OAuth method (task 10) and `currentSession`. App-lifetime (keepAlive):
 * construct once with an app-scoped [scope].
 */
class AuthStore(
    private val repository: AuthRepository,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AsyncValue<AuthSession?>>(AsyncValue.Loading)

    /** Loading until the startup session restore resolves, then the current session (or null). */
    val state: StateFlow<AsyncValue<AuthSession?>> = _state.asStateFlow()

    private val _gate = MutableStateFlow(AuthGate.Unknown)

    /**
     * D-002 gate. A [StateFlow] only emits on change, so token refreshes (which
     * update [state] but keep the user logged in) never re-emit here.
     */
    val authGate: StateFlow<AuthGate> = _gate.asStateFlow()

    private val _pendingPasswordReset = MutableStateFlow(false)

    /** True after a recovery link signed the user in, until the new-password dialog is dismissed. */
    val pendingPasswordReset: StateFlow<Boolean> = _pendingPasswordReset.asStateFlow()

    init {
        scope.launch {
            repository.authStatus
                .catch {
                    _state.value = AsyncValue.Error(it)
                    _gate.value = AuthGate.from(_state.value)
                }
                .collect { status ->
                    when (status) {
                        // Startup: keep Loading; the restored-or-null result arrives exactly once after this.
                        AuthStatus.Initializing -> Unit
                        AuthStatus.SignedOut -> _state.value = AsyncValue.Data(null)
                        is AuthStatus.SignedIn -> _state.value = AsyncValue.Data(status.session)
                    }
                    _gate.value = AuthGate.from(_state.value)
                }
        }
    }

    suspend fun signInWithPassword(email: String, password: String) =
        repository.signInWithPassword(email, password)

    /** Email confirmation is on (§2.8): this returns with no session; show "check your email". */
    suspend fun signUp(email: String, password: String) = repository.signUp(email, password)

    suspend fun recoverPassword(email: String, redirectUrl: String? = null) =
        repository.recoverPassword(email, redirectUrl)

    suspend fun updatePassword(password: String) = repository.updatePassword(password)

    /**
     * Handles an incoming D-003 deep link. A valid recovery link exchanges its code for a
     * session and raises [pendingPasswordReset]; a rejected link throws for the caller to surface.
     * Links that are not ours are ignored.
     */
    suspend fun handleDeepLink(url: String) {
        when (val link = DeepLinks.parse(url)) {
            is DeepLink.PasswordReset -> {
                repository.exchangeCodeForSession(link.code)
                _pendingPasswordReset.value = true
            }
            is DeepLink.LinkError -> throw IllegalStateException(link.message)
            null -> Unit
        }
    }

    fun dismissPasswordReset() {
        _pendingPasswordReset.value = false
    }

    suspend fun signOut() = repository.signOut()
}
