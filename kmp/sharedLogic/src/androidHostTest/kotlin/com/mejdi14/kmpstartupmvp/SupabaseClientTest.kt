package com.mejdi14.kmpstartupmvp

import com.mejdi14.kmpstartupmvp.config.SupabaseConfig
import com.mejdi14.kmpstartupmvp.data.SupabaseRepository
import com.mejdi14.kmpstartupmvp.data.createAppSupabaseClient
import com.russhwolf.settings.MapSettings
import io.github.jan.supabase.postgrest.postgrest
import kotlin.test.Test
import kotlin.test.assertEquals

class SupabaseClientTest {
    @Test
    fun localClientKeepsHttpScheme() {
        val client = createAppSupabaseClient(SupabaseConfig.LOCAL, MapSettings())
        assertEquals("localhost:54321", client.supabaseUrl)
        assertEquals("http://localhost:54321/rest/v1", client.postgrest.resolveUrl("").removeSuffix("/"))
        SupabaseRepository(client)
    }

    @Test
    fun hostedClientUsesHttps() {
        val client = createAppSupabaseClient(SupabaseConfig.HOSTED, MapSettings())
        assertEquals("https://ibyxomczmyaptsjgzvzi.supabase.co/rest/v1", client.postgrest.resolveUrl("").removeSuffix("/"))
    }
}
