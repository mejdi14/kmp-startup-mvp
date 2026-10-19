package com.mejdi14.kmpstartupmvp.auth

/** Sign-in vs sign-up toggle of the shared email form (Flutter `AuthAction`). */
enum class AuthAction { SignIn, SignUp }

/** Validation rules ported verbatim from Flutter's `email_form.dart` (§1.1). */
object EmailFormValidation {
    private val emailRegex = Regex("""^\S+@\S+\.\S+$""")

    fun email(email: String): String? = when {
        email.isEmpty() -> "Email is required"
        !emailRegex.matches(email) -> "Invalid email"
        else -> null
    }

    fun password(password: String): String? = when {
        password.isEmpty() -> "Password is required"
        password.length < 6 -> "Password must be at least 6 characters long"
        else -> null
    }

    fun confirmPassword(password: String, confirm: String): String? = when {
        confirm.isEmpty() -> "Password confirmation is required"
        confirm != password -> "Passwords do not match"
        else -> null
    }
}
