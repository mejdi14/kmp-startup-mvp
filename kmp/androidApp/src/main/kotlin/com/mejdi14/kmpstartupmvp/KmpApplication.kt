package com.mejdi14.kmpstartupmvp

import android.app.Application
import com.mejdi14.kmpstartupmvp.auth.AuthStore
import com.mejdi14.kmpstartupmvp.data.SessionSettingsFactory
import com.mejdi14.kmpstartupmvp.data.SupabaseRepository
import com.mejdi14.kmpstartupmvp.data.createAppSupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Owns the app-lifetime Supabase client and [AuthStore] (the keepAlive `Auth` provider). */
class KmpApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val authStore: AuthStore by lazy {
        val client = createAppSupabaseClient(supabaseConfig, SessionSettingsFactory(this).create())
        AuthStore(SupabaseRepository(client), appScope)
    }
}
