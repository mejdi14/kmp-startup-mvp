package com.mejdi14.kmpstartupmvp.metadata

/** Reads the [MetadataStore] needs; implemented over supabase-kt, faked in tests. */
interface MetadataRepository {
    /** The signed-in user's `users` row, or null when it is absent (`maybeSingle`, rough edge #3). */
    suspend fun fetchUserDetails(): UserMetadata?

    /** Newest trialing/active subscription with its price and product, or null. */
    suspend fun fetchSubscription(): SubscriptionWithPrice?
}
