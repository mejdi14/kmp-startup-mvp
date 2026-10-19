package com.mejdi14.kmpstartupmvp.nav

import com.mejdi14.kmpstartupmvp.auth.AuthGate

/** Route table from PORTING.md §1.6; names match Flutter's so analytics (task 12) stays aligned. */
object Routes {
    const val LOADING = "loading"
    const val LOGIN = "login"
    const val HOME = "home"
    const val RESET = "reset"
    const val PAYMENTS = "payments"

    /** Where the D-002 auth gate sends the user. */
    fun forGate(gate: AuthGate): String = when (gate) {
        AuthGate.Unknown -> LOADING
        AuthGate.LoggedOut -> LOGIN
        AuthGate.LoggedIn -> HOME
    }
}
