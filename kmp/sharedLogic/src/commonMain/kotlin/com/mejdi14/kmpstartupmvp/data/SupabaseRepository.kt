package com.mejdi14.kmpstartupmvp.data

import com.mejdi14.kmpstartupmvp.auth.AuthRepository
import com.mejdi14.kmpstartupmvp.auth.AuthSession
import com.mejdi14.kmpstartupmvp.auth.AuthStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import com.mejdi14.kmpstartupmvp.auth.IdTokenProvider
import com.mejdi14.kmpstartupmvp.auth.OAuthProvider
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import com.mejdi14.kmpstartupmvp.metadata.MetadataRepository
import com.mejdi14.kmpstartupmvp.metadata.SubscriptionWithPrice
import com.mejdi14.kmpstartupmvp.metadata.UserMetadata
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * Thin wrapper over the SDK so stores never touch it directly.
 */
class SupabaseRepository(val client: SupabaseClient) : AuthRepository, MetadataRepository {
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

    override suspend fun exchangeCodeForSession(code: String) {
        client.auth.exchangeCodeForSession(code)
    }

    override suspend fun signInWithIdToken(provider: IdTokenProvider, idToken: String, nonce: String?) {
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = when (provider) {
                IdTokenProvider.Google -> Google
                IdTokenProvider.Apple -> Apple
            }
            this.nonce = nonce
        }
    }

    override suspend fun signInWithOAuth(provider: OAuthProvider, redirectUrl: String) {
        when (provider) {
            OAuthProvider.Github -> client.auth.signInWith(Github, redirectUrl)
        }
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    override suspend fun fetchUserDetails(): UserMetadata? =
        client.from("users").select().decodeSingleOrNull<UserMetadata>()

    override suspend fun updateProfile(fullName: String?, avatarUrl: String?) {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not signed in")
        // RLS already limits this to the caller's own row (§2.1); the filter keeps the request explicit.
        client.from("users").update({
            set("full_name", fullName)
            set("avatar_url", avatarUrl)
        }) { filter { eq("id", userId) } }
    }

    override suspend fun fetchSubscription(): SubscriptionWithPrice? =
        client.from("subscriptions").select(Columns.raw("*, prices(*, products(*))")) {
            filter { isIn("status", listOf("trialing", "active")) }
            order("created", Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull<SubscriptionWithPrice>()

    /** Unauthenticated read of the public `products` table (PORTING.md §2.1). */
    suspend fun fetchProductsRaw(): String =
        client.from("products").select().data
}
