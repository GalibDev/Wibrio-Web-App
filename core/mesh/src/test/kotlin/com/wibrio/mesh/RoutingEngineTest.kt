package com.wibrio.mesh

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.ArrayDeque

class RoutingEngineTest {
    // Deterministic fixture only: production uses Android Keystore ECDSA, not this digest.
    private val auth = object : PacketAuthenticator {
        override fun seal(packet: MeshPacket) = packet.copy(signature = digest(packet))
        override fun verify(packet: MeshPacket) = packet.signature == digest(packet)
        fun digest(packet: MeshPacket) = MessageDigest.getInstance("SHA-256").digest(PacketCodec.signingBytes(packet)).joinToString("") { "%02x".format(it) }
    }
    private inner class Network(var time: Long = 1000) {
        val nodes = linkedMapOf<String, RoutingEngine>()
        val links = mutableSetOf<Set<String>>()
        val queue = ArrayDeque<Pair<String, RoutingAction>>()
        val delivered = mutableListOf<Pair<String, MeshPacket>>()
        fun node(id: String) { nodes[id] = RoutingEngine(id, "key-$id", auth, now = { time }) }
        fun connect(a: String, b: String) { links += setOf(a, b); nodes.getValue(a).neighborUp(b); nodes.getValue(b).neighborUp(a) }
        fun submit(from: String, actions: List<RoutingAction>) { actions.forEach { queue.add(from to it) } }
        fun cut(a: String, b: String) { links -= setOf(a, b); submit(a, nodes.getValue(a).neighborDown(b)); submit(b, nodes.getValue(b).neighborDown(a)); drain() }
        fun drain() {
            var steps = 0
            while (queue.isNotEmpty()) {
                check(++steps < 4096) { "Forwarding did not terminate" }
                val (from, action) = queue.removeFirst()
                when (action) {
                    is RoutingAction.Deliver -> delivered += from to action.packet
                    is RoutingAction.Send -> if (setOf(from, action.nextHop) in links) submit(action.nextHop, nodes.getValue(action.nextHop).receive(from, PacketCodec.decode(PacketCodec.encode(action.frame))))
                }
            }
        }
        fun announce() { nodes.forEach { (id, engine) -> submit(id, engine.originate(PacketType.PRESENCE, "", id.toByteArray())) }; drain() }
        fun send(from: String, to: String, hops: Int = 8): MeshPacket {
            val packet = nodes.getValue(from).create(PacketType.DATA, to, "ciphertext".toByteArray(), hopLimit = hops)
            submit(from, nodes.getValue(from).send(packet)); drain(); return packet
        }
    }
    @Test fun fourNodeLineForwardsOnlyToFinalRecipientAndLearnsThreeHops() {
        val n = Network(); listOf("A", "B", "C", "D").forEach(n::node)
        n.connect("A", "B"); n.connect("B", "C"); n.connect("C", "D"); n.announce()
        assertEquals(3, n.nodes.getValue("A").routeTo("D")!!.hops)
        val packet = n.send("A", "D")
        assertEquals(listOf("D"), n.delivered.filter { it.second.id == packet.id }.map { it.first })
        val ack = n.nodes.getValue("D").create(PacketType.ACK, "A", packet.id.toByteArray())
        n.submit("D", n.nodes.getValue("D").send(ack)); n.drain()
        assertEquals(listOf("A"), n.delivered.filter { it.second.id == ack.id }.map { it.first })
    }
    @Test fun diamondDeduplicatesAndRecoversWhenPrimaryRelayDisconnects() {
        val n = Network(); listOf("A", "B", "C", "D", "E").forEach(n::node)
        n.connect("A", "B"); n.connect("B", "C"); n.connect("C", "D"); n.connect("A", "E"); n.connect("E", "C"); n.announce()
        assertEquals("B", n.nodes.getValue("A").routeTo("D")!!.nextHop)
        n.cut("A", "B"); n.cut("B", "C")
        val packet = n.send("A", "D")
        assertEquals("E", n.nodes.getValue("A").routeTo("D")!!.nextHop)
        assertEquals(1, n.delivered.count { it.first == "D" && it.second.id == packet.id })
        assertTrue(n.nodes.values.sumOf { it.metrics.duplicates } > 0)
    }
    @Test fun floodDiscoversRouteAfterPartitionHealsWithoutInventingDelivery() {
        val n = Network(); listOf("A", "B", "C").forEach(n::node); n.connect("A", "B")
        val lost = n.send("A", "C"); assertFalse(n.delivered.any { it.second.id == lost.id })
        n.connect("B", "C"); val retry = n.send("A", "C")
        assertTrue(n.delivered.any { it.first == "C" && it.second.id == retry.id })
    }
    @Test fun hopLimitStopsDeliveryBeyondPermittedRange() {
        val n = Network(); listOf("A", "B", "C").forEach(n::node); n.connect("A", "B"); n.connect("B", "C")
        val packet = n.send("A", "C", 1); assertFalse(n.delivered.any { it.second.id == packet.id })
    }
    @Test fun malformedExpiredUnauthenticatedAndLoopedFramesAreRejected() {
        val n = Network(); n.node("A"); n.node("B"); n.connect("A", "B")
        val packet = n.nodes.getValue("A").create(PacketType.DATA, "B", byteArrayOf(1))
        val b = n.nodes.getValue("B")
        assertTrue(b.receive("stranger", RoutedFrame(packet, listOf("A"))).isEmpty())
        assertTrue(b.receive("A", RoutedFrame(packet.copy(payload = byteArrayOf(9)), listOf("A"))).isEmpty())
        assertTrue(b.receive("A", RoutedFrame(packet, listOf("A", "B", "A"))).isEmpty())
        n.time += 120_000
        assertTrue(b.receive("A", RoutedFrame(packet, listOf("A"))).isEmpty())
        assertEquals(4, b.metrics.rejected)
    }
    @Test fun duplicatePacketsDeliverOnceAndStaleRoutesExpire() {
        val n = Network(); listOf("A", "B", "C").forEach(n::node); n.connect("A", "B"); n.connect("B", "C"); n.announce()
        val packet = n.nodes.getValue("A").create(PacketType.DATA, "B", byteArrayOf(1))
        val frame = RoutedFrame(packet, listOf("A"))
        assertEquals(1, n.nodes.getValue("B").receive("A", frame).filterIsInstance<RoutingAction.Deliver>().size)
        assertTrue(n.nodes.getValue("B").receive("A", frame).isEmpty())
        n.time += 120_001
        assertNull(n.nodes.getValue("A").routeTo("C")); assertNotNull(n.nodes.getValue("A").routeTo("B"))
    }
    @Test fun codecRejectsOversizeTruncationAndTrailingGarbage() {
        val n = Network(); n.node("A")
        val frame = RoutedFrame(n.nodes.getValue("A").create(PacketType.DATA, "B", byteArrayOf(1)), listOf("A"))
        val bytes = PacketCodec.encode(frame)
        assertEquals(frame.packet.id, PacketCodec.decode(bytes).packet.id)
        listOf(ByteArray(9001), bytes.copyOf(3), bytes + byteArrayOf(0)).forEach { assertTrue(runCatching { PacketCodec.decode(it) }.isFailure) }
    }
    @Test fun duplicateIdsFromDifferentSignedOriginsDoNotSuppressEachOther() {
        val n = Network(); listOf("A", "B", "C").forEach(n::node); n.connect("A", "B"); n.connect("C", "B")
        val a = n.nodes.getValue("A").create(PacketType.DATA, "B", byteArrayOf(1))
        val c = auth.seal(n.nodes.getValue("C").create(PacketType.DATA, "B", byteArrayOf(2)).copy(id = a.id))
        assertEquals(1, n.nodes.getValue("B").receive("A", RoutedFrame(a, listOf("A"))).size)
        assertEquals(1, n.nodes.getValue("B").receive("C", RoutedFrame(c, listOf("C"))).size)
    }
    @Test fun relayOptOutPreventsForwardingButAllowsLocalDelivery() {
        val a = RoutingEngine("A", "key-A", auth, { 1000 })
        val b = RoutingEngine("B", "key-B", auth, { 1000 }, { false })
        a.neighborUp("B"); b.neighborUp("A"); b.neighborUp("C")
        val remote = a.create(PacketType.DATA, "C", byteArrayOf(1))
        assertTrue(b.receive("A", RoutedFrame(remote, listOf("A"))).isEmpty())
        val local = a.create(PacketType.DATA, "B", byteArrayOf(1))
        assertEquals(1, b.receive("A", RoutedFrame(local, listOf("A"))).filterIsInstance<RoutingAction.Deliver>().size)
    }
}
