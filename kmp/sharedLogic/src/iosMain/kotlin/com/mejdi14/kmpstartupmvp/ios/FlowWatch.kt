package com.mejdi14.kmpstartupmvp.ios

import com.mejdi14.kmpstartupmvp.errors.toUserFacingError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Handle returned by [watch]; call [cancel] to stop receiving values. */
class Watcher internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/**
 * Kotlin `StateFlow` -> Swift bridge (D-001, decided in task 13: a hand-written collector wrapper,
 * no SKIE). [onEach] is invoked on the main thread with the current value, then on every change.
 */
fun <T : Any?> StateFlow<T>.watch(onEach: (T) -> Unit): Watcher =
    Watcher(mainScope.launch { collect { onEach(it) } })

/**
 * The one sentence to show for a Kotlin throwable surfaced to Swift as an `NSError` (D-010).
 * The copy lives in `commonMain`, so iOS and Android say the same thing for the same failure.
 */
fun Throwable.userMessage(): String = toUserFacingError().message

/**
 * The raw failure behind [userMessage] — SDK exception chain and all. Swift logs this, so the
 * Ktor/NSURL detail that used to be shown to the user is still available when debugging (D-010).
 */
fun Throwable.technicalMessage(): String = toUserFacingError().technicalDetail
