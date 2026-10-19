package com.mejdi14.kmpstartupmvp.auth

import kotlinx.coroutines.flow.Flow

/** Auth operations the [AuthStore] needs; implemented over supabase-kt, faked in tests. */
interface AuthRepository {
    /** Replays the current status, starting at [AuthStatus.Initializing]. */
    val authStatus: Flow<AuthStatus>

    suspend fun signInWithPassword(email: String, password: String)
    suspend fun signUp(email: String, password: String)
    suspend fun recoverPassword(email: String, redirectUrl: String? = null)
    suspend fun updatePassword(password: String)
    /** Completes a PKCE flow from a deep link's auth code, establishing a session. */
    suspend fun exchangeCodeForSession(code: String)
    /** Native SDK sign-in (D-008): exchanges a provider ID token for a session. */
    suspend fun signInWithIdToken(provider: IdTokenProvider, idToken: String, nonce: String? = null)
    /** Browser-redirect sign-in (D-008, GitHub): opens the provider page; the session returns via the D-003 deep link. */
    suspend fun signInWithOAuth(provider: OAuthProvider, redirectUrl: String)
    /**
     * Guest access (D-009): establishes a real but anonymous session, so the auth gate,
     * the `users` row trigger and every `auth.uid()` RLS policy keep working unchanged.
     */
    suspend fun signInAnonymously()
    /**
     * Account conversion (D-010): sets [email] and [password] on the *current* anonymous user, so
     * the user id survives and nothing that hangs off it has to be migrated. Throws
     * [EmailAlreadyRegisteredException] when the address belongs to another account, and leaves the
     * session signed in either way. [redirectUrl] is where a confirmation email should return.
     */
    suspend fun convertAnonymousUser(
        email: String,
        password: String,
        redirectUrl: String? = null,
    ): AccountConversion
    suspend fun signOut()
}

/**
 * The conversion target address already belongs to an account (D-010). There is nothing to fall
 * back to: Supabase cannot fold a guest user into an existing one, and this app has no server-side
 * merge, so [message] says so instead of implying the data moved.
 */
class EmailAlreadyRegisteredException(val email: String) : Exception(
    "$email already belongs to an account. We can't move this guest session into it, so sign in to " +
        "that account instead — anything saved here will stay behind. Or save this session under a " +
        "different email.",
)
