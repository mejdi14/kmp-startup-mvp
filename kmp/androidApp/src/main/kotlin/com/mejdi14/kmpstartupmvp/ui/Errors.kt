package com.mejdi14.kmpstartupmvp.ui

import android.util.Log
import com.mejdi14.kmpstartupmvp.errors.toUserFacingError

/** Logcat tag for the raw SDK failure behind every error banner. */
private const val TAG = "AppError"

/**
 * The one sentence to show for a failure (D-010). The copy lives in `sharedLogic`, so Android and
 * iOS say the same thing; the raw Ktor/Supabase text is not shown — [logFailure] records it instead.
 */
fun Throwable.userMessage(): String = toUserFacingError().message

/**
 * Records the raw failure, so a message the user can act on does not cost us the detail needed to
 * debug it. Called once per failure where the ViewModels catch, including the many call sites that
 * pass no result callback at all and would otherwise swallow the failure silently.
 */
fun logFailure(throwable: Throwable) {
    val error = throwable.toUserFacingError()
    Log.w(TAG, "${error.kind}: ${error.technicalDetail}", throwable)
}
