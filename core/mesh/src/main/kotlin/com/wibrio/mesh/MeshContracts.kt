package com.wibrio.mesh

import kotlinx.coroutines.flow.StateFlow

@JvmInline value class NodeId(val value: String)
data class Peer(val id: NodeId, val displayName: String, val verified: Boolean, val hops: Int?)
enum class TransportPreference { AUTOMATIC, WIFI_PRIORITY, BLUETOOTH_PRIORITY }
enum class DeliveryState { QUEUED, SENDING, RELAYED, DELIVERED, FAILED, EXPIRED }
sealed interface MeshStatus {
    data object Inactive : MeshStatus
    data object Discovering : MeshStatus
    data class Active(val peers: List<Peer>) : MeshStatus
    data class Unavailable(val reason: String) : MeshStatus
}
interface Transport {
    val status: StateFlow<MeshStatus>
    suspend fun start(preference: TransportPreference)
    suspend fun stop()
    suspend fun confirmPeer(endpointId: String, accepted: Boolean)
    suspend fun send(endpointId: String, bytes: ByteArray)
}
interface MeshRepository {
    val status: StateFlow<MeshStatus>
    suspend fun start(preference: TransportPreference)
    suspend fun stop()
}
interface MessageRepository {
    suspend fun enqueue(recipient: NodeId, text: String): String
    // DELIVERED requires an authenticated acknowledgement from the final recipient.
    fun delivery(messageId: String): StateFlow<DeliveryState>
}
interface IdentityRepository {
    suspend fun localNodeId(): NodeId
    suspend fun fingerprint(): String
}
