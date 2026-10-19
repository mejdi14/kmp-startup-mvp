package com.mejdi14.kmpstartupmvp.metadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** `users` row (PORTING.md §1.2) plus the active subscription, merged by [MetadataStore]. */
@Serializable
data class UserMetadata(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("billing_address") val billingAddress: JsonObject? = null,
    @SerialName("payment_method") val paymentMethod: JsonObject? = null,
    val subscription: SubscriptionWithPrice? = null,
)

/** `subscriptions` row joined with `prices(*, products(*))`. */
@Serializable
data class SubscriptionWithPrice(
    val id: String,
    @SerialName("user_id") val userId: String,
    val status: String? = null,
    @SerialName("price_id") val priceId: String? = null,
    val quantity: Int? = null,
    @SerialName("cancel_at_period_end") val cancelAtPeriodEnd: Boolean? = null,
    @SerialName("cancel_at") val cancelAt: String? = null,
    @SerialName("canceled_at") val canceledAt: String? = null,
    val created: String,
    @SerialName("current_period_start") val currentPeriodStart: String,
    @SerialName("current_period_end") val currentPeriodEnd: String,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("trial_start") val trialStart: String? = null,
    @SerialName("trial_end") val trialEnd: String? = null,
    val metadata: JsonObject? = null,
    val prices: PriceWithProduct? = null,
)

@Serializable
data class PriceWithProduct(
    val id: String,
    val active: Boolean? = null,
    val currency: String? = null,
    val description: String? = null,
    val interval: String? = null,
    @SerialName("interval_count") val intervalCount: Int? = null,
    val metadata: JsonObject? = null,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("trial_period_days") val trialPeriodDays: Int? = null,
    val type: String? = null,
    @SerialName("unit_amount") val unitAmount: Long? = null,
    val products: Product? = null,
)

@Serializable
data class Product(
    val id: String,
    val active: Boolean? = null,
    val description: String? = null,
    val image: String? = null,
    val metadata: JsonObject? = null,
    val name: String? = null,
)

/** The signed-in user has no `users` row (rough edge #3): surfaced as the failed-to-load state. */
class MissingProfileException : Exception("No user profile row found")
