package com.mejdi14.kmpstartupmvp.auth

/** D-003 custom-scheme deep links. Registered per platform in task 9; later tasks add paths under it. */
object DeepLinks {
    const val SCHEME = "com.devtodollars.app"
    const val HOST = "login-callback"
    const val BASE_URL = "$SCHEME://$HOST"

    /** `redirect_to` for password-recovery emails; must be in the §2.8 allow-list. */
    const val RESET_REDIRECT_URL = "$BASE_URL/reset"

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

        if (path.trimEnd('/') != "/reset") return null
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

    /** Recovery link GoTrue rejected (e.g. expired). */
    data class LinkError(val message: String) : DeepLink
}
