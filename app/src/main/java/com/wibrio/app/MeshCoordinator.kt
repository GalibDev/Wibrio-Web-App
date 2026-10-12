package com.wibrio.app

import com.wibrio.mesh.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.util.UUID

data class MeshIdentity(val node: String, val name: String, val signingKey: String, val encryptionKey: ByteArray, val session: String, val expires: Long, val issued: Long)

/** Owned by the repository's mutex; radio implementation never enters routing logic. */
class MeshCoordinator(
    private val local: String,
    private val identity: PacketSigningIdentity,
    private val name: String,
    private val messages: MutableStateFlow<List<DirectMessage>>,
    private val stats: MutableStateFlow<NetworkStats>,
    private val trusted: (String, String) -> Boolean,
    private val changed: () -> Unit,
    private val relayAllowed: () -> Boolean,
    private val journal: MessageJournal,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sendLink: suspend (String, RoutedFrame) -> Unit
) {
    private val encryption = MeshEncryption()
    private val meshSession = UUID.randomUUID().toString()
    private val key = identity.publicKey()
    val router = RoutingEngine(local, key, object : PacketAuthenticator {
        override fun seal(packet: MeshPacket) = packet.copy(signature = identity.sign(PacketCodec.signingBytes(packet)))
        override fun verify(packet: MeshPacket): Boolean = runCatching { packet.source == DeviceIdentity.nodeId(packet.publicKey) && DeviceIdentity.verify(packet.publicKey, PacketCodec.signingBytes(packet), packet.signature) }.getOrDefault(false)
    }, clock, relayAllowed)
    private val identities = linkedMapOf<String, MeshIdentity>()
    private val pending = linkedMapOf<String, PendingMessage>()
    private val receivedMessages = linkedMapOf<String, Long>()
    private var lastAnnounced = 0L
    suspend fun restore() {
        commit(journal.load().recovered(clock()))
    }
    private fun snapshot() = MessageSnapshot(messages.value, pending.values.toList(), receivedMessages.toMap())
    private suspend fun commit(snapshot: MessageSnapshot) {
        val bounded = snapshot.boundedHistory()
        journal.save(bounded)
        pending.clear(); bounded.pending.forEach { pending[it.id] = it }
        receivedMessages.clear(); receivedMessages.putAll(bounded.receipts)
        messages.value = bounded.messages
    }
    fun identity(node: String): MeshIdentity? = identities[node]
    fun peers(): List<Peer> = identities.values.filter { it.expires > clock() && router.routeTo(it.node) != null }.map {
        Peer(NodeId(it.node), it.name, trusted(it.node, it.signingKey), router.routeTo(it.node)!!.hops)
    }
    suspend fun up(node: String) { router.neighborUp(node); announce() }
    suspend fun down(node: String) { actions(router.neighborDown(node)); changed() }
    private suspend fun announce() {
        lastAnnounced = clock()
        val body = JSONObject().put("name", name).put("session", meshSession).put("encryption", DeviceIdentity.encode(encryption.publicKey())).toString().toByteArray()
        actions(router.originate(PacketType.PRESENCE, "", body)); changed()
    }
    suspend fun receive(incoming: String, frame: RoutedFrame) { actions(router.receive(incoming, frame)); changed() }
    private suspend fun actions(actions: List<RoutingAction>): Int {
        var sent = 0
        for (action in actions) when (action) {
            is RoutingAction.Send -> try { sendLink(action.nextHop, action.frame); sent++ }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { stats.value = stats.value.copy(failures = stats.value.failures + 1) }
            is RoutingAction.Deliver -> try { deliver(action.packet) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { stats.value = stats.value.copy(failures = stats.value.failures + 1) }
        }
        return sent
    }
    private suspend fun deliver(packet: MeshPacket) {
        when (packet.type) {
            PacketType.PRESENCE -> {
                val body = JSONObject(packet.payload.toString(Charsets.UTF_8))
                val session = body.getString("session"); require(UUID.fromString(session).toString() == session)
                val public = DeviceIdentity.decode(body.getString("encryption")); require(public.size in 1..2048)
                val previous = identities[packet.source]
                if ((previous == null || packet.createdAt >= previous.issued) && (identities.size < 128 || previous != null)) {
                    identities[packet.source] = MeshIdentity(packet.source, body.getString("name").take(40), packet.publicKey, public, session, packet.expiresAt, packet.createdAt)
                }
            }
            PacketType.DATA -> {
                if (packet.destination != local) return
                if (!trusted(packet.source, packet.publicKey)) return
                val context = MeshEncryption.context(packet.source, local, meshSession)
                val plain = encryption.decrypt(packet.payload, context)
                require(plain.size <= 5000)
                val body = JSONObject(plain.toString(Charsets.UTF_8))
                require(body.getString("toSession") == meshSession)
                val id = body.getString("id"); require(UUID.fromString(id).toString() == id)
                val text = body.getString("text"); require(text.isNotBlank() && text.toByteArray().size <= 3072)
                val dedup = "${packet.source}:$id"
                if (dedup !in receivedMessages) {
                    if (receivedMessages.size >= 1024) return // Never evict a live dedup entry to admit a replay.
                    commit(snapshot().copy(receipts = receivedMessages + (dedup to (clock() + 86_400_000)),
                        messages = messages.value + DirectMessage(id, packet.source, text, false, clock(), DeliveryState.DELIVERED)))
                }
                val ack = JSONObject().put("id", id).put("toSession", body.getString("fromSession")).toString().toByteArray()
                actions(router.originate(PacketType.ACK, packet.source, ack))
            }
            PacketType.ACK -> {
                if (packet.destination != local) return
                val body = JSONObject(packet.payload.toString(Charsets.UTF_8))
                if (body.optString("toSession") != meshSession) return
                val id = body.getString("id")
                require(UUID.fromString(id).toString() == id)
                val message = pending[id] ?: return
                if (message.node != packet.source || !trusted(packet.source, packet.publicKey)) return
                commit(snapshot().copy(pending = pending.values.filterNot { it.id == id }, messages = messages.value.map { if (it.id == id && it.outgoing && it.peerId == packet.source) it.copy(state = DeliveryState.DELIVERED) else it }))
                val latency = (clock() - message.created).coerceAtLeast(0)
                stats.value = stats.value.copy(acks = stats.value.acks + 1, lastDeliveryLatencyMs = latency, deliveryLatencyTotalMs = stats.value.deliveryLatencyTotalMs + latency)
            }
            PacketType.LINK_DOWN, PacketType.ROUTE_REQUEST -> Unit
        }
    }
    suspend fun send(node: String, text: String) {
        require(text.isNotBlank() && text.toByteArray().size <= 3072) { "Message must be 1–3072 UTF-8 bytes" }
        val peer = identities[node] ?: error("Peer identity is not available. Wait for discovery.")
        require(peer.expires > clock()) { "Peer session expired. Wait for it to reconnect." }
        require(trusted(node, peer.signingKey)) { "Verify the recipient fingerprint first." }
        require(pending.size < 32) { "Pending message queue is full" }
        val id = UUID.randomUUID().toString()
        require(JSONObject().put("id", id).put("text", text).put("fromSession", meshSession).put("toSession", peer.session).toString().toByteArray().size <= 5000) { "Escaped message is too large" }
        val message = PendingMessage(id, node, text, clock(), clock() + 60_000)
        commit(snapshot().copy(pending = pending.values + message, messages = messages.value + DirectMessage(id, node, text, true, clock(), DeliveryState.QUEUED)))
        attempt(message)
    }
    private suspend fun attempt(message: PendingMessage) {
        if (message.attempts >= 5) return
        val peer = identities[message.node] ?: return
        if (peer.expires <= clock() || !trusted(message.node, peer.signingKey)) return
        // Fresh endpoint sessions after either app restarts: retain logical UUID and re-encrypt.
        val plain = JSONObject().put("id", message.id).put("text", message.text).put("fromSession", meshSession).put("toSession", peer.session).toString().toByteArray()
        val cipher = MeshEncryption.encrypt(peer.encryptionKey, plain, MeshEncryption.context(local, message.node, peer.session))
        require(cipher.size <= 6144)
        val packet = router.create(PacketType.DATA, message.node, cipher, lifetime = (message.deadline - clock()).coerceIn(1, 60_000))
        val actions = router.send(packet)
        val attempted = message.copy(lastAttempt = clock(), attempts = message.attempts + if (actions.isNotEmpty()) 1 else 0)
        commit(snapshot().copy(pending = pending.values.map { if (it.id == message.id) attempted else it },
            messages = messages.value.map { if (it.id == message.id && it.outgoing) it.copy(state = if (actions.isEmpty()) DeliveryState.QUEUED else DeliveryState.SENDING) else it }))
        if (actions.isNotEmpty() && attempted.attempts > 1) stats.value = stats.value.copy(retries = stats.value.retries + 1)
        val successful = actions(actions)
        val state = if (successful == 0) DeliveryState.QUEUED else if (actions.filterIsInstance<RoutingAction.Send>().none { it.nextHop == message.node }) DeliveryState.RELAYED else DeliveryState.SENDING
        commit(snapshot().copy(messages = messages.value.map { if (it.id == message.id && it.outgoing && it.state != DeliveryState.DELIVERED) it.copy(state = state) else it }))
    }
    suspend fun tick() {
        router.expire()
        identities.entries.removeAll { it.value.expires <= clock() }
        receivedMessages.entries.removeAll { it.value <= clock() }
        if (clock() - lastAnnounced >= 20_000) announce()
        pending.values.toList().forEach { message ->
            if (message.deadline <= clock()) {
                commit(snapshot().copy(pending = pending.values.filterNot { it.id == message.id }, messages = messages.value.map { if (it.id == message.id && it.outgoing) it.copy(state = DeliveryState.EXPIRED) else it }))
            } else if (clock() - message.lastAttempt >= 10_000) attempt(message)
        }
        changed()
    }
}
