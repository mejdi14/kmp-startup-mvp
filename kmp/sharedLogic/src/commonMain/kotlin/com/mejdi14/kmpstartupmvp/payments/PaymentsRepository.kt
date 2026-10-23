package com.mejdi14.kmpstartupmvp.payments

/** The `get_stripe_url` edge function (PORTING.md §2.3); implemented over supabase-kt, faked in tests. */
interface PaymentsRepository {
    /**
     * Returns the hosted Stripe URL: Checkout for a known [price], the billing portal when [price] is
     * null or unknown. Throws [StripeException] carrying the server's `message` on failure.
     */
    suspend fun getStripeUrl(price: String?, returnUrl: String): String
}

/** A failed `get_stripe_url` call; [message] is shown in the "Failed to open Stripe" dialog. */
class StripeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A guest (anonymous session, D-009) asked for Checkout or the billing portal. Refused before the
 * edge function is called: a guest has no email for the Stripe customer, and a guest who later signs
 * out could never get back to a subscription they paid for. Saving the account (D-011) lifts it.
 */
class GuestCheckoutException : Exception("Guest sessions cannot open Stripe; save the account first")
