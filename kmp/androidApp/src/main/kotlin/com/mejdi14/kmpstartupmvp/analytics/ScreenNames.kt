package com.mejdi14.kmpstartupmvp.analytics

import com.mejdi14.kmpstartupmvp.nav.Routes

/**
 * Screen-view names matching Flutter's `PosthogObserver`, which reports route paths
 * (`/`, `/login`, `/payments`, `/loading`, `/reset`; PORTING.md §1.5). `account` has no
 * Flutter counterpart and follows the same path style.
 */
object ScreenNames {
    fun forRoute(route: String?): String? {
        val base = route?.substringBefore('?') ?: return null
        return when (base) {
            Routes.HOME -> "/"
            Routes.LOGIN -> "/login"
            Routes.PAYMENTS -> "/payments"
            Routes.LOADING -> "/loading"
            Routes.RESET -> "/reset"
            Routes.ACCOUNT -> "/account"
            else -> null
        }
    }
}
