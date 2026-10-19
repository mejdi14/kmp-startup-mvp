package com.mejdi14.kmpstartupmvp.analytics

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.mejdi14.kmpstartupmvp.BuildConfig
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

/**
 * PostHog Android SDK behind [AnalyticsTracker]. Config comes from manifest
 * `com.posthog.posthog.*` meta-data, the same native keys the Flutter app used (§1.5),
 * deliberately separate from the Supabase build config. A missing key disables analytics.
 */
class PostHogTracker(context: Context) : AnalyticsTracker {
    private val enabled: Boolean

    init {
        val meta = context.packageManager
            .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        val apiKey = meta?.getString(KEY_API)
        enabled = !apiKey.isNullOrBlank()
        if (enabled) {
            val config = PostHogAndroidConfig(apiKey!!, meta.getString(KEY_HOST) ?: DEFAULT_HOST).apply {
                captureApplicationLifecycleEvents = meta.getBoolean(KEY_LIFECYCLE, true)
                // Screen views come from the nav graph, not Activity tracking (Compose has one Activity).
                captureScreenViews = false
                debug = BuildConfig.DEBUG
            }
            PostHogAndroid.setup(context.applicationContext, config)
        } else {
            Log.w(TAG, "PostHog API key missing; analytics disabled")
        }
    }

    override fun identify(userId: String, properties: Map<String, Any>) {
        if (enabled) PostHog.identify(userId, userProperties = properties)
    }

    override fun reset() {
        if (enabled) PostHog.reset()
    }

    override fun screen(name: String) {
        if (enabled) PostHog.screen(name)
    }

    private companion object {
        const val TAG = "PostHog"
        const val DEFAULT_HOST = "https://app.posthog.com"
        const val KEY_API = "com.posthog.posthog.API_KEY"
        const val KEY_HOST = "com.posthog.posthog.POSTHOG_HOST"
        const val KEY_LIFECYCLE = "com.posthog.posthog.TRACK_APPLICATION_LIFECYCLE_EVENTS"
    }
}
