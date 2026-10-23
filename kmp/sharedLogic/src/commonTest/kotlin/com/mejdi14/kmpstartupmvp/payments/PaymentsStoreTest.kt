package com.mejdi14.kmpstartupmvp.payments

import com.mejdi14.kmpstartupmvp.auth.AsyncValue
import com.mejdi14.kmpstartupmvp.auth.AuthSession
import com.mejdi14.kmpstartupmvp.auth.DeepLinks
import com.mejdi14.kmpstartupmvp.metadata.MetadataRepository
import com.mejdi14.kmpstartupmvp.metadata.PriceWithProduct
import com.mejdi14.kmpstartupmvp.metadata.SubscriptionWithPrice
import com.mejdi14.kmpstartupmvp.metadata.UserMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakePayments : PaymentsRepository {
    val calls = mutableListOf<Pair<String?, String>>()
    var failure: StripeException? = null
    override suspend fun getStripeUrl(price: String?, returnUrl: String): String {
        calls += price to returnUrl
        failure?.let { throw it }
        return "https://checkout.stripe.com/x"
    }
}

private class FakeMeta : MetadataRepository {
    var subscription: SubscriptionWithPrice? = null
    var fetches = 0
    override suspend fun fetchUserDetails(): UserMetadata? = null
    override suspend fun fetchSubscription(): SubscriptionWithPrice? { fetches++; return subscription }
    override suspend fun updateProfile(fullName: String?, avatarUrl: String?) = Unit
}

private val SUB = SubscriptionWithPrice(
    id = "s", userId = "u", created = "c", currentPeriodStart = "a", currentPeriodEnd = "b",
    prices = PriceWithProduct(id = "p"),
)

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsStoreTest {
    private val payments = FakePayments()
    private val meta = FakeMeta()
    private val auth = MutableStateFlow<AsyncValue<AuthSession?>>(AsyncValue.Data(AuthSession("u", null, "t")))
    private var refreshes = 0

    private fun TestScope.store() = PaymentsStore(
        payments, meta, auth, CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        onSubscriptionChanged = { refreshes++ }, pollIntervalMs = 2_000, pollTimeoutMs = 10_000,
    )

    @Test
    fun sendsPriceAndDeepLinkReturnUrl() = runTest {
        val url = store().getStripeUrl("price_1")
        assertEquals("https://checkout.stripe.com/x", url)
        assertEquals(listOf<Pair<String?, String>>("price_1" to DeepLinks.STRIPE_RETURN_URL), payments.calls)
    }

    @Test
    fun noPriceMeansPortal() = runTest {
        store().getStripeUrl(" ")
        assertEquals(null, payments.calls.single().first)
        assertTrue(payments.calls.single().second.isNotEmpty())
    }

    @Test
    fun refusesWithoutSessionAndNeverCallsFunction() = runTest {
        auth.value = AsyncValue.Data(null)
        assertFailsWith<StripeException> { store().getStripeUrl(null) }
        auth.value = AsyncValue.Loading
        assertFailsWith<StripeException> { store().getStripeUrl(null) }
        assertTrue(payments.calls.isEmpty())
    }

    @Test
    fun refusesAGuestAndNeverCallsFunction() = runTest {
        // D-009/D-011: a guest has no email for the Stripe customer and would lose a paid subscription on sign-out.
        auth.value = AsyncValue.Data(AuthSession("u", null, "t", isAnonymous = true))
        assertFailsWith<GuestCheckoutException> { store().getStripeUrl("price_1") }
        assertFailsWith<GuestCheckoutException> { store().getStripeUrl(null) }
        assertTrue(payments.calls.isEmpty())
    }

    @Test
    fun surfacesServerMessage() = runTest {
        payments.failure = StripeException("Could not create billing portal")
        val e = assertFailsWith<StripeException> { store().getStripeUrl(null) }
        assertEquals("Could not create billing portal", e.message)
    }

    @Test
    fun checkoutReturnPollsUntilSubscriptionAppears() = runTest {
        val store = store()
        store.markLaunched("price_1")
        store.onReturned()
        runCurrent()
        assertTrue(store.syncing.value)
        assertEquals(0, refreshes)
        advanceTimeBy(4_100)
        meta.subscription = SUB
        advanceTimeBy(2_100)
        assertEquals(1, refreshes)
        assertFalse(store.syncing.value)
    }

    @Test
    fun checkoutReturnGivesUpAtTimeoutButStillRefreshes() = runTest {
        val store = store()
        store.markLaunched("price_1")
        store.onReturned()
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(1, refreshes)
        assertFalse(store.syncing.value)
        assertEquals(6, meta.fetches) // t=0,2,4,6,8,10
    }

    @Test
    fun portalReturnRefreshesOnceWithoutPolling() = runTest {
        val store = store()
        store.markLaunched(null)
        store.onReturned()
        assertEquals(1, refreshes)
        assertEquals(0, meta.fetches)
    }

    @Test
    fun returnIsIdempotentAndIgnoredWhenNothingLaunched() = runTest {
        val store = store()
        store.onReturned()
        assertEquals(0, refreshes)
        store.markLaunched(null)
        store.onReturned()
        store.onReturned()
        assertEquals(1, refreshes)
    }

    @Test
    fun queuesPaymentsRequestUntilConsumed() = runTest {
        val store = store()
        store.requestPayments("price_9")
        assertEquals(PaymentRequest("price_9"), store.pendingRequest.value)
        store.consumeRequest()
        assertEquals(null, store.pendingRequest.value)
    }
}
