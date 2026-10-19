package com.mejdi14.kmpstartupmvp.analytics

import com.mejdi14.kmpstartupmvp.auth.AsyncValue
import com.mejdi14.kmpstartupmvp.auth.AuthSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * SDK-independent analytics surface. Each platform supplies the PostHog-backed
 * implementation (native init differs per platform, PORTING.md §1.5). There is no
 * client-side `capture()` for business events: those are emitted server-side.
 */
interface AnalyticsTracker {
    fun identify(userId: String, properties: Map<String, Any>)
    fun reset()
    fun screen(name: String)
}

/**
 * Port of the PostHog side effect inside Flutter's `Auth` stream listener
 * (`auth_notifier.dart:29-34`): `identify` on every non-null session emission
 * (including token refreshes, as in Flutter) and `reset` on a null session.
 * Loading/error emit nothing, matching the Flutter listener which only sees sessions.
 */
class AnalyticsSessionObserver(
    authState: StateFlow<AsyncValue<AuthSession?>>,
    tracker: AnalyticsTracker,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            authState.collect { state ->
                if (state !is AsyncValue.Data) return@collect
                val session = state.value
                if (session == null) {
                    tracker.reset()
                } else {
                    tracker.identify(session.userId, session.email?.let { mapOf("email" to it) } ?: emptyMap())
                }
            }
        }
    }
}
