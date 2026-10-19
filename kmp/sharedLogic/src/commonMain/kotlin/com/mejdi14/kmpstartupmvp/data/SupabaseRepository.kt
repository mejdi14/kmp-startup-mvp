package com.mejdi14.kmpstartupmvp.data

import com.mejdi14.kmpstartupmvp.auth.AccountConversion
import com.mejdi14.kmpstartupmvp.auth.AuthRepository
import com.mejdi14.kmpstartupmvp.auth.AuthSession
import com.mejdi14.kmpstartupmvp.auth.AuthStatus
import com.mejdi14.kmpstartupmvp.auth.EmailAlreadyRegisteredException
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import com.mejdi14.kmpstartupmvp.auth.IdTokenProvider
import com.mejdi14.kmpstartupmvp.auth.OAuthProvider
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.status.SessionStatus
import com.mejdi14.kmpstartupmvp.metadata.MetadataRepository
import com.mejdi14.kmpstartupmvp.metadata.SubscriptionWithPrice
import com.mejdi14.kmpstartupmvp.metadata.UserMetadata
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import com.mejdi14.kmpstartupmvp.payments.PaymentsRepository
import com.mejdi14.kmpstartupmvp.payments.StripeException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Thin wrapper over the SDK so stores never touch it directly.
 */
class SupabaseRepository(val client: SupabaseClient) : AuthRepository, MetadataRepository, PaymentsRepository {
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
                        isAnonymous = status.session.user?.isAnonymous == true,
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

    override suspend fun signInAnonymously() {
        client.auth.signInAnonymously()
    }

    /**
     * D-010. `updateUser` on an anonymous user adds the credentials in place, keeping the user id.
     * The default `updateCurrentUser = true` writes the returned user back into the stored session,
     * so the `isAnonymous` flag on the session stream follows without a re-fetch.
     *
     * With email confirmation on (§2.8) GoTrue keeps the old (empty) email and reports the address
     * as `newEmail` until the link is followed, which is exactly the pending case; the password is
     * live immediately either way.
     */
    override suspend fun convertAnonymousUser(
        email: String,
        password: String,
        redirectUrl: String?,
    ): AccountConversion {
        val user = try {
            client.auth.updateUser(redirectUrl = redirectUrl) {
                this.email = email
                this.password = password
            }
        } catch (e: AuthRestException) {
            // GoTrue reports a taken address as email_exists, or user_already_exists on older versions.
            if (e.errorCode == AuthErrorCode.EmailExists || e.errorCode == AuthErrorCode.UserAlreadyExists) {
                throw EmailAlreadyRegisteredException(email)
            }
            throw e
        }
        return if (user.email.equals(email, ignoreCase = true)) {
            AccountConversion.Complete
        } else {
            AccountConversion.ConfirmationRequired(email)
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

    override suspend fun getStripeUrl(price: String?, returnUrl: String): String {
        val response = try {
            // Body shape is fixed by §2.3: `price` (not price_id) and `return_url`; null price → portal.
            client.functions.invoke("get_stripe_url", buildJsonObject {
                if (price != null) put("price", price)
                put("return_url", returnUrl)
            })
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestException) {
            // Every failure is a 400 `{"message": ...}` (§2.3).
            throw StripeException(serverMessage(e.description.orEmpty()) ?: "Error retrieving stripe redirect url", e)
        } catch (e: Exception) {
            throw StripeException(e.message ?: "Error retrieving stripe redirect url", e)
        }
        val url = try {
            response.body<StripeUrlResponse>().redirectUrl
        } catch (e: Exception) {
            null
        }
        return url?.takeIf { it.isNotBlank() } ?: throw StripeException("Error retrieving stripe redirect url")
    }

    private fun serverMessage(body: String): String? = runCatching {
        Json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
    }.getOrNull() ?: body.takeIf { it.isNotBlank() }
}

@Serializable
private data class StripeUrlResponse(@SerialName("redirect_url") val redirectUrl: String? = null)
