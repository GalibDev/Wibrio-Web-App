package com.wibrio.app

import android.content.Context
import com.wibrio.mesh.DirectLinkListener
import com.wibrio.mesh.DirectLinkTransport
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
    private var generation = 0
    private var callback: ConnectionLifecycleCallback? = null
    private var payload: PayloadCallback? = null
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
            override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
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
            override fun onDisconnected(endpointId: String) { if (epoch == generation) listener.disconnected(endpointId, "Peer disconnected") }
        }
        client.startAdvertising(name, SERVICE, callback!!, AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()).boundedAwait()
        client.startDiscovery(SERVICE, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) { if (epoch == generation) listener.found(endpointId, info.endpointName.take(40)) }
            override fun onEndpointLost(endpointId: String) { if (epoch == generation) listener.lost(endpointId) }
        }, DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()).boundedAwait()
    }
    override suspend fun request(endpoint: String, name: String) { client.requestConnection(name, endpoint, checkNotNull(callback)).boundedAwait() }
    override suspend fun accept(endpoint: String) { client.acceptConnection(endpoint, checkNotNull(payload)).boundedAwait() }
    override suspend fun reject(endpoint: String) { client.rejectConnection(endpoint).boundedAwait() }
    override suspend fun send(endpoint: String, bytes: ByteArray) {
        require(bytes.size <= 16_384)
        client.sendPayload(endpoint, Payload.fromBytes(bytes)).boundedAwait()
    }
    override fun disconnect(endpoint: String) { client.disconnectFromEndpoint(endpoint) }
    override fun stopDiscovery() { client.stopAdvertising(); client.stopDiscovery() }
    override fun stop() { generation++; stopDiscovery(); client.stopAllEndpoints(); callback = null; payload = null }
    companion object { private const val SERVICE = "com.wibrio.app.direct.v1" }
}

private suspend fun <T> Task<T>.boundedAwait(): T = try {
    withTimeout(20_000) { await() }
} catch (timeout: TimeoutCancellationException) {
    throw java.io.IOException("Nearby operation timed out. Retry the connection.", timeout)
}
