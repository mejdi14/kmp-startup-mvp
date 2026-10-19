package com.mejdi14.kmpstartupmvp.data

import com.mejdi14.kmpstartupmvp.config.SupabaseConfig
import com.mejdi14.kmpstartupmvp.auth.DeepLinks
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Builds the single app-wide client (analogue of `Supabase.instance.client`).
 * Mirrors supabase_flutter defaults: PKCE, auto-refresh, persisted session.
 * The Ktor engine is picked up from the platform dependency (OkHttp/Darwin).
 */
fun createAppSupabaseClient(config: SupabaseConfig, settings: Settings): SupabaseClient =
    createSupabaseClient(config.url, config.anonKey) {
        install(Auth) {
            flowType = FlowType.PKCE
            alwaysAutoRefresh = true
            sessionManager = SettingsSessionManager(settings)
            scheme = DeepLinks.SCHEME
            host = DeepLinks.HOST
            codeVerifierCache = SettingsCodeVerifierCache(settings)
        }
        install(Postgrest)
        install(Functions)
    }
