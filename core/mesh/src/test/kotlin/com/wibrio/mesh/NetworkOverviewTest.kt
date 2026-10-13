package com.wibrio.mesh

import org.junit.Assert.*
import org.junit.Test

class NetworkOverviewTest {
    private fun peer(node: String, hops: Int?, verified: Boolean = true) = Peer(NodeId(node), node, verified, hops)
    @Test fun currentConnectionsDetermineCounts() {
        val overview = NetworkOverview.from(listOf(peer("B", 1), peer("C", 2), peer("D", 3, false)))
        assertEquals(3, overview.reachable); assertEquals(1, overview.direct); assertEquals(2, overview.relayed)
    }
    @Test fun directRouteOverridesDuplicateRelayedIdentity() {
        val overview = NetworkOverview.from(listOf(peer("B", 3, false), peer("B", 1), peer("C", 2)))
        assertEquals(2, overview.reachable); assertEquals(1, overview.direct)
        assertTrue(overview.peers.first { it.id.value == "B" }.verified)
    }
    @Test fun disconnectedAndInvalidRoutesDoNotCount() {
        val overview = NetworkOverview.from(listOf(peer("B", null), peer("C", 0), peer("D", 9)))
        assertEquals(0, overview.reachable); assertEquals(0, overview.direct); assertEquals(0, overview.relayed)
    }
    @Test fun removalIsReflectedImmediately() {
        assertEquals(1, NetworkOverview.from(listOf(peer("B", 1))).direct)
        assertEquals(0, NetworkOverview.from(emptyList()).direct)
    }
}
