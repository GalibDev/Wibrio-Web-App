package com.wibrio.mesh

import org.junit.Assert.*
import org.junit.Test

class ReplayWindowTest {
    @Test fun duplicateAndInvalidSequencesAreRejected() {
        val window = ReplayWindow()
        assertFalse(window.accept(0)); assertFalse(window.accept(-1))
        assertTrue(window.accept(1)); assertFalse(window.accept(1))
    }
    @Test fun legitimateReorderingWithinWindowIsAcceptedOnce() {
        val window = ReplayWindow()
        assertTrue(window.accept(3)); assertTrue(window.accept(1)); assertTrue(window.accept(2))
        assertFalse(window.accept(2)); assertTrue(window.accept(4))
    }
    @Test fun oldPacketsStayRejectedAfterWindowAdvances() {
        val window = ReplayWindow()
        assertTrue(window.accept(1)); assertTrue(window.accept(65))
        assertFalse(window.accept(1)); assertTrue(window.accept(2)); assertFalse(window.accept(2))
        assertTrue(window.accept(Long.MAX_VALUE)); assertFalse(window.accept(65))
    }
}
