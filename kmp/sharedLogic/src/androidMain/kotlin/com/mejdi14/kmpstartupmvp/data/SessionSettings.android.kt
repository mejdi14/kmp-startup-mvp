package com.mejdi14.kmpstartupmvp.data

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

actual class SessionSettingsFactory(private val context: Context) {
    actual fun create(): Settings =
        SharedPreferencesSettings(context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE))
}
