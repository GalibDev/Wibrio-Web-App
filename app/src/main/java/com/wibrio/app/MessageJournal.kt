package com.wibrio.app

import com.wibrio.mesh.DeliveryState
import org.json.JSONArray
import org.json.JSONObject

data class PendingMessage(val id: String, val node: String, val text: String, val created: Long, val deadline: Long, val lastAttempt: Long = 0, val attempts: Int = 0)
data class MessageSnapshot(val messages: List<DirectMessage> = emptyList(), val pending: List<PendingMessage> = emptyList(), val receipts: Map<String, Long> = emptyMap())

fun MessageSnapshot.boundedHistory(): MessageSnapshot {
    val pendingIds = pending.map { it.id }.toSet()
    val protected = messages.filter { it.outgoing && it.id in pendingIds }
    require(protected.size <= 32)
    val recent = messages.filterNot { it.outgoing && it.id in pendingIds }.takeLast(500 - protected.size)
    return copy(messages = (protected + recent).sortedBy { it.timestamp })
}

fun MessageSnapshot.recovered(now: Long): MessageSnapshot {
    val pendingIds = pending.map { it.id }.toSet()
    val expired = pending.filter { it.deadline <= now || it.created > now }.map { it.id }.toSet()
    return copy(messages = messages.map { if (it.outgoing && it.id in pendingIds) it.copy(state = if (it.id in expired) DeliveryState.EXPIRED else DeliveryState.QUEUED) else it },
        pending = pending.filterNot { it.id in expired }, receipts = receipts.filterValues { it > now })
}

/** A save must be atomic and durable before returning. Failure must never acknowledge delivery. */
interface MessageJournal {
    suspend fun load(): MessageSnapshot
    suspend fun save(snapshot: MessageSnapshot)
}

object MessageSnapshotCodec {
    const val MAX_BYTES = 3_000_000
    fun encode(snapshot: MessageSnapshot): ByteArray {
        require(snapshot.messages.size <= 500 && snapshot.pending.size <= 32 && snapshot.receipts.size <= 1024)
        val messages = JSONArray()
        snapshot.messages.forEach { messages.put(JSONObject().put("id", it.id).put("peer", it.peerId).put("text", it.text).put("out", it.outgoing).put("at", it.timestamp).put("state", it.state.name)) }
        val pending = JSONArray()
        snapshot.pending.forEach { pending.put(JSONObject().put("id", it.id).put("peer", it.node).put("text", it.text).put("created", it.created).put("deadline", it.deadline).put("attempt", it.lastAttempt).put("count", it.attempts)) }
        return JSONObject().put("version", 1).put("messages", messages).put("pending", pending).put("receipts", JSONObject(snapshot.receipts)).toString().toByteArray().also { require(it.size <= MAX_BYTES) }
    }
    fun decode(bytes: ByteArray): MessageSnapshot {
        require(bytes.size <= MAX_BYTES)
        val root = JSONObject(bytes.toString(Charsets.UTF_8)); require(root.getInt("version") == 1)
        val m = root.getJSONArray("messages"); val p = root.getJSONArray("pending"); val r = root.getJSONObject("receipts")
        require(m.length() <= 500 && p.length() <= 32 && r.length() <= 1024)
        val messages = List(m.length()) { i -> m.getJSONObject(i).let { DirectMessage(it.getString("id"), it.getString("peer"), it.getString("text"), it.getBoolean("out"), it.getLong("at"), DeliveryState.valueOf(it.getString("state"))) } }
        val pending = List(p.length()) { i -> p.getJSONObject(i).let { PendingMessage(it.getString("id"), it.getString("peer"), it.getString("text"), it.getLong("created"), it.getLong("deadline"), it.getLong("attempt"), it.getInt("count")) } }
        require((messages.map { it.text } + pending.map { it.text }).all { it.isNotBlank() && it.toByteArray().size <= 3072 })
        require(pending.all { it.attempts in 0..5 && it.deadline - it.created in 1..60_000 })
        return MessageSnapshot(messages, pending, r.keys().asSequence().associateWith { r.getLong(it) })
    }
}
