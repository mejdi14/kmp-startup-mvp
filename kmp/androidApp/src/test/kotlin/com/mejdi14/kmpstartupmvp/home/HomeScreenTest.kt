package com.mejdi14.kmpstartupmvp.home

import com.mejdi14.kmpstartupmvp.auth.AsyncValue
import com.mejdi14.kmpstartupmvp.metadata.MissingProfileException
import com.mejdi14.kmpstartupmvp.metadata.PriceWithProduct
import com.mejdi14.kmpstartupmvp.metadata.Product
import com.mejdi14.kmpstartupmvp.metadata.SubscriptionWithPrice
import com.mejdi14.kmpstartupmvp.metadata.UserMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeScreenTest {
    private fun sub(name: String?) = SubscriptionWithPrice(
        id = "s", userId = "u", created = "c", currentPeriodStart = "a", currentPeriodEnd = "b",
        prices = PriceWithProduct(id = "p", products = Product(id = "x", name = name)),
    )

    @Test
    fun loadingHasNoSentence() = assertNull(statusText(AsyncValue.Loading))

    @Test
    fun failedToLoad() = assertEquals(
        "Failed to load subscription plan",
        statusText(AsyncValue.Error(MissingProfileException())),
    )

    @Test
    fun noPlan() = assertEquals(
        "You are not currently subscribed to any plan.",
        statusText(AsyncValue.Data(UserMetadata(id = "u"))),
    )

    @Test
    fun hasPlan() = assertEquals(
        "You are currently on the Pro plan.",
        statusText(AsyncValue.Data(UserMetadata(id = "u", subscription = sub("Pro")))),
    )

    @Test
    fun unnamedPlanDoesNotSayNull() = assertEquals(
        "You are currently subscribed to a plan.",
        statusText(AsyncValue.Data(UserMetadata(id = "u", subscription = sub(null)))),
    )
}
