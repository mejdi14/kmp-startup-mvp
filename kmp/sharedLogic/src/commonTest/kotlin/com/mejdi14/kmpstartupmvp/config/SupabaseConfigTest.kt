package com.mejdi14.kmpstartupmvp.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SupabaseConfigTest {
    @Test
    fun localPointsAtLocalSupabase() {
        assertEquals("http://localhost:54321", SupabaseConfig.forEnvironment("LOCAL").url)
    }

    @Test
    fun hostedPointsAtHostedProject() {
        assertEquals("https://ibyxomczmyaptsjgzvzi.supabase.co", SupabaseConfig.forEnvironment("HOSTED").url)
    }

    @Test
    fun environmentsUseDifferentKeys() {
        assertNotEquals(SupabaseConfig.LOCAL.anonKey, SupabaseConfig.HOSTED.anonKey)
    }

    @Test
    fun unknownEnvironmentFails() {
        assertFailsWith<IllegalArgumentException> { SupabaseConfig.forEnvironment("STAGING") }
    }
}
