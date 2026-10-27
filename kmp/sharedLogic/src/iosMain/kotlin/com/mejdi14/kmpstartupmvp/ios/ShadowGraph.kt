package com.mejdi14.kmpstartupmvp.ios

import com.mejdi14.kmpstartupmvp.shadow.ShadowApi
import com.mejdi14.kmpstartupmvp.shadow.ShadowStore
import com.russhwolf.settings.NSUserDefaultsSettings
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.posix.memcpy

/** iOS owner of the Shadow Studio store, like [AppGraph] for the boilerplate stores. */
class ShadowGraph {
    val store = ShadowStore(
        ShadowApi(),
        NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults),
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
}

/** Fast `NSData` <-> `ByteArray` copies for audio passed between Swift and the store. */
@OptIn(ExperimentalForeignApi::class)
fun bytesOf(data: NSData): ByteArray {
    val out = ByteArray(data.length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return out
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun dataOf(bytes: ByteArray): NSData =
    if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
