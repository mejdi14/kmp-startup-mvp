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
    suspend fun signOut()
}
