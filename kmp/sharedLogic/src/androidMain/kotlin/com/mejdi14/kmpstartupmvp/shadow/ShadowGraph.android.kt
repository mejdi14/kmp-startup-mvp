package com.mejdi14.kmpstartupmvp.shadow

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Android owner of the Shadow Studio store, the counterpart of iOS's `ShadowGraph`. */
class ShadowGraph(context: Context) {
    val store = ShadowStore(
        ShadowApi(),
        SharedPreferencesSettings(context.getSharedPreferences("shadow", Context.MODE_PRIVATE)),
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
}
