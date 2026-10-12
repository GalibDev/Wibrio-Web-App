package com.wibrio.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.wibrio.mesh.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredPeer(val endpoint: String, val name: String, val state: String = "Nearby")
data class VerificationRequest(val endpoint: String, val name: String, val digits: String)
data class DirectMessage(val id: String, val peerId: String, val text: String, val outgoing: Boolean, val timestamp: Long, val state: DeliveryState)
data class NetworkStats(val sent: Int = 0, val received: Int = 0, val acks: Int = 0, val failures: Int = 0, val retries: Int = 0)
private data class Session(val localNonce: String = UUID.randomUUID().toString(), var remoteNonce: String? = null, var key: String? = null, var node: String? = null, var name: String = "", var accepted: Boolean = false, var verified: Boolean = false, var window: Long = 0, var received: Int = 0, var outgoingSequence: Long = 0, val replay: ReplayWindow = ReplayWindow(), var pendingProof: ByteArray? = null, val deferred: MutableList<ByteArray> = mutableListOf())

@Singleton class DirectMeshRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val transport: DirectLinkTransport,
    private val identity: DeviceIdentity
) : MeshRepository, DirectLinkListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutableStatus = MutableStateFlow<MeshStatus>(MeshStatus.Inactive)
    override val status = mutableStatus.asStateFlow()
    val nearby = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val verification = MutableStateFlow<List<VerificationRequest>>(emptyList())
    val messages = MutableStateFlow<List<DirectMessage>>(emptyList())
    val error = MutableStateFlow<String?>(null)
    val stats = MutableStateFlow(NetworkStats())
    val localId = MutableStateFlow("")
    val searching = MutableStateFlow(false)
    val relayEnabled = MutableStateFlow(true)
    val routes = MutableStateFlow<List<MeshRoute>>(emptyList())
    val routingMetrics = MutableStateFlow(RoutingMetrics())
    private var coordinator: MeshCoordinator? = null
    private val remoteTrust = context.getSharedPreferences("wibrio_remote_fingerprints", Context.MODE_PRIVATE)
    private fun trusted(node: String, key: String): Boolean = sessions.values.any { it.verified && it.node == node && it.key == key } || remoteTrust.getString(node, null) == key || trust.getString(node, null) == key
    fun approveRemote(node: String) = launch {
        val peer = coordinator?.identity(node) ?: error("Peer identity expired")
        require(remoteTrust.all.size < 128 || remoteTrust.contains(node)) { "Remote trust limit reached" }
        check(remoteTrust.edit().putString(node, peer.signingKey).commit()) { "Could not save fingerprint" }
        publish()
    }
    private val sessions = mutableMapOf<String, Session>()
    private val availableEndpoints = mutableSetOf<String>()
    private val pendingPackets = AtomicInteger(0)
    private var localName = "Wibrio"
    private var running = false
    @Volatile private var epoch = 0
    private val trust = context.getSharedPreferences("wibrio_verified_peers", Context.MODE_PRIVATE)
    init { launch { localId.value = identity.localNodeId().value } }
    private fun launch(completed: () -> Unit = {}, block: suspend () -> Unit) { val submitted = epoch; scope.launch { try {
        mutex.withLock {
            if (submitted != epoch) return@withLock
            try { block() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error.value = failure.message?.take(180) ?: "Wireless operation failed"; stats.value = stats.value.copy(failures = stats.value.failures + 1) }
        }
    } finally { completed() } } }
    private fun packet(endpoint: String, block: suspend () -> Unit) {
        if (pendingPackets.incrementAndGet() > 64) {
            pendingPackets.decrementAndGet()
            transport.disconnect(endpoint)
            disconnected(endpoint, "Packet queue limit exceeded")
            return
        }
        launch(completed = { pendingPackets.decrementAndGet() }, block = block)
    }
    fun configureName(name: String) { localName = name.trim().take(40).ifBlank { "Wibrio" } }
    override suspend fun start(preference: TransportPreference) = mutex.withLock {
        if (running && searching.value) return@withLock
        if (running) {
            try {
                require(permissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) { "Grant wireless permissions first." }
                transport.resumeDiscovery(); searching.value = true; discoveryWindow(epoch); publish()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { transport.stopDiscovery(); searching.value = false; error.value = failure.message?.take(180) ?: "Discovery could not resume" }
            return@withLock
        }
        try {
            require(permissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) { "Grant wireless permissions in Settings → Permissions first." }
            require(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) { "Google Play services is missing or needs an update." }
            localId.value = identity.localNodeId().value
            coordinator = withContext(Dispatchers.IO) {
                MeshCoordinator(localId.value, identity, localName, messages, stats, ::trusted, ::publish, { relayEnabled.value }) { nextHop, frame ->
                    val link = sessions.entries.firstOrNull { it.value.verified && it.value.node == nextHop } ?: error("Next hop disconnected")
                    try { sendPacket(link.key, JSONObject().put("type", "MESH").put("to", nextHop).put("nonce", link.value.remoteNonce).put("frame", DeviceIdentity.encode(PacketCodec.encode(frame)))) }
                    catch (failure: Exception) { transport.disconnect(link.key); throw failure }
                }
            }
            running = true; epoch++; searching.value = true; mutableStatus.value = MeshStatus.Discovering
            transport.start(localName, this)
            val started = epoch
            discoveryWindow(started)
            scope.launch {
                val deadline = android.os.SystemClock.elapsedRealtime() + 30 * 60_000L
                while (isActive) {
                    delay(5000)
                    mutex.withLock {
                        if (!running || epoch != started) return@launch
                        if (android.os.SystemClock.elapsedRealtime() >= deadline) { reset(); error.value = "Mesh session ended after 30 minutes. Restart when needed."; return@launch }
                        try { coordinator?.tick() } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error.value = failure.message?.take(180) ?: "Mesh maintenance failed" }
                    }
                }
            }
        } catch (cancelled: CancellationException) { reset(); throw cancelled }
        catch (failure: Exception) { reset(); mutableStatus.value = MeshStatus.Unavailable(failure.message?.take(180) ?: "Unable to start Nearby") }
    }
    override suspend fun stop() = mutex.withLock { reset() }
    private fun discoveryWindow(generation: Int) { scope.launch {
        delay(120_000)
        mutex.withLock { if (running && epoch == generation) { transport.stopDiscovery(); searching.value = false; nearby.value = nearby.value.filter { sessions.containsKey(it.endpoint) }; publish() } }
    } }
    fun shutdown() = launch { reset() }
    private fun reset() {
        running = false; epoch++; searching.value = false; runCatching { transport.stop() }; sessions.clear(); coordinator = null; routes.value = emptyList(); routingMetrics.value = RoutingMetrics(); availableEndpoints.clear(); nearby.value = emptyList(); verification.value = emptyList()
        messages.value = messages.value.map { if (it.state in listOf(DeliveryState.QUEUED, DeliveryState.SENDING, DeliveryState.RELAYED)) it.copy(state = DeliveryState.FAILED) else it }
        mutableStatus.value = MeshStatus.Inactive
    }
    private fun publish() {
        if (!running) return
        val peers = (coordinator?.peers().orEmpty() + sessions.values.filter { it.verified }.map { Peer(NodeId(it.node!!), it.name, true, 1) }).distinctBy { it.id }; routes.value = coordinator?.router?.routes().orEmpty(); routingMetrics.value = coordinator?.router?.metrics ?: RoutingMetrics()
        mutableStatus.value = if (peers.isEmpty() && searching.value) MeshStatus.Discovering else MeshStatus.Active(peers)
    }
    fun connect(endpoint: String) = launch {
        require(running && nearby.value.any { it.endpoint == endpoint }) { "Device is no longer nearby. Restart discovery." }
        require(sessions.size < 8) { "Connection limit reached" }
        if (sessions.containsKey(endpoint)) return@launch
        sessions[endpoint] = Session()
        nearby.value = nearby.value.map { if (it.endpoint == endpoint) it.copy(state = "Connecting") else it }
        try { transport.request(endpoint, localName); timeout(endpoint, epoch) }
        catch (failure: Exception) { remove(endpoint); throw failure }
    }
    fun confirm(endpoint: String, accepted: Boolean) = launch {
        require(verification.value.any { it.endpoint == endpoint }) { "Verification request expired" }
        verification.value = verification.value.filterNot { it.endpoint == endpoint }
        if (accepted) {
            sessions[endpoint]?.accepted = true
            try { transport.accept(endpoint) } catch (failure: Exception) { remove(endpoint); throw failure }
        } else { try { transport.reject(endpoint) } finally { remove(endpoint) } }
    }
    fun disconnectPeer(node: String) = launch { sessions.entries.firstOrNull { it.value.node == node }?.key?.let { transport.disconnect(it); remove(it) } }
    private fun timeout(endpoint: String, generation: Int) { scope.launch {
        delay(45_000)
        mutex.withLock { if (generation == epoch && sessions[endpoint]?.verified == false) { transport.disconnect(endpoint); remove(endpoint); error.value = "Connection verification timed out. Try again." } }
    } }
    override fun found(endpoint: String, name: String) = launch {
        if (running && nearby.value.size < 64) {
            availableEndpoints.add(endpoint)
            if (nearby.value.none { it.endpoint == endpoint }) nearby.value += DiscoveredPeer(endpoint, name)
        }
    }
    override fun lost(endpoint: String) = launch { availableEndpoints.remove(endpoint); if (!sessions.containsKey(endpoint)) nearby.value = nearby.value.filterNot { it.endpoint == endpoint } }
    override fun verification(endpoint: String, name: String, digits: String) = launch {
        if (!running || (sessions.size >= 8 && !sessions.containsKey(endpoint)) || digits.isBlank()) { transport.reject(endpoint); return@launch }
        sessions.getOrPut(endpoint) { Session() }.name = name
        if (verification.value.none { it.endpoint == endpoint }) verification.value += VerificationRequest(endpoint, name, digits)
        timeout(endpoint, epoch)
    }
    override fun connected(endpoint: String) = launch {
        val session = sessions[endpoint]
        if (!running || session?.accepted != true) { transport.disconnect(endpoint); return@launch }
        verification.value = verification.value.filterNot { it.endpoint == endpoint }
        sendPacket(endpoint, JSONObject().put("type", "HELLO").put("nonce", session.localNonce).put("name", localName).put("key", identity.publicKey()))
    }
    override fun disconnected(endpoint: String, reason: String) = launch {
        val known = sessions.containsKey(endpoint)
        remove(endpoint)
        if (known && running) error.value = "$reason. Reconnect from People; confirm the code again."
    }
    private suspend fun remove(endpoint: String) {
        val node = sessions.remove(endpoint)?.node
        verification.value = verification.value.filterNot { it.endpoint == endpoint }
        nearby.value = if (availableEndpoints.contains(endpoint) && searching.value) nearby.value.map { if (it.endpoint == endpoint) it.copy(state = "Nearby") else it } else nearby.value.filterNot { it.endpoint == endpoint }
        if (node != null) coordinator?.down(node)
        publish()
    }
    private suspend fun sendPacket(endpoint: String, body: JSONObject) {
        body.put("v", 1).put("from", localId.value).put("seq", ++checkNotNull(sessions[endpoint]).outgoingSequence)
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        val envelope = JSONObject().put("body", DeviceIdentity.encode(bytes)).put("sig", identity.sign(bytes)).toString().toByteArray(Charsets.UTF_8)
        transport.send(endpoint, envelope)
        stats.value = stats.value.copy(sent = stats.value.sent + 1)
    }
    override fun received(endpoint: String, bytes: ByteArray): Unit = packet(endpoint) {
        val session = sessions[endpoint] ?: return@packet
        if (!running || !session.accepted) return@packet
        try {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - session.window > 1000) { session.window = now; session.received = 0 }
            require(++session.received <= 30) { "Peer exceeded packet rate limit" }
            require(bytes.size <= 16_384)
            val envelope = JSONObject(bytes.toString(Charsets.UTF_8))
            val bodyBytes = DeviceIdentity.decode(envelope.getString("body"))
            require(bodyBytes.size <= 10_000)
            val body = JSONObject(bodyBytes.toString(Charsets.UTF_8))
            require(body.getInt("v") == 1)
            val type = body.getString("type")
            // Byte payload completion can race; never assume HELLO/PROOF/DATA order.
            if (type == "PROOF" && session.key == null) {
                require(session.pendingProof == null) { "Repeated premature identity proof" }
                session.pendingProof = bytes
                return@packet
            }
            if ((type == "MESH") && !session.verified) {
                require(session.deferred.size < 8) { "Too many packets before identity verification" }
                session.deferred += bytes
                return@packet
            }
            val key = if (type == "HELLO") body.getString("key") else checkNotNull(session.key)
            require(DeviceIdentity.verify(key, bodyBytes, envelope.getString("sig"))) { "Invalid packet signature" }
            require(body.getString("from") == DeviceIdentity.nodeId(key)) { "Identity mismatch" }
            stats.value = stats.value.copy(received = stats.value.received + 1)
            if (type == "HELLO") {
                require(session.node == null) { "Unexpected repeated identity handshake" }
                val node = DeviceIdentity.nodeId(key)
                require(node != localId.value && sessions.values.none { it !== session && it.node == node }) { "Duplicate identity detected" }
                val nonce = body.getString("nonce"); require(nonce.length == 36)
                val pinned = trust.getString(node, null); require(pinned == null || pinned == key) { "Peer key changed" }
                require(trust.all.size < 128 || pinned != null) { "Trusted device limit reached" }
                session.key = key; session.node = node; session.remoteNonce = nonce; session.name = body.getString("name").take(40)
                sendPacket(endpoint, JSONObject().put("type", "PROOF").put("to", node).put("nonce", nonce).put("peerNonce", session.localNonce))
                session.pendingProof?.let { session.pendingProof = null; received(endpoint, it) }
                return@packet
            }
            require(body.getString("to") == localId.value && body.getString("nonce") == session.localNonce) { "Wrong recipient or expired session" }
            if (!session.replay.accept(body.getLong("seq"))) return@packet
            if (type == "PROOF") {
                require(body.getString("peerNonce") == session.remoteNonce && !session.verified) { "Invalid identity proof" }
                check(trust.edit().putString(session.node, session.key).commit()) { "Could not save verified peer" }
                session.verified = true
                nearby.value = nearby.value.map { if (it.endpoint == endpoint) it.copy(name = session.name, state = "Connected") else it }
                coordinator?.up(session.node!!)
                publish()
                val deferred = session.deferred.toList(); session.deferred.clear()
                deferred.forEach { received(endpoint, it) }
                return@packet
            }
            require(session.verified) { "Identity handshake incomplete" }
            require(type == "MESH") { "Unsupported direct protocol. All phones must use version 0.3.0." }
            coordinator?.receive(session.node!!, PacketCodec.decode(DeviceIdentity.decode(body.getString("frame"))))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { transport.disconnect(endpoint); remove(endpoint); throw IllegalArgumentException("Rejected packet: ${failure.message?.take(100)}", failure) }
    }
    fun send(node: String, text: String) = launch {
        checkNotNull(coordinator) { "Start the mesh first" }.send(node, text)
        publish()
    }
}
