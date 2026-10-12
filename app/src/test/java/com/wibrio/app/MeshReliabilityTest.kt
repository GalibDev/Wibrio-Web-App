package com.wibrio.app

import com.wibrio.mesh.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MeshSizeTest(private val size: Int) {
    companion object { @JvmStatic @Parameterized.Parameters(name = "{0} nodes") fun sizes() = listOf(arrayOf(4), arrayOf(5), arrayOf(10)) }
    @Test fun simultaneousEndpointAndRelayTrafficWithDuplicatesAndAlternativeRoute() = runBlocking {
        val n = SimulatedMesh(size)
        val names = n.keys.keys.toList()
        // Ten-node network has seven-hop maximum shortest paths; protocol retains its eight-hop limit.
        names.zipWithNext().forEach { (a, b) -> n.connect(a, b); n.drain() }
        if (size == 10) { n.connect("B", "E"); n.drain() }
        if (size >= 5) { n.connect("A", "E"); n.drain() }
        n.refresh()
        n.duplicatePackets = true
        n.send("A", names.last(), "edge to edge")
        n.send(names.last(), "A", "return traffic")
        n.send("B", "C", "relay is also an endpoint")
        n.drain()
        assertTrue(n.history.getValue("A").value.any { !it.outgoing && it.text == "return traffic" })
        assertTrue(n.history.getValue("C").value.any { !it.outgoing && it.text == "relay is also an endpoint" })
        assertEquals(1, n.history.getValue(names.last()).value.count { !it.outgoing && it.text == "edge to edge" })
        assertTrue(n.history.values.flatMap { it.value }.filter { it.outgoing }.all { it.state == DeliveryState.DELIVERED })
        assertTrue(n.nodes.values.sumOf { it.router.metrics.duplicates } > 0)
        if (size >= 5) {
            n.cut("A", "B"); n.send("A", names.last(), "survive route cut"); n.drain()
            assertEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.last().state)
        }
    }
}

class MeshRecoveryTest {
    @Test fun signedBroadcastAckFromRecipientIsNotAnAddressedDeliveryReceipt() = runBlocking {
        val n = SimulatedMesh(); n.line(); n.dropRecipientAck = true
        n.send("A", "D", "requires addressed ACK"); n.drain()
        val ack = n.captured.first { it.frame.packet.type == PacketType.ACK }.frame.packet
        n.dropRecipientAck = false
        n.nodes.getValue("D").router.originate(PacketType.ACK, "", ack.payload).filterIsInstance<RoutingAction.Send>().forEach { action ->
            n.queue.add(SimulatedWire("D", n.keys.entries.first { it.value.id == action.nextHop }.key, action.frame))
        }
        n.drain()
        assertNotEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.single().state)
        n.time += 10_001; n.nodes.getValue("A").tick(); n.drain()
        assertEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.single().state)
    }
    @Test fun persistentOutboxIsBoundedAndExpiryFreesCapacity() = runBlocking {
        val n = SimulatedMesh(); n.line(); n.cut("A", "B")
        repeat(32) { n.send("A", "D", "queued $it") }
        assertTrue(runCatching { n.send("A", "D", "over limit") }.isFailure)
        assertEquals(32, n.journals.getValue("A").load().pending.size)
        n.time += 60_001; n.nodes.getValue("A").tick()
        assertTrue(n.journals.getValue("A").load().pending.isEmpty())
        assertTrue(n.history.getValue("A").value.all { it.state == DeliveryState.EXPIRED })
    }
    @Test fun oversizedEscapedTextIsRejectedBeforeQueueWrite() = runBlocking {
        val n = SimulatedMesh(); n.line()
        assertTrue(runCatching { n.send("A", "D", "\u0001".repeat(1000)) }.isFailure)
        assertTrue(n.journals.getValue("A").load().pending.isEmpty())
        n.send("A", "D", "normal text still works"); n.drain()
        assertEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.single().state)
    }
    @Test fun senderRestartRetainsQueueIdDeadlineAndReencryptsForFreshSessions() = runBlocking {
        val n = SimulatedMesh(); n.line(); n.cut("A", "B")
        n.send("A", "D", "durable after restart")
        val id = n.history.getValue("A").value.single().id
        n.restart("A"); n.restart("D")
        assertEquals(id, n.history.getValue("A").value.single().id)
        n.connect("A", "B"); n.connect("C", "D"); n.drain(); n.refresh(); n.drain()
        assertEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.single().state)
        assertEquals(id, n.history.getValue("D").value.single().id)
    }
    @Test fun recipientRestartAfterLostAckDoesNotDuplicateDurablyAcceptedText() = runBlocking {
        val n = SimulatedMesh(); n.line(); n.dropRecipientAck = true
        n.send("A", "D", "exactly one stored copy"); n.drain()
        assertEquals(1, n.history.getValue("D").value.size)
        n.restart("D"); n.connect("C", "D"); n.drain()
        n.dropRecipientAck = false; n.refresh(); n.drain()
        assertEquals(1, n.history.getValue("D").value.size)
        assertEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.single().state)
    }
    @Test fun expiredQueueRemainsExpiredOnRestartAndCannotExtendDeadline() = runBlocking {
        val n = SimulatedMesh(); n.line(); n.cut("A", "B"); n.send("A", "D", "old")
        val deadline = n.journals.getValue("A").load().pending.single().deadline
        n.time = deadline + 1; n.restart("A")
        assertEquals(DeliveryState.EXPIRED, n.history.getValue("A").value.single().state)
        assertTrue(n.journals.getValue("A").load().pending.isEmpty())
    }
    @Test fun storageFailureNeverAcknowledgesOrPublishesUndurableReceipt() = runBlocking {
        val n = SimulatedMesh(); n.line(); n.journals.getValue("D").failWrites = true
        n.send("A", "D", "must persist first"); n.drain()
        assertTrue(n.history.getValue("D").value.isEmpty())
        assertNotEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.single().state)
        n.journals.getValue("D").failWrites = false; n.time += 10_001; n.nodes.getValue("A").tick(); n.drain()
        assertEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.single().state)
    }
    @Test fun failedOutboxWriteSendsNothing() = runBlocking {
        val n = SimulatedMesh(); n.line(); n.captured.clear(); n.journals.getValue("A").failWrites = true
        assertTrue(runCatching { n.send("A", "D", "no durable outbox") }.isFailure)
        n.drain()
        assertTrue(n.captured.none { it.frame.packet.type == PacketType.DATA })
        assertTrue(n.history.getValue("A").value.isEmpty())
    }
    @Test fun oldSessionDataReplayAfterRecipientRestartIsRejected() = runBlocking {
        val n = SimulatedMesh(); n.line(); n.send("A", "D", "cannot replay"); n.drain()
        val old = n.captured.first { it.to == "D" && it.frame.packet.type == PacketType.DATA }
        n.restart("D"); n.connect("C", "D"); n.drain()
        n.nodes.getValue("D").receive(n.keys.getValue("C").id, old.frame)
        assertEquals(1, n.history.getValue("D").value.size)
        assertTrue(n.stats.getValue("D").value.failures > 0)
    }
    @Test fun relayOptOutBlocksTransitButCanStillSendAndReceiveOwnMessages() = runBlocking {
        val n = SimulatedMesh(); n.line(); n.relay["B"] = false
        n.send("A", "D", "blocked transit"); n.send("B", "C", "my own text"); n.drain()
        assertTrue(n.history.getValue("D").value.isEmpty())
        assertEquals(DeliveryState.DELIVERED, n.history.getValue("B").value.single().state)
    }
    @Test fun retryBudgetSurvivesRestart() = runBlocking {
        val n = SimulatedMesh(); n.line(); n.dropRecipientAck = true
        n.send("A", "D", "bounded retries"); n.drain()
        repeat(4) { n.time += 10_001; n.nodes.getValue("A").tick(); n.drain() }
        assertEquals(5, n.journals.getValue("A").load().pending.single().attempts)
        n.restart("A"); n.connect("A", "B"); n.drain(); n.captured.clear()
        n.time += 10_001; n.nodes.getValue("A").tick(); n.drain()
        assertTrue(n.captured.none { it.from == "A" && it.frame.packet.type == PacketType.DATA })
        assertNotEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.single().state)
    }
}
