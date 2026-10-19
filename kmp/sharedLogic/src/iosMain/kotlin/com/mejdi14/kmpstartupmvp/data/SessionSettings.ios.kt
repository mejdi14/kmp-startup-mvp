package com.mejdi14.kmpstartupmvp.data

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

actual class SessionSettingsFactory {
    actual fun create(): Settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
}
