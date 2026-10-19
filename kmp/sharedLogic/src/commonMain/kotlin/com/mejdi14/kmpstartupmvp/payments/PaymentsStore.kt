package com.mejdi14.kmpstartupmvp.payments

import com.mejdi14.kmpstartupmvp.auth.AsyncValue
import com.mejdi14.kmpstartupmvp.auth.AuthSession
import com.mejdi14.kmpstartupmvp.auth.DeepLinks
import com.mejdi14.kmpstartupmvp.metadata.MetadataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Port of `PaymentsScreen` + `Auth.getUserStripeLink` (PORTING.md §1.1, §1.4), with the §1.4 gap fixed:
 * after Stripe hands control back, [onReturned] polls `subscriptions` with backoff (D-004) and asks
 * [onSubscriptionChanged] (the Home metadata refresh) to reload once the webhook has written the row.
 */
class PaymentsStore(
    private val repository: PaymentsRepository,
    private val metadata: MetadataRepository,
    private val authState: StateFlow<AsyncValue<AuthSession?>>,
    private val scope: CoroutineScope,
    private val onSubscriptionChanged: () -> Unit,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    private val pollTimeoutMs: Long = POLL_TIMEOUT_MS,
) {
    private val _pendingRequest = MutableStateFlow<PaymentRequest?>(null)

    /** A payments deep link received before the UI could act on it (e.g. while logged out). */
    val pendingRequest: StateFlow<PaymentRequest?> = _pendingRequest.asStateFlow()

    private val _syncing = MutableStateFlow(false)

    /** True while the post-checkout poll is running. */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private var launchedPrice: String? = null
    private var launched = false
    private var pollJob: Job? = null

    /**
     * Resolves the Stripe URL. Guarded on a real session: with none, supabase-kt would send the anon
     * key as bearer and the function answers 400 "Authentication Error" (§2.3 pitfall).
     */
    // @Throws is what makes a Kotlin exception cross into Swift as an NSError. Without it the
    // ObjC export treats any non-CancellationException as fatal and aborts the process, so a
    // wrong password or an offline device would kill the iOS app instead of showing a message.
    @Throws(Throwable::class)
    suspend fun getStripeUrl(price: String?): String {
        if ((authState.value as? AsyncValue.Data)?.value == null) {
            throw StripeException("You must be signed in to open Stripe")
        }
        return repository.getStripeUrl(price?.takeIf { it.isNotBlank() }, DeepLinks.STRIPE_RETURN_URL)
    }

    /** Record that the browser tab was opened, so the next [onReturned] knows to look for a purchase. */
    fun markLaunched(price: String?) {
        launched = true
        launchedPrice = price?.takeIf { it.isNotBlank() }
    }

    /**
     * Stripe handed control back (deep link, or the tab was dismissed and the app resumed). Idempotent:
     * only the first call after [markLaunched] acts. A checkout return polls for the new subscription; a
     * portal return just refreshes Home once.
     */
    fun onReturned() {
        if (!launched) return
        launched = false
        val checkout = launchedPrice != null
        launchedPrice = null
        pollJob?.cancel()
        if (!checkout) {
            onSubscriptionChanged()
            return
        }
        pollJob = scope.launch {
            _syncing.value = true
            try {
                var waited = 0L
                while (true) {
                    val found = try {
                        metadata.fetchSubscription() != null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        false // transient; keep polling until the window closes
                    }
                    if (found || waited >= pollTimeoutMs) break
                    delay(pollIntervalMs)
                    waited += pollIntervalMs
                }
                // Refresh on timeout too, so Home shows whatever state the server has.
                onSubscriptionChanged()
            } finally {
                _syncing.value = false
            }
        }
    }

    /** Queue a `/payments` deep link; the nav layer consumes it once signed in. */
    fun requestPayments(price: String?) {
        _pendingRequest.value = PaymentRequest(price?.takeIf { it.isNotBlank() })
    }

    fun consumeRequest() {
        _pendingRequest.value = null
    }

    companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val POLL_TIMEOUT_MS = 30_000L
    }
}

/** Open Payments for [price] (Checkout) or, when null, the billing portal. */
data class PaymentRequest(val price: String?)
