package com.mejdi14.kmpstartupmvp.auth

/** D-003 custom-scheme deep links. Registered per platform in task 9; later tasks add paths under it. */
object DeepLinks {
    const val SCHEME = "com.devtodollars.app"
    const val HOST = "login-callback"
    const val BASE_URL = "$SCHEME://$HOST"

    /** `redirect_to` for the GitHub browser sign-in (task 10); must be in the §2.8 allow-list. */
    const val OAUTH_REDIRECT_URL = BASE_URL

    /**
     * `redirect_to` for the confirmation email sent when a guest account is converted (D-010).
     * Same URL as [OAUTH_REDIRECT_URL] on purpose: the link carries a PKCE `code` on the bare host,
     * so [parse] already classifies it as [DeepLink.OAuthCallback] and the existing exchange applies.
     */
    const val EMAIL_CONFIRM_REDIRECT_URL = BASE_URL

    /** `redirect_to` for password-recovery emails; must be in the §2.8 allow-list. */
    const val RESET_REDIRECT_URL = "$BASE_URL/reset"

    /** Stripe `return_url` (D-005): Checkout success/cancel and billing-portal return all land here (§2.8). */
    const val STRIPE_RETURN_URL = "$BASE_URL/stripe-return"

    /** Deep link that opens Checkout for a price, e.g. `$BASE_URL/payments?price=price_XXX` (task 11). */
    fun paymentsUrl(price: String): String = "$BASE_URL/payments?price=$price"

    /** Classifies an incoming URL; null when it is not one of ours. */
    fun parse(url: String): DeepLink? {
        val prefix = "$SCHEME://$HOST"
        if (!url.startsWith(prefix, ignoreCase = true)) return null
        val rest = url.substring(prefix.length)
        // Only "", "/..." , "?..." or "#..." may follow the host.
        if (rest.isNotEmpty() && rest[0] !in "/?#") return null

        val hashAt = rest.indexOf('#').let { if (it < 0) rest.length else it }
        val beforeFragment = rest.substring(0, hashAt)
        val fragment = rest.substring(minOf(hashAt + 1, rest.length))
        val queryAt = beforeFragment.indexOf('?')
        val path = if (queryAt < 0) beforeFragment else beforeFragment.substring(0, queryAt)
        val query = if (queryAt < 0) "" else beforeFragment.substring(queryAt + 1)
        // GoTrue reports errors in the fragment, PKCE codes in the query; read both.
        val params = parseParams(query) + parseParams(fragment)

        val route = path.trimEnd('/')
        if (route.isEmpty()) {
            params["error_description"]?.let { return DeepLink.LinkError(it) }
            params["error"]?.let { return DeepLink.LinkError(it) }
            val code = params["code"]?.takeIf { it.isNotEmpty() } ?: return null
            return DeepLink.OAuthCallback(code)
        }
        if (route == "/stripe-return") return DeepLink.StripeReturn
        if (route == "/payments") return DeepLink.OpenPayments(params["price"]?.takeIf { it.isNotEmpty() })
        if (route != "/reset") return null
        params["error_description"]?.let { return DeepLink.LinkError(it) }
        params["error"]?.let { return DeepLink.LinkError(it) }
        val code = params["code"]?.takeIf { it.isNotEmpty() } ?: return DeepLink.LinkError("Missing reset code")
        return DeepLink.PasswordReset(code)
    }

    private fun parseParams(s: String): Map<String, String> =
        s.split('&').filter { it.isNotEmpty() }.associate {
            val eq = it.indexOf('=')
            if (eq < 0) percentDecode(it) to "" else percentDecode(it.substring(0, eq)) to percentDecode(it.substring(eq + 1))
        }

    private fun percentDecode(s: String): String {
        if ('%' !in s && '+' !in s) return s
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '+' -> { bytes += ' '.code.toByte(); i++ }
                c == '%' && i + 2 < s.length && s.substring(i + 1, i + 3).toIntOrNull(16) != null -> {
                    bytes += s.substring(i + 1, i + 3).toInt(16).toByte(); i += 3
                }
                else -> { bytes += c.toString().encodeToByteArray().toList(); i++ }
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}

sealed interface DeepLink {
    /** Recovery link carrying a PKCE auth [code] to exchange for a session. */
    data class PasswordReset(val code: String) : DeepLink

    /** OAuth redirect (GitHub) carrying a PKCE auth [code] to exchange for a session. */
    data class OAuthCallback(val code: String) : DeepLink

    /** Stripe handed control back (success, cancel or portal exit); the outcome is learned by polling (D-004). */
    data object StripeReturn : DeepLink

    /** Request to open Payments: Checkout for [price], or the billing portal when null. */
    data class OpenPayments(val price: String?) : DeepLink

    /** Link GoTrue rejected (e.g. expired). */
    data class LinkError(val message: String) : DeepLink
}
