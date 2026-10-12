package com.wibrio.app

import com.wibrio.mesh.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.ArrayDeque

/** Real coordinator, P-256 signatures and Tink encryption; simulated transport exists only in tests. */
class SimulatedIdentity : PacketSigningIdentity {
    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    override fun publicKey() = DeviceIdentity.encode(keys.public.encoded)
    override fun sign(bytes: ByteArray) = DeviceIdentity.encode(Signature.getInstance("SHA256withECDSA").run { initSign(keys.private); update(bytes); sign() })
    val id get() = DeviceIdentity.nodeId(publicKey())
}
data class SimulatedWire(val from: String, val to: String, val frame: RoutedFrame)
open class SimulatedMesh(size: Int = 5) {
    var time = 1000L
    var dropRecipientAck = false
    var trustRemote = true
    var failOriginSends = false
    var duplicatePackets = false
    val keys = (0 until size).map { ('A'.code + it).toChar().toString() }.associateWith { SimulatedIdentity() }
    val history = keys.mapValues { MutableStateFlow<List<DirectMessage>>(emptyList()) }
    val stats = keys.mapValues { MutableStateFlow(NetworkStats()) }
    val journals = keys.mapValues { TestMessageJournal() }
    val relay = keys.mapValues { true }.toMutableMap()
    val links = mutableSetOf<Set<String>>()
    val queue = ArrayDeque<SimulatedWire>()
    val captured = mutableListOf<SimulatedWire>()
    private fun coordinator(name: String) = keys.getValue(name).let { key ->
        MeshCoordinator(key.id, key, name, history.getValue(name), stats.getValue(name), { _, _ -> trustRemote }, {}, { relay.getValue(name) }, journals.getValue(name), { time }) { next, frame ->
            if (failOriginSends && name == "A" && frame.packet.type == PacketType.DATA) throw java.io.IOException("simulated radio send failure")
            val to = keys.entries.first { it.value.id == next }.key
            if (setOf(name, to) !in links) throw java.io.IOException("disconnected simulated link")
            queue.add(SimulatedWire(name, to, PacketCodec.decode(PacketCodec.encode(frame))))
        }
    }
    val nodes = keys.mapValues { (name, _) -> coordinator(name) }.toMutableMap()
    suspend fun connect(a: String, b: String) { links += setOf(a, b); nodes.getValue(a).up(keys.getValue(b).id); nodes.getValue(b).up(keys.getValue(a).id) }
    suspend fun cut(a: String, b: String) { links -= setOf(a, b); nodes.getValue(a).down(keys.getValue(b).id); nodes.getValue(b).down(keys.getValue(a).id); drain() }
    suspend fun restart(name: String) {
        links.filter { name in it }.toList().forEach { link -> cut(name, link.first { it != name }) }
        history.getValue(name).value = emptyList()
        nodes[name] = coordinator(name).also { it.restore() }
    }
    suspend fun drain() {
        var count = 0
        while (queue.isNotEmpty()) {
            check(++count < 20_000) { "Simulated forwarding failed to terminate" }
            val wire = queue.removeFirst(); captured += wire
            if (setOf(wire.from, wire.to) !in links) continue
            if (dropRecipientAck && wire.frame.packet.type == PacketType.ACK && wire.frame.packet.source == keys.getValue("D").id) continue
            nodes.getValue(wire.to).receive(keys.getValue(wire.from).id, wire.frame)
            if (duplicatePackets) nodes.getValue(wire.to).receive(keys.getValue(wire.from).id, wire.frame)
        }
    }
    suspend fun refresh() { time += 20_001; nodes.values.forEach { it.tick(); drain() } }
    suspend fun line() { connect("A", "B"); connect("B", "C"); connect("C", "D"); drain(); refresh() }
    suspend fun send(from: String, to: String, text: String) { nodes.getValue(from).send(keys.getValue(to).id, text) }
}
