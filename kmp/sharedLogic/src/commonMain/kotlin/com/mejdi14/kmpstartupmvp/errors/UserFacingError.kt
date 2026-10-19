package com.mejdi14.kmpstartupmvp.errors

import com.mejdi14.kmpstartupmvp.payments.StripeException
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.exception.InvalidJwtException
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.exception.SessionRequiredException
import io.github.jan.supabase.auth.exception.TokenExpiredException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException

/**
 * What went wrong, at the granularity the UI copy distinguishes (D-010). One kind per
 * distinct sentence shown to the user; failures that deserve the same sentence share a kind.
 */
enum class ErrorKind {
    Offline,
    Timeout,
    InvalidCredentials,
    EmailAlreadyRegistered,
    EmailNotConfirmed,
    InvalidEmail,
    WeakPassword,
    SamePassword,
    ExpiredLink,
    GuestDisabled,
    SignUpDisabled,
    ProviderDisabled,
    AccountNotFound,
    AccountBanned,
    CaptchaFailed,
    RateLimited,
    SessionExpired,
    NotAllowed,
    NotFound,
    Payment,
    Server,
    Unknown,
}

/**
 * A failure in the shape the UI needs (D-010). [message] is the only text shown to the user;
 * [technicalDetail] and [cause] carry the raw SDK failure for logs so nothing is lost.
 */
data class UserFacingError(
    val kind: ErrorKind,
    val message: String,
    val technicalDetail: String,
    val cause: Throwable?,
)

/** A deep link GoTrue rejected (expired or already-used recovery link, denied OAuth callback). */
class AuthLinkException(message: String) : Exception(message)

/**
 * Turns any throwable raised by the shared stores into something a non-technical person can act
 * on (D-010). Both platforms call this, so the copy is identical on Android and iOS; the raw Ktor
 * or NSURL text that used to reach the screen is kept in [UserFacingError.technicalDetail].
 */
fun Throwable.toUserFacingError(): UserFacingError {
    val kind = classifyError(this)
    return UserFacingError(
        kind = kind,
        message = kind.userMessage,
        technicalDetail = technicalDetail(),
        cause = this,
    )
}

/** The one sentence shown for each [ErrorKind]. No URLs, no status codes, no SDK vocabulary. */
val ErrorKind.userMessage: String
    get() = when (this) {
        ErrorKind.Offline -> "No connection. Check your internet and try again."
        ErrorKind.Timeout -> "The server took too long to respond. Please try again."
        ErrorKind.InvalidCredentials -> "Incorrect email or password."
        ErrorKind.EmailAlreadyRegistered -> "That email is already registered. Try signing in instead."
        ErrorKind.EmailNotConfirmed -> "Confirm your email first — check your inbox for the link."
        ErrorKind.InvalidEmail -> "That email address doesn't look right."
        ErrorKind.WeakPassword -> "That password is too weak. Please choose a longer one."
        ErrorKind.SamePassword -> "That is already your current password. Please choose a different one."
        ErrorKind.ExpiredLink -> "That link has expired or was already used. Please request a new one."
        ErrorKind.GuestDisabled -> "Guest access is turned off for this app. Please sign in or create an account."
        ErrorKind.SignUpDisabled -> "New sign-ups are turned off right now."
        ErrorKind.ProviderDisabled -> "That sign-in method is turned off for this app."
        ErrorKind.AccountNotFound -> "No account found for that email."
        ErrorKind.AccountBanned -> "This account has been suspended."
        ErrorKind.CaptchaFailed -> "We could not verify that you are not a robot. Please try again."
        ErrorKind.RateLimited -> "Too many attempts. Please wait a minute and try again."
        ErrorKind.SessionExpired -> "Your session has expired. Please sign in again."
        ErrorKind.NotAllowed -> "You are not allowed to do that. Please sign in again and retry."
        ErrorKind.NotFound -> "We could not find that. It may have been removed."
        ErrorKind.Payment -> "We could not open the payment page. Please try again in a moment."
        ErrorKind.Server -> "Something went wrong on our side. Please try again in a moment."
        ErrorKind.Unknown -> "Something went wrong. Please try again."
    }

/**
 * Keyed off typed fields wherever supabase-kt has one — [AuthRestException.errorCode] and
 * [RestException.statusCode] — never off message text, which changes between SDK and GoTrue
 * versions. [HttpRequestException] is what supabase-kt wraps every transport failure in
 * (its message is the "HTTP request to … failed with message: …" dump), so it is the offline case.
 */
internal fun classifyError(throwable: Throwable): ErrorKind = when (throwable) {
    is HttpRequestTimeoutException -> ErrorKind.Timeout
    is HttpRequestException -> ErrorKind.Offline
    is AuthWeakPasswordException -> ErrorKind.WeakPassword
    is AuthRestException -> authErrorKind(throwable.errorCode, throwable.statusCode)
    is RestException -> httpStatusKind(throwable.statusCode)
    is StripeException -> ErrorKind.Payment
    is AuthLinkException -> ErrorKind.ExpiredLink
    is NoSessionFoundException,
    is SessionRequiredException,
    is TokenExpiredException,
    is InvalidJwtException,
    -> ErrorKind.SessionExpired
    else -> ErrorKind.Unknown
}

/** GoTrue's own error code (the stable identifier) wins; the HTTP status is the fallback. */
internal fun authErrorKind(code: AuthErrorCode?, statusCode: Int): ErrorKind = when (code) {
    AuthErrorCode.InvalidCredentials -> ErrorKind.InvalidCredentials
    AuthErrorCode.EmailExists,
    AuthErrorCode.UserAlreadyExists,
    AuthErrorCode.PhoneExists,
    AuthErrorCode.IdentityAlreadyExists,
    -> ErrorKind.EmailAlreadyRegistered
    AuthErrorCode.EmailNotConfirmed,
    AuthErrorCode.PhoneNotConfirmed,
    AuthErrorCode.ProviderEmailNeedsVerification,
    -> ErrorKind.EmailNotConfirmed
    AuthErrorCode.EmailAddressInvalid,
    AuthErrorCode.EmailAddressNotAuthorized,
    -> ErrorKind.InvalidEmail
    AuthErrorCode.WeakPassword -> ErrorKind.WeakPassword
    AuthErrorCode.SamePassword -> ErrorKind.SamePassword
    // A recovery or OAuth link that expired, was already used, or no longer matches its PKCE verifier.
    AuthErrorCode.FlowStateNotFound,
    AuthErrorCode.FlowStateExpired,
    AuthErrorCode.OtpExpired,
    AuthErrorCode.BadCodeVerifier,
    AuthErrorCode.BadOauthState,
    AuthErrorCode.BadOauthCallback,
    -> ErrorKind.ExpiredLink
    // D-009's failure mode: `enable_anonymous_sign_ins` is off, so "Continue without login" cannot work.
    AuthErrorCode.AnonymousProviderDisabled -> ErrorKind.GuestDisabled
    AuthErrorCode.SignupDisabled -> ErrorKind.SignUpDisabled
    AuthErrorCode.EmailProviderDisabled,
    AuthErrorCode.PhoneProviderDisabled,
    AuthErrorCode.ProviderDisabled,
    AuthErrorCode.OauthProviderNotSupported,
    AuthErrorCode.OtpDisabled,
    -> ErrorKind.ProviderDisabled
    AuthErrorCode.UserNotFound -> ErrorKind.AccountNotFound
    AuthErrorCode.UserBanned -> ErrorKind.AccountBanned
    AuthErrorCode.CaptchaFailed -> ErrorKind.CaptchaFailed
    AuthErrorCode.OverRequestRateLimit,
    AuthErrorCode.OverEmailSendRateLimit,
    AuthErrorCode.OverSmsSendRateLimit,
    -> ErrorKind.RateLimited
    AuthErrorCode.SessionNotFound,
    AuthErrorCode.SessionExpired,
    AuthErrorCode.RefreshTokenNotFound,
    AuthErrorCode.RefreshTokenAlreadyUsed,
    AuthErrorCode.BadJwt,
    AuthErrorCode.NoAuthorization,
    AuthErrorCode.ReauthenticationNeeded,
    -> ErrorKind.SessionExpired
    AuthErrorCode.RequestTimeout -> ErrorKind.Timeout
    AuthErrorCode.UnexpectedFailure -> ErrorKind.Server
    else -> httpStatusKind(statusCode)
}

/** Last resort for a REST failure with no code we recognise: say only what the status implies. */
internal fun httpStatusKind(statusCode: Int): ErrorKind = when {
    statusCode == 401 || statusCode == 403 -> ErrorKind.NotAllowed
    statusCode == 404 -> ErrorKind.NotFound
    statusCode == 408 -> ErrorKind.Timeout
    statusCode == 429 -> ErrorKind.RateLimited
    statusCode >= 500 -> ErrorKind.Server
    else -> ErrorKind.Unknown
}

/**
 * The raw failure, for logs only: this chain is what used to be shown to the user.
 * Walks `cause` so the NSURL / OkHttp detail underneath an SDK wrapper survives.
 */
internal fun Throwable.technicalDetail(): String = buildString {
    var current: Throwable? = this@technicalDetail
    var depth = 0
    while (current != null && depth < 5) {
        if (depth > 0) append(" <- ")
        append(current::class.simpleName ?: "Throwable")
        current.message?.let { append(": ").append(it) }
        current = current.cause.takeIf { it !== current }
        depth++
    }
}
