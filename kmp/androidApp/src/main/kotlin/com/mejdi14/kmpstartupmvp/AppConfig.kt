package com.mejdi14.kmpstartupmvp

import com.mejdi14.kmpstartupmvp.config.SupabaseConfig

/** Supabase settings for this build variant: local for debug, hosted for release. */
val supabaseConfig: SupabaseConfig = SupabaseConfig.forEnvironment(BuildConfig.SUPABASE_ENV)
