package com.wibrio.app

import com.wibrio.mesh.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.ArrayDeque

class MeshCoordinatorTest {
    private class TestIdentity : PacketSigningIdentity {
        private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        override fun publicKey() = DeviceIdentity.encode(keys.public.encoded)
        override fun sign(bytes: ByteArray) = DeviceIdentity.encode(Signature.getInstance("SHA256withECDSA").run { initSign(keys.private); update(bytes); sign() })
        val id get() = DeviceIdentity.nodeId(publicKey())
    }
    private data class Wire(val from: String, val to: String, val frame: RoutedFrame)
    private class Network {
        var time = 1000L
        var dropRecipientAck = false
        var trustRemote = true
        var failOriginSends = false
        val keys = listOf("A", "B", "C", "D", "E").associateWith { TestIdentity() }
        val history = keys.mapValues { MutableStateFlow<List<DirectMessage>>(emptyList()) }
        val links = mutableSetOf<Set<String>>()
        val queue = ArrayDeque<Wire>()
        val captured = mutableListOf<Wire>()
        val nodes = keys.mapValues { (name, key) -> MeshCoordinator(key.id, key, name, history.getValue(name), MutableStateFlow(NetworkStats()), { _, _ -> trustRemote }, {}, { true }, { time }) { next, frame ->
            if (failOriginSends && name == "A" && frame.packet.type == PacketType.DATA) throw java.io.IOException("simulated radio send failure")
            val to = keys.entries.first { it.value.id == next }.key
            queue.add(Wire(name, to, PacketCodec.decode(PacketCodec.encode(frame))))
        } }
        suspend fun connect(a: String, b: String) { links += setOf(a, b); nodes.getValue(a).up(keys.getValue(b).id); nodes.getValue(b).up(keys.getValue(a).id) }
        suspend fun cut(a: String, b: String) { links -= setOf(a, b); nodes.getValue(a).down(keys.getValue(b).id); nodes.getValue(b).down(keys.getValue(a).id); drain() }
        suspend fun drain() {
            var count = 0
            while (queue.isNotEmpty()) {
                check(++count < 4096)
                val wire = queue.removeFirst(); captured += wire
                if (setOf(wire.from, wire.to) !in links) continue
                if (dropRecipientAck && wire.frame.packet.type == PacketType.ACK && wire.frame.packet.source == keys.getValue("D").id) continue
                nodes.getValue(wire.to).receive(keys.getValue(wire.from).id, wire.frame)
            }
        }
        suspend fun line() { connect("A", "B"); connect("B", "C"); connect("C", "D"); drain(); time += 20_001; nodes.values.forEach { it.tick() }; drain() }
    }
    @Test fun encryptedThreeHopTextReachesOnlyRecipientAndFinalAckDelivers() = runBlocking {
        val n = Network(); n.line()
        n.nodes.getValue("A").send(n.keys.getValue("D").id, "private Bangla বাংলা")
        n.drain()
        assertEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.single().state)
        assertEquals("private Bangla বাংলা", n.history.getValue("D").value.single().text)
        assertTrue(n.history.getValue("B").value.isEmpty()); assertTrue(n.history.getValue("C").value.isEmpty())
        assertTrue(n.captured.filter { it.frame.packet.type == PacketType.DATA }.all { !it.frame.packet.payload.toString(Charsets.UTF_8).contains("private Bangla") })
    }
    @Test fun lostAckRetriesOnceWithoutDuplicateTextAndRelayCannotAcknowledgeRecipient() = runBlocking {
        val n = Network(); n.line(); n.dropRecipientAck = true
        n.nodes.getValue("A").send(n.keys.getValue("D").id, "only once"); n.drain()
        assertNotEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.single().state)
        val realAck = n.captured.first { it.frame.packet.type == PacketType.ACK }.frame.packet
        val relay = n.nodes.getValue("B").router.originate(PacketType.ACK, n.keys.getValue("A").id, realAck.payload)
        relay.filterIsInstance<RoutingAction.Send>().forEach { action -> n.queue.add(Wire("B", n.keys.entries.first { it.value.id == action.nextHop }.key, action.frame)) }
        n.drain()
        assertNotEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.single().state)
        n.dropRecipientAck = false; n.time += 10_001; n.nodes.getValue("A").tick(); n.drain()
        assertEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.single().state)
        assertEquals(1, n.history.getValue("D").value.size)
    }
    @Test fun queuedMessageRecoversOverAlternativePathAndUnapprovedSenderIsRejected() = runBlocking {
        val n = Network(); n.line(); n.cut("A", "B")
        n.nodes.getValue("A").send(n.keys.getValue("D").id, "recover")
        assertEquals(DeliveryState.QUEUED, n.history.getValue("A").value.single().state)
        n.connect("A", "E"); n.connect("E", "C"); n.drain(); n.time += 10_001; n.nodes.getValue("A").tick(); n.drain()
        assertEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.single().state)
        assertEquals("recover", n.history.getValue("D").value.single().text)
        n.trustRemote = false
        assertTrue(runCatching { n.nodes.getValue("A").send(n.keys.getValue("D").id, "unapproved") }.isFailure)
    }
    @Test fun partitionedPendingMessageExpiresWithoutDeliveryClaim() = runBlocking {
        val n = Network(); n.line(); n.cut("A", "B")
        n.nodes.getValue("A").send(n.keys.getValue("D").id, "will expire")
        n.time += 60_001; n.nodes.getValue("A").tick(); n.drain()
        assertEquals(DeliveryState.EXPIRED, n.history.getValue("A").value.single().state)
        assertTrue(n.history.getValue("D").value.isEmpty())
    }
    @Test fun failedRadioSendDoesNotClaimRelayedOrDeliveredAndCanRetry() = runBlocking {
        val n = Network(); n.line(); n.failOriginSends = true
        n.nodes.getValue("A").send(n.keys.getValue("D").id, "retry radio")
        assertEquals(DeliveryState.QUEUED, n.history.getValue("A").value.single().state)
        assertTrue(n.history.getValue("D").value.isEmpty())
        n.failOriginSends = false; n.time += 10_001; n.nodes.getValue("A").tick(); n.drain()
        assertEquals(DeliveryState.DELIVERED, n.history.getValue("A").value.single().state)
    }
}
