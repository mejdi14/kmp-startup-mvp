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
    suspend fun signOut()
}
