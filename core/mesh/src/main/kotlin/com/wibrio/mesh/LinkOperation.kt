package com.wibrio.mesh

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Await radio tasks outside the state lock so verification callbacks can progress. */
fun launchLinkOperation(
    scope: CoroutineScope,
    mutex: Mutex,
    isCurrent: () -> Boolean,
    onFailure: suspend (Exception) -> Unit,
    operation: suspend () -> Unit
) = scope.launch {
    // Recheck after the caller releases its lock; a stop may have invalidated this attempt.
    if (!mutex.withLock { isCurrent() }) return@launch
    try { operation() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) { mutex.withLock { if (isCurrent()) onFailure(failure) } }
}
