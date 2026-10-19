package com.mejdi14.kmpstartupmvp.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkTest {
    @Test
    fun parsesResetCode() {
        assertEquals(
            DeepLink.PasswordReset("abc-123"),
            DeepLinks.parse("com.devtodollars.app://login-callback/reset?code=abc-123"),
        )
    }

    @Test
    fun parsesOAuthCallback() {
        assertEquals(DeepLink.OAuthCallback("g1"), DeepLinks.parse("com.devtodollars.app://login-callback?code=g1"))
        assertEquals(DeepLink.OAuthCallback("g1"), DeepLinks.parse("${DeepLinks.OAUTH_REDIRECT_URL}/?code=g1"))
        assertEquals(DeepLink.LinkError("denied"), DeepLinks.parse("com.devtodollars.app://login-callback#error_description=denied"))
        assertNull(DeepLinks.parse("com.devtodollars.app://login-callback"))
    }

    @Test
    fun resetRedirectUrlRoundTrips() {
        assertEquals(DeepLink.PasswordReset("x"), DeepLinks.parse("${DeepLinks.RESET_REDIRECT_URL}?code=x"))
    }

    @Test
    fun reportsGoTrueErrorFromFragment() {
        val link = DeepLinks.parse(
            "com.devtodollars.app://login-callback/reset#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired",
        )
        assertEquals(DeepLink.LinkError("Email link is invalid or has expired"), link)
    }

    @Test
    fun resetWithoutCodeIsAnError() {
        assertEquals(DeepLink.LinkError("Missing reset code"), DeepLinks.parse("com.devtodollars.app://login-callback/reset"))
    }

    @Test
    fun ignoresForeignAndOtherPaths() {
        assertNull(DeepLinks.parse("https://example.com/reset?code=1"))
        assertNull(DeepLinks.parse("com.devtodollars.app://other/reset?code=1"))
        assertNull(DeepLinks.parse("com.devtodollars.app://login-callbackx/reset?code=1"))
        assertNull(DeepLinks.parse("com.devtodollars.app://login-callback/payments?price=p1"))
    }
}
