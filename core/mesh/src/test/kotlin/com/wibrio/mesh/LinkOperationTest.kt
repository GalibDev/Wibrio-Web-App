package com.wibrio.mesh

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

class LinkOperationTest {
    @Test fun pendingRequestAllowsVerificationAndAcceptance() = runBlocking {
        val mutex = Mutex()
        val requestStarted = CompletableDeferred<Unit>()
        val accepted = CompletableDeferred<Unit>()
        var failure: Exception? = null
        val request = mutex.withLock {
            launchLinkOperation(this, mutex, { true }, { failure = it }) {
                requestStarted.complete(Unit)
                accepted.await()
            }
        }
        withTimeout(1000) {
            requestStarted.await()
            // Models a verification callback needing the same repository lock.
            mutex.withLock { accepted.complete(Unit) }
            request.join()
        }
        assertNull(failure)
    }

    @Test fun oldAttemptFailureCannotRemoveReplacementSession() = runBlocking {
        val mutex = Mutex()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var current = true
        var removed = false
        val request = launchLinkOperation(this, mutex, { current }, { removed = true }) {
            started.complete(Unit)
            finish.await()
            throw java.io.IOException("Old radio task failed")
        }
        withTimeout(1000) {
            started.await()
            mutex.withLock { current = false }
            finish.complete(Unit)
            request.join()
        }
        assertFalse(removed)
    }

    @Test fun currentAttemptFailureIsReportedUnderStateLock() = runBlocking {
        val mutex = Mutex()
        var reported = false
        launchLinkOperation(this, mutex, { true }, {
            assertTrue(mutex.isLocked)
            reported = true
        }) { throw java.io.IOException("Radio unavailable") }.join()
        assertTrue(reported)
    }

    @Test fun stoppedAttemptDoesNotStartRadioRequest() = runBlocking {
        var invoked = false
        launchLinkOperation(this, Mutex(), { false }, { fail("Unexpected failure") }) { invoked = true }.join()
        assertFalse(invoked)
    }
}
