package com.mejdi14.kmpstartupmvp.analytics

import com.mejdi14.kmpstartupmvp.nav.Routes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenNamesTest {
    @Test
    fun routesMapToFlutterPaths() {
        assertEquals("/", ScreenNames.forRoute(Routes.HOME))
        assertEquals("/login", ScreenNames.forRoute(Routes.LOGIN))
        assertEquals("/loading", ScreenNames.forRoute(Routes.LOADING))
        assertEquals("/reset", ScreenNames.forRoute(Routes.RESET))
        assertEquals("/payments", ScreenNames.forRoute(Routes.PAYMENTS))
    }

    @Test
    fun paymentsPatternAndQueryCollapseToPayments() {
        assertEquals("/payments", ScreenNames.forRoute(Routes.PAYMENTS_PATTERN))
        assertEquals("/payments", ScreenNames.forRoute(Routes.payments("price_1")))
    }

    @Test
    fun unknownOrNullIsNotTracked() {
        assertNull(ScreenNames.forRoute(null))
        assertNull(ScreenNames.forRoute("other"))
    }
}
