package com.mejdi14.kmpstartupmvp.auth

/** Hand-written stand-in for Riverpod's `AsyncValue<T>` (D-001). */
sealed interface AsyncValue<out T> {
    data object Loading : AsyncValue<Nothing>
    data class Data<T>(val value: T) : AsyncValue<T>
    data class Error(val cause: Throwable) : AsyncValue<Nothing>
}

/** SDK-independent view of a signed-in session. */
data class AuthSession(
    val userId: String,
    val email: String?,
    val accessToken: String,
    /**
     * True for a guest signed in by `signInAnonymously` (D-009). Stays true until a conversion
     * (D-010) completes, so a screen can offer "Save your account" off the session stream alone.
     */
    val isAnonymous: Boolean = false,
)

/**
 * Outcome of turning a guest session into a permanent account (D-010). Either shape keeps the
 * same user id, so the `users` row, the subscription and the Stripe customer carry over.
 */
sealed interface AccountConversion {
    /** Email confirmation is off: the credentials work immediately and the session is no longer anonymous. */
    data object Complete : AccountConversion

    /**
     * Email confirmation is on: the password is set, but [email] is only pending until the
     * confirmation link is followed, and the session stays anonymous until then.
     */
    data class ConfirmationRequired(val email: String) : AccountConversion
}

/** Session lifecycle as reported by the repository (SDK types stay behind it). */
sealed interface AuthStatus {
    data object Initializing : AuthStatus
    data object SignedOut : AuthStatus
    data class SignedIn(val session: AuthSession) : AuthStatus
}

/** Providers signed in natively with an ID token (D-008). Apple is wired in task 13. */
enum class IdTokenProvider { Google, Apple }

/** Providers signed in through the browser redirect (D-008). */
enum class OAuthProvider { Github }

/** D-002: the only auth signal navigation should react to. */
enum class AuthGate {
    Unknown,
    LoggedOut,
    LoggedIn;

    companion object {
        fun from(state: AsyncValue<AuthSession?>): AuthGate = when (state) {
            is AsyncValue.Data -> if (state.value == null) LoggedOut else LoggedIn
            // An error before any session is known must not strand the user on the splash.
            is AsyncValue.Error -> LoggedOut
            AsyncValue.Loading -> Unknown
        }
    }
}
