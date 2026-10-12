package com.wibrio.mesh

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PayloadTransfersTest {
    @Test fun onlyMatchingEndpointCompletionCountsAndDuplicatesAreHarmless() = runBlocking {
        val tracker = PayloadTransfers(); val sent = tracker.register("B", 1)
        tracker.complete("C", 1, true); assertFalse(sent.isCompleted)
        tracker.complete("B", 1, true); sent.await()
        tracker.complete("B", 1, false); assertFalse(sent.isCancelled)
    }
    @Test fun disconnectFailsOnlyThatPeersPendingTransfers() = runBlocking {
        val tracker = PayloadTransfers(); val b = tracker.register("B", 1); val c = tracker.register("C", 2)
        tracker.disconnect("B"); assertTrue(runCatching { b.await() }.isFailure); assertFalse(c.isCompleted)
        tracker.complete("C", 2, true); c.await()
    }
    @Test fun failedTransferAndSessionStopNeverCompleteSuccessfully() = runBlocking {
        val tracker = PayloadTransfers(); val b = tracker.register("B", 1)
        tracker.complete("B", 1, false); assertTrue(runCatching { b.await() }.isFailure)
        val c = tracker.register("C", 2); tracker.stop(); assertTrue(runCatching { c.await() }.isFailure)
    }
}
