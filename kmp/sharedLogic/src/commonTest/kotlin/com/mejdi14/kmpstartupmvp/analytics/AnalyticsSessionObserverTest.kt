package com.mejdi14.kmpstartupmvp.analytics

import com.mejdi14.kmpstartupmvp.auth.AsyncValue
import com.mejdi14.kmpstartupmvp.auth.AuthSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class RecordingTracker : AnalyticsTracker {
    val events = mutableListOf<String>()
    override fun identify(userId: String, properties: Map<String, Any>) { events += "identify:$userId:$properties" }
    override fun reset() { events += "reset" }
    override fun screen(name: String) { events += "screen:$name" }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsSessionObserverTest {
    private val tracker = RecordingTracker()
    private val auth = MutableStateFlow<AsyncValue<AuthSession?>>(AsyncValue.Loading)

    private fun TestScopeObserver(scope: CoroutineScope) = AnalyticsSessionObserver(auth, tracker, scope)

    @Test
    fun identifiesOnSessionAndOnEveryRefresh_resetsOnLogout() = runTest {
        TestScopeObserver(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        assertEquals(emptyList(), tracker.events) // Loading emits nothing

        auth.value = AsyncValue.Data(AuthSession("u1", "a@b.co", "t1"))
        auth.value = AsyncValue.Data(AuthSession("u1", "a@b.co", "t2")) // token refresh
        auth.value = AsyncValue.Data(null)

        assertEquals(
            listOf("identify:u1:{email=a@b.co}", "identify:u1:{email=a@b.co}", "reset"),
            tracker.events,
        )
    }

    @Test
    fun coldStartWithNoSessionResets() = runTest {
        auth.value = AsyncValue.Data(null)
        TestScopeObserver(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        assertEquals(listOf("reset"), tracker.events)
    }

    @Test
    fun omitsEmailWhenAbsent() = runTest {
        auth.value = AsyncValue.Data(AuthSession("u2", null, "t"))
        TestScopeObserver(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        assertEquals(listOf("identify:u2:{}"), tracker.events)
    }
}
