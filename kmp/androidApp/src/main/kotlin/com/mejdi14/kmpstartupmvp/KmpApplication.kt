package com.mejdi14.kmpstartupmvp

import android.app.Application
import com.mejdi14.kmpstartupmvp.analytics.AnalyticsTracker
import com.mejdi14.kmpstartupmvp.analytics.PostHogTracker
import com.mejdi14.kmpstartupmvp.auth.AuthStore
import com.mejdi14.kmpstartupmvp.data.SessionSettingsFactory
import com.mejdi14.kmpstartupmvp.data.SupabaseRepository
import com.mejdi14.kmpstartupmvp.data.createAppSupabaseClient
import com.mejdi14.kmpstartupmvp.metadata.MetadataStore
import com.mejdi14.kmpstartupmvp.payments.PaymentsStore
import com.mejdi14.kmpstartupmvp.shadow.ShadowGraph
import com.mejdi14.kmpstartupmvp.shadow.ShadowStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Owns the app-lifetime Supabase client and the shared stores. */
class KmpApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val repository: SupabaseRepository by lazy {
        SupabaseRepository(createAppSupabaseClient(supabaseConfig, SessionSettingsFactory(this).create()))
    }

    /** The keepAlive `Auth` provider. */
    val authStore: AuthStore by lazy { AuthStore(repository, appScope) }

    val analytics: AnalyticsTracker by lazy { PostHogTracker(this) }

    /** The `Metadata` provider; follows [authStore]'s session. */
    val metadataStore: MetadataStore by lazy { MetadataStore(repository, authStore.state, appScope) }

    /** Stripe Checkout/portal link + post-return subscription polling (D-004). */
    val paymentsStore: PaymentsStore by lazy {
        PaymentsStore(repository, repository, authStore.state, appScope, onSubscriptionChanged = { metadataStore.refresh() })
    }

    /** Shadow Studio, talking to the engine on the Mac (reached through `adb reverse tcp:8765 tcp:8765`). */
    val shadowStore: ShadowStore by lazy { ShadowGraph(this).store }

    // Sign-in, payments and analytics are skipped for now: nothing above is created unless a screen asks for it,
    // so the app opens straight into Shadow Studio and sends nothing to Supabase or PostHog.
}
