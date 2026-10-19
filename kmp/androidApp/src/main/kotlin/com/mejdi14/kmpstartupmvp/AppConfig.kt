package com.mejdi14.kmpstartupmvp

import com.mejdi14.kmpstartupmvp.config.SupabaseConfig

/** Google web OAuth client ID for native sign-in (empty when not configured). */
const val googleWebClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID

/** Supabase settings for this build variant: local for debug, hosted for release. */
val supabaseConfig: SupabaseConfig = SupabaseConfig.forEnvironment(BuildConfig.SUPABASE_ENV)
