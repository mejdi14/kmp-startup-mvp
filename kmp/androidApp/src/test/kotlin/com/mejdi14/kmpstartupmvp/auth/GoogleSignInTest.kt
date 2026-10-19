package com.mejdi14.kmpstartupmvp.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GoogleSignInTest {
    @Test
    fun sha256HexMatchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", GoogleSignIn.sha256Hex("abc"))
    }

    @Test
    fun noncesAreLongHexAndUnique() {
        val a = GoogleSignIn.newNonce()
        assertEquals(64, a.length)
        assertNotEquals(a, GoogleSignIn.newNonce())
    }
}
