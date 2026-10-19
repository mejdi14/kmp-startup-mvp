package com.mejdi14.kmpstartupmvp.metadata

/** Client-side checks for the profile form; the `users` columns themselves are unconstrained text. */
object ProfileValidation {
    const val MAX_NAME_LENGTH = 100

    fun fullName(name: String): String? =
        if (name.trim().length > MAX_NAME_LENGTH) "Name must be at most $MAX_NAME_LENGTH characters" else null

    /** Empty is fine (clears the avatar); otherwise it must be an http(s) URL. */
    fun avatarUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        val ok = Regex("""^https?://[^\s/$.?#][^\s]*$""", RegexOption.IGNORE_CASE).matches(trimmed)
        return if (ok) null else "Enter a valid http(s) URL"
    }
}
