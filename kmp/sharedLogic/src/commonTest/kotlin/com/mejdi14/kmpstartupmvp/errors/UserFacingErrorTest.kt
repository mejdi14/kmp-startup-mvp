package com.mejdi14.kmpstartupmvp.errors

import com.mejdi14.kmpstartupmvp.auth.EmailAlreadyRegisteredException
import com.mejdi14.kmpstartupmvp.payments.GuestCheckoutException
import com.mejdi14.kmpstartupmvp.payments.StripeException
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.InvalidJwtException
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.exception.SessionRequiredException
import io.github.jan.supabase.auth.exception.TokenExpiredException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * D-010. The `AuthRestException` / `RestException` family cannot be built without a real
 * Ktor `HttpResponse`, so those cases are covered through [authErrorKind] / [httpStatusKind] —
 * the same functions the throwable path delegates to — and the rest through real exceptions.
 */
class UserFacingErrorTest {

    // ---- the failure the user hit most: no backend reachable --------------------------------

    @Test
    fun transportFailureReadsAsAnOfflineSentence() {
        // Exactly what supabase-kt throws when nothing is listening on the Supabase URL.
        val raw = "Exception in http request: Error Domain=NSURLErrorDomain Code=-1004 " +
            "\"Could not connect to the server.\" _kCFStreamErrorCodeKey=61"
        val error = HttpRequestException(raw, HttpRequestBuilder()).toUserFacingError()

        assertEquals(ErrorKind.Offline, error.kind)
        assertEquals("No connection. Check your internet and try again.", error.message)
        // The dump the user used to see must not be in the message any more...
        assertFalse("NSURLErrorDomain" in error.message)
        assertFalse("localhost" in error.message)
        // ...but it must still be there for whoever debugs it.
        assertTrue("NSURLErrorDomain" in error.technicalDetail)
        assertTrue("HttpRequestException" in error.technicalDetail)
    }

    @Test
    fun noMessageReadsAsTheGenericSentence() {
        val error = RuntimeException().toUserFacingError()
        assertEquals(ErrorKind.Unknown, error.kind)
        assertEquals("Something went wrong. Please try again.", error.message)
        assertEquals("RuntimeException", error.technicalDetail)
    }

    @Test
    fun technicalDetailKeepsTheCauseChainAndTheOriginalThrowable() {
        val cause = IllegalArgumentException("connect(2) failed: errno 61")
        val thrown = IllegalStateException("wrapper", cause)

        val error = thrown.toUserFacingError()

        assertSame(thrown, error.cause)
        assertEquals(
            "IllegalStateException: wrapper <- IllegalArgumentException: connect(2) failed: errno 61",
            error.technicalDetail,
        )
    }

    // ---- the typed, non-REST exceptions --------------------------------------------------

    @Test
    fun rejectedRecoveryLinkReadsAsAnExpiredLinkSentence() {
        // AuthStore throws this for a GoTrue `error_description` on a /reset deep link.
        val error = AuthLinkException("Email link is invalid or has expired").toUserFacingError()
        assertEquals(ErrorKind.ExpiredLink, error.kind)
        assertEquals("That link has expired or was already used. Please request a new one.", error.message)
    }

    @Test
    fun missingSessionExceptionsAllReadAsAnExpiredSession() {
        val expected = "Your session has expired. Please sign in again."
        assertEquals(expected, NoSessionFoundException().toUserFacingError().message)
        assertEquals(expected, SessionRequiredException("auth").toUserFacingError().message)
        assertEquals(expected, TokenExpiredException().toUserFacingError().message)
        assertEquals(expected, InvalidJwtException().toUserFacingError().message)
    }

    @Test
    fun stripeFailureHidesTheServerTextButKeepsIt() {
        val error = StripeException("Unable to access customer record.").toUserFacingError()
        assertEquals(ErrorKind.Payment, error.kind)
        assertEquals("We could not open the payment page. Please try again in a moment.", error.message)
        assertTrue("Unable to access customer record." in error.technicalDetail)
    }

    @Test
    fun takenEmailOnGuestConversionGetsItsOwnSentence() {
        // D-011: not the generic "Something went wrong", and not the sign-up "already registered" copy.
        val error = EmailAlreadyRegisteredException("taken@b.co").toUserFacingError()
        assertEquals(ErrorKind.GuestEmailTaken, error.kind)
        assertTrue("can't be moved into it" in error.message)
        assertTrue("taken@b.co" in error.technicalDetail)
    }

    @Test
    fun guestCheckoutAsksToSaveTheAccount() {
        val error = GuestCheckoutException().toUserFacingError()
        assertEquals(ErrorKind.GuestCheckout, error.kind)
        assertTrue("Save your account" in error.message)
    }

    // ---- GoTrue error codes ---------------------------------------------------------------

    @Test
    fun guestButtonWithAnonymousSignInsOffSaysSo() {
        // D-009's documented failure mode: `enable_anonymous_sign_ins = false`.
        val kind = authErrorKind(AuthErrorCode.AnonymousProviderDisabled, statusCode = 422)
        assertEquals(ErrorKind.GuestDisabled, kind)
        assertEquals(
            "Guest access is turned off for this app. Please sign in or create an account.",
            kind.userMessage,
        )
    }

    @Test
    fun representativeAuthErrorCodesMapToTheirCopy() {
        val cases = mapOf(
            AuthErrorCode.InvalidCredentials to "Incorrect email or password.",
            AuthErrorCode.EmailExists to "That email is already registered. Try signing in instead.",
            AuthErrorCode.UserAlreadyExists to "That email is already registered. Try signing in instead.",
            AuthErrorCode.EmailNotConfirmed to "Confirm your email first — check your inbox for the link.",
            AuthErrorCode.EmailAddressInvalid to "That email address doesn't look right.",
            AuthErrorCode.WeakPassword to "That password is too weak. Please choose a longer one.",
            AuthErrorCode.SamePassword to "That is already your current password. Please choose a different one.",
            AuthErrorCode.FlowStateExpired to "That link has expired or was already used. Please request a new one.",
            AuthErrorCode.BadCodeVerifier to "That link has expired or was already used. Please request a new one.",
            AuthErrorCode.OtpExpired to "That link has expired or was already used. Please request a new one.",
            AuthErrorCode.SignupDisabled to "New sign-ups are turned off right now.",
            AuthErrorCode.EmailProviderDisabled to "That sign-in method is turned off for this app.",
            AuthErrorCode.UserNotFound to "No account found for that email.",
            AuthErrorCode.UserBanned to "This account has been suspended.",
            AuthErrorCode.CaptchaFailed to "We could not verify that you are not a robot. Please try again.",
            AuthErrorCode.OverRequestRateLimit to "Too many attempts. Please wait a minute and try again.",
            AuthErrorCode.OverEmailSendRateLimit to "Too many attempts. Please wait a minute and try again.",
            AuthErrorCode.SessionExpired to "Your session has expired. Please sign in again.",
            AuthErrorCode.RefreshTokenAlreadyUsed to "Your session has expired. Please sign in again.",
            AuthErrorCode.UnexpectedFailure to "Something went wrong on our side. Please try again in a moment.",
        )
        for ((code, expected) in cases) {
            assertEquals(expected, authErrorKind(code, statusCode = 400).userMessage, "for $code")
        }
    }

    @Test
    fun anUnrecognisedCodeFallsBackToTheHttpStatus() {
        // MFA codes are not in the mapping table; a 429 alongside one still reads as rate limiting.
        assertEquals(ErrorKind.RateLimited, authErrorKind(AuthErrorCode.MfaChallengeExpired, statusCode = 429))
        assertEquals(ErrorKind.Server, authErrorKind(null, statusCode = 503))
        assertEquals(ErrorKind.Unknown, authErrorKind(null, statusCode = 400))
    }

    // ---- bare HTTP statuses, for REST failures with no code -------------------------------

    @Test
    fun httpStatusesMapToTheirCopy() {
        assertEquals(ErrorKind.NotAllowed, httpStatusKind(401))
        assertEquals(ErrorKind.NotAllowed, httpStatusKind(403))
        assertEquals(ErrorKind.NotFound, httpStatusKind(404))
        assertEquals(ErrorKind.Timeout, httpStatusKind(408))
        assertEquals(ErrorKind.RateLimited, httpStatusKind(429))
        assertEquals(ErrorKind.Server, httpStatusKind(500))
        assertEquals(ErrorKind.Server, httpStatusKind(502))
        assertEquals(ErrorKind.Unknown, httpStatusKind(418))
    }

    // ---- the copy itself -------------------------------------------------------------------

    @Test
    fun noMessageLeaksInternalVocabulary() {
        val forbidden = listOf(
            "http", "HTTP", "localhost", "NSURL", "Error Domain", "Exception",
            "null", "supabase", "Supabase", "token", "JWT", "jwt", "401", "500",
        )
        for (kind in ErrorKind.entries) {
            val message = kind.userMessage
            assertTrue(message.isNotBlank(), "$kind has no copy")
            assertTrue(message.trim().endsWith("."), "$kind should read as a sentence: $message")
            for (word in forbidden) {
                assertFalse(word in message, "$kind leaks \"$word\": $message")
            }
        }
    }
}
