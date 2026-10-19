package com.mejdi14.kmpstartupmvp.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Port of the Flutter `Auth` notifier (PORTING.md §1.2) minus PostHog (task 12),
 * and `currentSession`. App-lifetime (keepAlive):
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

    // @Throws is what makes a Kotlin exception cross into Swift as an NSError. Without it the
    // ObjC export treats any non-CancellationException as fatal and aborts the process, so a
    // wrong password or an offline device would kill the iOS app instead of showing a message.
    @Throws(Throwable::class)
    suspend fun signInWithPassword(email: String, password: String) =
        repository.signInWithPassword(email, password)

    /** Email confirmation is on (§2.8): this returns with no session; show "check your email". */
    @Throws(Throwable::class)
    suspend fun signUp(email: String, password: String) = repository.signUp(email, password)

    @Throws(Throwable::class)
    suspend fun recoverPassword(email: String, redirectUrl: String? = null) =
        repository.recoverPassword(email, redirectUrl)

    @Throws(Throwable::class)
    suspend fun updatePassword(password: String) = repository.updatePassword(password)

    /**
     * Handles an incoming D-003 deep link. A valid recovery link exchanges its code for a
     * session and raises [pendingPasswordReset]; a rejected link throws for the caller to surface.
     * Links that are not ours are ignored.
     */
    @Throws(Throwable::class)
    suspend fun handleDeepLink(url: String) {
        when (val link = DeepLinks.parse(url)) {
            is DeepLink.PasswordReset -> {
                repository.exchangeCodeForSession(link.code)
                _pendingPasswordReset.value = true
            }
            is DeepLink.OAuthCallback -> repository.exchangeCodeForSession(link.code)
            // Payments links are routed by the PaymentsStore, not auth.
            DeepLink.StripeReturn, is DeepLink.OpenPayments -> Unit
            is DeepLink.LinkError -> throw IllegalStateException(link.message)
            null -> Unit
        }
    }

    fun dismissPasswordReset() {
        _pendingPasswordReset.value = false
    }

    /** Native Google/Apple sign-in; [nonce] is the raw nonce whose hash was sent to the provider. */
    @Throws(Throwable::class)
    suspend fun signInWithIdToken(provider: IdTokenProvider, idToken: String, nonce: String? = null) =
        repository.signInWithIdToken(provider, idToken, nonce)

    /** Starts the GitHub browser flow; completion arrives through [handleDeepLink]. */
    @Throws(Throwable::class)
    suspend fun signInWithGithub() =
        repository.signInWithOAuth(OAuthProvider.Github, DeepLinks.OAUTH_REDIRECT_URL)

    /**
     * "Continue without login" (D-009): signs in anonymously, so [authGate] flips to
     * [AuthGate.LoggedIn] like any other sign-in and the rest of the app needs no guest
     * special-casing. The session is disposable — signing out cannot be undone, because
     * an anonymous user has no credentials to sign back in with.
     */
    @Throws(Throwable::class)
    suspend fun continueAsGuest() = repository.signInAnonymously()

    @Throws(Throwable::class)
    suspend fun signOut() = repository.signOut()
}
