package com.mejdi14.kmpstartupmvp.nav

import com.mejdi14.kmpstartupmvp.auth.AuthGate
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutesTest {
    @Test
    fun gateMapsToRoute() {
        assertEquals(Routes.LOADING, Routes.forGate(AuthGate.Unknown))
        assertEquals(Routes.LOGIN, Routes.forGate(AuthGate.LoggedOut))
        assertEquals(Routes.HOME, Routes.forGate(AuthGate.LoggedIn))
    }
}
