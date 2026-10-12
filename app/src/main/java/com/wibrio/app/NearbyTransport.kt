package com.wibrio.app

import android.content.Context
import com.wibrio.mesh.DirectLinkListener
import com.wibrio.mesh.DirectLinkTransport
import com.wibrio.mesh.PayloadTransfers
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import com.google.android.gms.tasks.Task
import javax.inject.Inject
import javax.inject.Singleton

// Endpoint IDs belong only to this transport session, never to persistent identities.
@Singleton class NearbyTransport @Inject constructor(@ApplicationContext context: Context) : DirectLinkTransport {
    private val client = Nearby.getConnectionsClient(context)
    @Volatile private var generation = 0
    private val transfers = PayloadTransfers()
    private var callback: ConnectionLifecycleCallback? = null
    private var payload: PayloadCallback? = null
    private var discovery: EndpointDiscoveryCallback? = null
    private var advertisedName = "Wibrio"
    private var advertising = false
    override suspend fun start(name: String, listener: DirectLinkListener) {
        stop()
        val epoch = generation
        payload = object : PayloadCallback() {
            override fun onPayloadReceived(endpointId: String, incoming: Payload) {
                if (epoch != generation) return
                val bytes = incoming.asBytes()
                if (incoming.type != Payload.Type.BYTES || bytes == null || bytes.size > 16_384) {
                    client.disconnectFromEndpoint(endpointId); listener.disconnected(endpointId, "Invalid payload"); return
                }
                listener.received(endpointId, bytes)
            }
            override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
                if (epoch != generation) return
                when (update.status) {
                    PayloadTransferUpdate.Status.SUCCESS -> transfers.complete(endpointId, update.payloadId, true)
                    PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> transfers.complete(endpointId, update.payloadId, false)
                }
            }
        }
        callback = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                if (epoch == generation) listener.verification(endpointId, info.endpointName.take(40), info.authenticationDigits)
            }
            override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
                if (epoch != generation) return
                if (resolution.status.isSuccess) listener.connected(endpointId)
                else listener.disconnected(endpointId, "Connection rejected or failed (${resolution.status.statusCode})")
            }
            override fun onDisconnected(endpointId: String) { if (epoch == generation) { transfers.disconnect(endpointId); listener.disconnected(endpointId, "Peer disconnected") } }
        }
        advertisedName = name
        discovery = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) { if (epoch == generation) listener.found(endpointId, info.endpointName.take(40)) }
            override fun onEndpointLost(endpointId: String) { if (epoch == generation) listener.lost(endpointId) }
        }
        resumeDiscovery()
    }
    override suspend fun resumeDiscovery() {
        if (!advertising) {
            client.startAdvertising(advertisedName, SERVICE, checkNotNull(callback), AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()).boundedAwait()
            advertising = true
        }
        client.startDiscovery(SERVICE, checkNotNull(discovery), DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()).boundedAwait()
    }
    override suspend fun request(endpoint: String, name: String) { client.requestConnection(name, endpoint, checkNotNull(callback)).boundedAwait() }
    override suspend fun accept(endpoint: String) { client.acceptConnection(endpoint, checkNotNull(payload)).boundedAwait() }
    override suspend fun reject(endpoint: String) { client.rejectConnection(endpoint).boundedAwait() }
    override suspend fun send(endpoint: String, bytes: ByteArray) {
        require(bytes.size <= 16_384)
        val outgoing = Payload.fromBytes(bytes)
        val completed = transfers.register(endpoint, outgoing.id)
        try {
            client.sendPayload(endpoint, outgoing).boundedAwait()
            try { withTimeout(10_000) { completed.await() } }
            catch (timeout: TimeoutCancellationException) { throw java.io.IOException("Nearby payload completion timed out", timeout) }
        } finally { transfers.forget(outgoing.id) }
    }
    override fun disconnect(endpoint: String) { transfers.disconnect(endpoint); client.disconnectFromEndpoint(endpoint) }
    // Pause expensive scanning, but remain reachable for incoming verified connections.
    override fun stopDiscovery() { client.stopDiscovery() }
    override fun stop() { generation++; transfers.stop(); stopDiscovery(); client.stopAdvertising(); advertising = false; client.stopAllEndpoints(); callback = null; payload = null; discovery = null }
    // Isolate pre-durable-ACK clients; all field-test phones must run 0.4.0 or newer.
    companion object { private const val SERVICE = "com.wibrio.app.mesh.v4" }
}

private suspend fun <T> Task<T>.boundedAwait(): T = try {
    withTimeout(20_000) { await() }
} catch (timeout: TimeoutCancellationException) {
    throw java.io.IOException("Nearby operation timed out. Retry the connection.", timeout)
}
