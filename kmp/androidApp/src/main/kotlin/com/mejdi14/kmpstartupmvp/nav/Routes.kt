package com.mejdi14.kmpstartupmvp.nav

import com.mejdi14.kmpstartupmvp.auth.AuthGate

/** Route table from PORTING.md §1.6; names match Flutter's so analytics (task 12) stays aligned. */
object Routes {
    const val LOADING = "loading"
    const val LOGIN = "login"
    const val HOME = "home"
    const val RESET = "reset"
    const val ACCOUNT = "account"
    const val PAYMENTS = "payments"

    /** Nav pattern for Payments; `price` is optional (absent → billing portal). */
    const val PAYMENTS_PATTERN = "$PAYMENTS?price={price}"
    const val PRICE_ARG = "price"

    fun payments(price: String?): String =
        if (price.isNullOrBlank()) PAYMENTS else "$PAYMENTS?price=${price.trim()}"

    /** Where the D-002 auth gate sends the user. */
    fun forGate(gate: AuthGate): String = when (gate) {
        AuthGate.Unknown -> LOADING
        AuthGate.LoggedOut -> LOGIN
        AuthGate.LoggedIn -> HOME
    }
}
