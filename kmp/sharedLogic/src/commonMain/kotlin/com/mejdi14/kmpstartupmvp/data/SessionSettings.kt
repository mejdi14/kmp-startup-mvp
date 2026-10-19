package com.mejdi14.kmpstartupmvp.data

import com.russhwolf.settings.Settings

/**
 * Platform-backed key/value store for the Supabase session (D-006): Android
 * `SharedPreferences`, iOS `NSUserDefaults`. Plaintext, same posture as Flutter.
 */
expect class SessionSettingsFactory {
    fun create(): Settings
}
