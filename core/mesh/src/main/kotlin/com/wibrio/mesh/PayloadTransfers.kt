package com.wibrio.mesh

import kotlinx.coroutines.CompletableDeferred
import java.io.IOException

/** Tracks only our outbound bytes. Nearby task acceptance is not transfer completion. */
class PayloadTransfers {
    private data class Transfer(val endpoint: String, val done: CompletableDeferred<Unit>)
    private val pending = mutableMapOf<Long, Transfer>()
    @Synchronized fun register(endpoint: String, id: Long): CompletableDeferred<Unit> {
        check(pending.size < 64 && id !in pending)
        return CompletableDeferred<Unit>().also { pending[id] = Transfer(endpoint, it) }
    }
    @Synchronized fun complete(endpoint: String, id: Long, success: Boolean) {
        val transfer = pending[id] ?: return
        if (transfer.endpoint != endpoint) return
        pending.remove(id)
        if (success) transfer.done.complete(Unit) else transfer.done.completeExceptionally(IOException("Nearby payload transfer failed"))
    }
    @Synchronized fun forget(id: Long) { pending.remove(id)?.done?.cancel() }
    @Synchronized fun disconnect(endpoint: String) {
        pending.filterValues { it.endpoint == endpoint }.keys.toList().forEach { complete(endpoint, it, false) }
    }
    @Synchronized fun stop() { pending.values.forEach { it.done.completeExceptionally(IOException("Nearby session stopped")) }; pending.clear() }
}
