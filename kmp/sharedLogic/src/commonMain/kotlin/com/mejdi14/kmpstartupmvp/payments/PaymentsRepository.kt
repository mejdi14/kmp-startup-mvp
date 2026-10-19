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
