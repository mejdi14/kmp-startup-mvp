package com.mejdi14.kmpstartupmvp.data

import com.mejdi14.kmpstartupmvp.auth.AuthRepository
import com.mejdi14.kmpstartupmvp.auth.AuthSession
import com.mejdi14.kmpstartupmvp.auth.AuthStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * Thin wrapper over the SDK so stores never touch it directly.
 */
class SupabaseRepository(val client: SupabaseClient) : AuthRepository {
    /**
     * Maps SDK status to [AuthStatus]. A refresh failure keeps the stored session
     * (the SDK retries), so it is dropped rather than treated as a sign-out.
     */
    override val authStatus: Flow<AuthStatus> =
        client.auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Initializing -> AuthStatus.Initializing
                is SessionStatus.NotAuthenticated -> AuthStatus.SignedOut
                is SessionStatus.Authenticated -> AuthStatus.SignedIn(
                    AuthSession(
                        userId = status.session.user?.id.orEmpty(),
                        email = status.session.user?.email,
                        accessToken = status.session.accessToken,
                    )
                )
                is SessionStatus.RefreshFailure -> null
            }
        }.filterNotNull()

    override suspend fun signInWithPassword(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signUp(email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun recoverPassword(email: String, redirectUrl: String?) {
        client.auth.resetPasswordForEmail(email, redirectUrl)
    }

    override suspend fun updatePassword(password: String) {
        client.auth.updateUser { this.password = password }
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    /** Unauthenticated read of the public `products` table (PORTING.md §2.1). */
    suspend fun fetchProductsRaw(): String =
        client.from("products").select().data
}
