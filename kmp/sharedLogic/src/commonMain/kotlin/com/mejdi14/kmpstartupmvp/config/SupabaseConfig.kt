package com.mejdi14.kmpstartupmvp.config

/**
 * Supabase connection settings. Ported from `flutter/env.local.json` (debug)
 * and `flutter/env.json` (release). Both anon keys are public by design
 * (PORTING.md §1.9), so they are committed rather than injected.
 *
 * Which environment a build uses is chosen by the platform app at build time
 * (Android: `BuildConfig.SUPABASE_ENV` per build type), not at runtime.
 * PostHog is deliberately not here — it is configured natively per platform
 * (task 12, PORTING.md §1.5).
 */
data class SupabaseConfig(
    val url: String,
    val anonKey: String,
) {
    companion object {
        /**
         * Local `supabase start`. On an Android emulator `localhost` is the
         * emulator itself; use `adb reverse tcp:54321 tcp:54321` (also for
         * physical devices) so it reaches the dev machine.
         */
        val LOCAL = SupabaseConfig(
            url = "http://localhost:54321",
            anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0",
        )

        val HOSTED = SupabaseConfig(
            url = "https://ibyxomczmyaptsjgzvzi.supabase.co",
            anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlieXhvbWN6bXlhcHRzamd6dnppIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MjEzMDk0OTUsImV4cCI6MjAzNjg4NTQ5NX0.DOhoJJ3V7KJFjglNftgDDQwMNrB3oiWtf21BfRcB2Cc",
        )

        /** Resolves the `SUPABASE_ENV` build-config value: `"LOCAL"` or `"HOSTED"`. */
        fun forEnvironment(name: String): SupabaseConfig = when (name) {
            "LOCAL" -> LOCAL
            "HOSTED" -> HOSTED
            else -> throw IllegalArgumentException("Unknown SUPABASE_ENV '$name' (expected LOCAL or HOSTED)")
        }
    }
}
