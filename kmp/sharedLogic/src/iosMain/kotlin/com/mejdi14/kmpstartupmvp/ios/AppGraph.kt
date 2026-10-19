package com.mejdi14.kmpstartupmvp.ios

import com.mejdi14.kmpstartupmvp.analytics.AnalyticsSessionObserver
import com.mejdi14.kmpstartupmvp.analytics.AnalyticsTracker
import com.mejdi14.kmpstartupmvp.auth.AuthStore
import com.mejdi14.kmpstartupmvp.config.SupabaseConfig
import com.mejdi14.kmpstartupmvp.data.SessionSettingsFactory
import com.mejdi14.kmpstartupmvp.data.SupabaseRepository
import com.mejdi14.kmpstartupmvp.data.createAppSupabaseClient
import com.mejdi14.kmpstartupmvp.metadata.MetadataStore
import com.mejdi14.kmpstartupmvp.payments.PaymentsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * iOS counterpart of Android's `KmpApplication`: owns the app-lifetime client and shared stores.
 * Swift cannot build a `CoroutineScope` or call the top-level factory ergonomically, so this lives
 * in iosMain. [environment] is `"LOCAL"` or `"HOSTED"` (see [SupabaseConfig.forEnvironment]).
 */
class AppGraph(environment: String, analytics: AnalyticsTracker) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val repository = SupabaseRepository(
        createAppSupabaseClient(SupabaseConfig.forEnvironment(environment), SessionSettingsFactory().create()),
    )

    val authStore = AuthStore(repository, appScope)
    val metadataStore = MetadataStore(repository, authStore.state, appScope)
    val paymentsStore = PaymentsStore(
        repository, repository, authStore.state, appScope,
        onSubscriptionChanged = { metadataStore.refresh() },
    )

    init {
        // identify/reset follow the session stream for the whole app lifetime (task 12).
        AnalyticsSessionObserver(authStore.state, analytics, appScope)
    }
}
