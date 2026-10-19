package com.mejdi14.kmpstartupmvp.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.security.SecureRandom

/** A Google ID token plus the raw nonce Supabase must verify it against. */
data class GoogleIdTokenResult(val idToken: String, val rawNonce: String)

/** Native Google Sign-In through Credential Manager (D-008). */
object GoogleSignIn {
    suspend fun getIdToken(activityContext: Context, webClientId: String): GoogleIdTokenResult {
        check(webClientId.isNotBlank()) {
            "Google sign-in is not configured: set GOOGLE_WEB_CLIENT_ID (see androidApp/build.gradle.kts)"
        }
        val rawNonce = newNonce()
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setNonce(sha256Hex(rawNonce))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type from Google sign-in"
        }
        return GoogleIdTokenResult(GoogleIdTokenCredential.createFrom(credential.data).idToken, rawNonce)
    }

    /** Supabase gets the raw nonce; Google gets its SHA-256 hex digest. */
    internal fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    internal fun newNonce(): String =
        ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
}
