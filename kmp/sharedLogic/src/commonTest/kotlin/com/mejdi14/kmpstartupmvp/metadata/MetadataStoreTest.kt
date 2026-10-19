package com.mejdi14.kmpstartupmvp.metadata

import com.mejdi14.kmpstartupmvp.auth.AsyncValue
import com.mejdi14.kmpstartupmvp.auth.AuthSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class FakeMetadataRepository : MetadataRepository {
    var user: UserMetadata? = UserMetadata(id = "u1")
    var subscription: SubscriptionWithPrice? = null
    var failure: Throwable? = null
    var fetches = 0

    override suspend fun fetchUserDetails(): UserMetadata? {
        fetches++
        failure?.let { throw it }
        return user
    }

    override suspend fun fetchSubscription() = subscription
}

private fun session(token: String, user: String = "u1") = AuthSession(user, "a@b.co", token)

private fun sub() = SubscriptionWithPrice(
    id = "s1", userId = "u1", created = "c", currentPeriodStart = "a", currentPeriodEnd = "b",
    prices = PriceWithProduct(id = "p1", products = Product(id = "pr1", name = "Pro")),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MetadataStoreTest {
    private val auth = MutableStateFlow<AsyncValue<AuthSession?>>(AsyncValue.Loading)
    private val repo = FakeMetadataRepository()

    private fun kotlinx.coroutines.test.TestScope.store() =
        MetadataStore(repo, auth, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun loadingWhileAuthLoadingThenNullWhenSignedOut() = runTest {
        val store = store()
        assertEquals(AsyncValue.Loading, store.state.value)
        auth.value = AsyncValue.Data(null)
        assertEquals(AsyncValue.Data(null), store.state.value)
        assertEquals(0, repo.fetches)
    }

    @Test
    fun mergesSubscriptionIntoProfile() = runTest {
        repo.subscription = sub()
        val store = store()
        auth.value = AsyncValue.Data(session("t1"))
        val data = assertIs<AsyncValue.Data<UserMetadata?>>(store.state.value)
        assertEquals("Pro", data.value?.subscription?.prices?.products?.name)
    }

    @Test
    fun noSubscriptionIsDataWithNullSubscription() = runTest {
        val store = store()
        auth.value = AsyncValue.Data(session("t1"))
        val data = assertIs<AsyncValue.Data<UserMetadata?>>(store.state.value)
        assertEquals(null, data.value?.subscription)
    }

    @Test
    fun missingProfileRowIsErrorNotCrash() = runTest {
        repo.user = null
        val store = store()
        auth.value = AsyncValue.Data(session("t1"))
        val err = assertIs<AsyncValue.Error>(store.state.value)
        assertIs<MissingProfileException>(err.cause)
    }

    @Test
    fun fetchFailureIsError() = runTest {
        repo.failure = RuntimeException("boom")
        val store = store()
        auth.value = AsyncValue.Data(session("t1"))
        assertIs<AsyncValue.Error>(store.state.value)
    }

    @Test
    fun tokenRefreshDoesNotRefetch() = runTest {
        val store = store()
        auth.value = AsyncValue.Data(session("t1"))
        auth.value = AsyncValue.Data(session("t2"))
        assertEquals(1, repo.fetches)
        assertIs<AsyncValue.Data<UserMetadata?>>(store.state.value)
    }

    @Test
    fun logoutClearsAndLoginRefetches() = runTest {
        val store = store()
        auth.value = AsyncValue.Data(session("t1"))
        auth.value = AsyncValue.Data(null)
        assertEquals(AsyncValue.Data(null), store.state.value)
        auth.value = AsyncValue.Data(session("t3", user = "u2"))
        assertEquals(2, repo.fetches)
    }

    @Test
    fun refreshRefetches() = runTest {
        val store = store()
        auth.value = AsyncValue.Data(session("t1"))
        repo.subscription = sub()
        store.refresh()
        assertEquals(2, repo.fetches)
        val data = assertIs<AsyncValue.Data<UserMetadata?>>(store.state.value)
        assertEquals("Pro", data.value?.subscription?.prices?.products?.name)
    }
}
