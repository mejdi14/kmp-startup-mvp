package com.mejdi14.kmpstartupmvp

import android.app.Application
import com.mejdi14.kmpstartupmvp.auth.AuthStore
import com.mejdi14.kmpstartupmvp.data.SessionSettingsFactory
import com.mejdi14.kmpstartupmvp.data.SupabaseRepository
import com.mejdi14.kmpstartupmvp.data.createAppSupabaseClient
import com.mejdi14.kmpstartupmvp.metadata.MetadataStore
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

    /** The `Metadata` provider; follows [authStore]'s session. */
    val metadataStore: MetadataStore by lazy { MetadataStore(repository, authStore.state, appScope) }
}
