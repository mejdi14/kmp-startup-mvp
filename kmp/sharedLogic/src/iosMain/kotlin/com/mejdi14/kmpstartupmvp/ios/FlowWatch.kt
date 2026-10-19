package com.mejdi14.kmpstartupmvp.ios

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

/** Message of a Kotlin throwable surfaced to Swift as an `NSError` (null-safe for Swift callers). */
fun Throwable.userMessage(): String = message ?: this::class.simpleName ?: "Something went wrong"
