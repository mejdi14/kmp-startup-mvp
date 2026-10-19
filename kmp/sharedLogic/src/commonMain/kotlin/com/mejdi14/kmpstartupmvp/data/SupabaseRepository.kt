package com.mejdi14.kmpstartupmvp.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper over the SDK so later tasks' stores never touch it directly.
 * Task 3 grows the auth surface; this task only exposes the minimum.
 */
class SupabaseRepository(val client: SupabaseClient) {
    val sessionStatus: Flow<SessionStatus> get() = client.auth.sessionStatus

    /** Unauthenticated read of the public `products` table (PORTING.md §2.1). */
    suspend fun fetchProductsRaw(): String =
        client.from("products").select().data
}
