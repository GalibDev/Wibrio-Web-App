package com.wibrio.mesh

import java.nio.ByteBuffer
import java.util.UUID

enum class CallKind { INVITE, ACCEPT, READY, END, BUSY, PING, AUDIO, VIDEO }
enum class CallPhase { OUTGOING, INCOMING, CONNECTING, ACTIVE, ENDED }
data class CallPacket(val id: String, val video: Boolean, val kind: CallKind, val sequence: Long = 0, val data: ByteArray = byteArrayOf())
data class CallView(val id: String, val node: String, val video: Boolean, val phase: CallPhase, val reason: String = "")
sealed interface CallAction {
    data class Send(val node: String, val packet: CallPacket) : CallAction
    data object StartMedia : CallAction
    data object StopMedia : CallAction
    data class Media(val packet: CallPacket) : CallAction
}
object CallWire {
    const val MAX_DATA = 3500
    fun encode(packet: CallPacket): ByteArray {
        validate(packet)
        val id = UUID.fromString(packet.id)
        return ByteBuffer.allocate(29 + packet.data.size).put(1).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits)
            .put(packet.kind.ordinal.toByte()).put(if (packet.video) 1.toByte() else 0.toByte()).putLong(packet.sequence)
            .putShort(packet.data.size.toShort()).put(packet.data).array()
    }
    fun decode(bytes: ByteArray): CallPacket {
        require(bytes.size in 29..(29 + MAX_DATA))
        val b = ByteBuffer.wrap(bytes)
        require(b.get().toInt() == 1)
        val id = UUID(b.long, b.long).toString()
        val kind = CallKind.entries.getOrNull(b.get().toInt()) ?: error("Unknown call packet")
        val video = b.get().toInt(); require(video in 0..1)
        val seq = b.long
        val size = b.short.toInt(); require(size == b.remaining())
        return CallPacket(id, video == 1, kind, seq, ByteArray(size).also { b.get(it) }).also(::validate)
    }
    private fun validate(p: CallPacket) {
        require(UUID.fromString(p.id).toString() == p.id && p.sequence >= 0)
        require(p.data.size <= MAX_DATA)
        when (p.kind) {
            CallKind.AUDIO -> require(p.data.size in 2..1600 && p.data.size % 2 == 0)
            CallKind.VIDEO -> require(p.video && p.data.size in 4..MAX_DATA)
            else -> require(p.data.isEmpty())
        }
    }
}

/** Single owner; only feed packets from already verified direct links. No media before consent. */
class CallMachine(private val now: () -> Long) {
    var view: CallView? = null; private set
    private var began = 0L
    private var heard = 0L
    private var sent = 0L
    private var audio = -1L
    private var video = -1L
    private val ended = linkedMapOf<String, Long>()
    private fun send(kind: CallKind): CallAction.Send = view!!.let { CallAction.Send(it.node, CallPacket(it.id, it.video, kind)) }
    fun start(node: String, video: Boolean): List<CallAction> {
        check(view == null || view?.phase == CallPhase.ENDED) { "A call is already in progress" }
        view = CallView(UUID.randomUUID().toString(), node, video, CallPhase.OUTGOING)
        began = now(); heard = began; sent = began; audio = -1; this.video = -1
        return listOf(send(CallKind.INVITE))
    }
    fun answer(): List<CallAction> {
        check(view?.phase == CallPhase.INCOMING)
        view = view!!.copy(phase = CallPhase.CONNECTING); sent = now()
        return listOf(send(CallKind.ACCEPT))
    }
    fun end(reason: String = "Call ended", notify: Boolean = true): List<CallAction> {
        val v = view ?: return emptyList()
        if (v.phase == CallPhase.ENDED) return emptyList()
        val outgoing = if (notify) listOf(send(CallKind.END)) else emptyList()
        ended[v.id] = now() + 120_000
        while (ended.size > 64) ended.remove(ended.keys.first())
        view = v.copy(phase = CallPhase.ENDED, reason = reason)
        return listOf(CallAction.StopMedia) + outgoing
    }
    fun dismiss() { check(view == null || view?.phase == CallPhase.ENDED); view = null }
    fun receive(node: String, p: CallPacket): List<CallAction> {
        ended.entries.removeAll { it.value <= now() }
        if (p.id in ended) return emptyList()
        val v = view
        if (p.kind == CallKind.INVITE && (v == null || v.phase == CallPhase.ENDED)) {
            view = CallView(p.id, node, p.video, CallPhase.INCOMING)
            began = now(); heard = began; sent = began; audio = -1; video = -1
            return emptyList()
        }
        if (v == null || v.node != node || v.id != p.id || v.video != p.video || v.phase == CallPhase.ENDED) {
            return if (p.kind == CallKind.INVITE) listOf(CallAction.Send(node, p.copy(kind = CallKind.BUSY))) else emptyList()
        }
        heard = now()
        return when (p.kind) {
            CallKind.INVITE -> if (v.phase in listOf(CallPhase.CONNECTING, CallPhase.ACTIVE)) listOf(send(CallKind.ACCEPT)) else emptyList()
            CallKind.ACCEPT -> when (v.phase) {
                CallPhase.OUTGOING -> { view = v.copy(phase = CallPhase.ACTIVE); listOf(send(CallKind.READY), CallAction.StartMedia) }
                CallPhase.ACTIVE -> listOf(send(CallKind.READY))
                else -> emptyList()
            }
            CallKind.READY -> if (v.phase == CallPhase.CONNECTING) { view = v.copy(phase = CallPhase.ACTIVE); listOf(CallAction.StartMedia) } else emptyList()
            CallKind.END -> end("Peer ended the call", false)
            CallKind.BUSY -> end("Peer is busy", false)
            CallKind.PING -> emptyList()
            CallKind.AUDIO -> if (v.phase == CallPhase.ACTIVE && p.sequence > audio) { audio = p.sequence; listOf(CallAction.Media(p)) } else emptyList()
            CallKind.VIDEO -> if (v.phase == CallPhase.ACTIVE && v.video && p.sequence > video) { video = p.sequence; listOf(CallAction.Media(p)) } else emptyList()
        }
    }
    fun tick(): List<CallAction> {
        val v = view ?: return emptyList()
        if (v.phase == CallPhase.ENDED) return emptyList()
        if (v.phase != CallPhase.ACTIVE && now() - began >= 30_000) return end("Call unanswered / connection timed out")
        if (v.phase == CallPhase.ACTIVE && now() - heard >= 15_000) return end("Call link lost")
        if (now() - began >= 30 * 60_000) return end("Call reached the 30 minute session limit")
        if (now() - sent < 2000) return emptyList()
        sent = now()
        return when (v.phase) {
            CallPhase.OUTGOING -> listOf(send(CallKind.INVITE))
            CallPhase.CONNECTING -> listOf(send(CallKind.ACCEPT))
            CallPhase.ACTIVE -> listOf(send(CallKind.PING))
            else -> emptyList()
        }
    }
}
