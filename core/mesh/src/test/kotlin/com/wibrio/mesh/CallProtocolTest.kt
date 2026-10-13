package com.wibrio.mesh

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CallProtocolTest {
    private var time = 1000L
    private fun machine() = CallMachine { time }
    private fun invitation(actions: List<CallAction>) = (actions.single() as CallAction.Send).packet
    private fun connected(video: Boolean = false): Pair<CallMachine, CallMachine> {
        val a = machine(); val b = machine()
        val invite = invitation(a.start("B", video))
        b.receive("A", invite)
        val accepted = invitation(b.answer())
        val ready = a.receive("B", accepted).filterIsInstance<CallAction.Send>().single().packet
        b.receive("A", ready)
        return a to b
    }
    @Test fun captureRequiresExplicitAcceptanceAndReadyOnBothSides() {
        val a = machine(); val b = machine()
        val invite = invitation(a.start("B", true))
        assertTrue(b.receive("A", invite).isEmpty())
        assertEquals(CallPhase.INCOMING, b.view!!.phase)
        val media = invite.copy(kind = CallKind.AUDIO, data = ByteArray(1600))
        assertTrue(b.receive("A", media).isEmpty())
        val accepted = b.answer()
        assertFalse(accepted.any { it == CallAction.StartMedia })
        val ready = a.receive("B", invitation(accepted))
        assertTrue(ready.contains(CallAction.StartMedia))
        assertTrue(b.receive("A", ready.filterIsInstance<CallAction.Send>().single().packet).contains(CallAction.StartMedia))
    }
    @Test fun fullDuplexAudioAndVideoArriveOnBothSides() {
        val (a, b) = connected(true)
        for (kind in listOf(CallKind.AUDIO, CallKind.VIDEO)) {
            val packet = CallPacket(a.view!!.id, true, kind, 1, ByteArray(100))
            assertEquals(1, a.receive("B", CallWire.decode(CallWire.encode(packet))).filterIsInstance<CallAction.Media>().size)
            assertEquals(1, b.receive("A", CallWire.decode(CallWire.encode(packet))).filterIsInstance<CallAction.Media>().size)
        }
    }
    @Test fun duplicateAndOutOfOrderMediaIsDroppedSeparatelyByKind() {
        val (a, _) = connected(true)
        val packet = CallPacket(a.view!!.id, true, CallKind.AUDIO, 3, ByteArray(100))
        assertEquals(1, a.receive("B", packet).size)
        assertTrue(a.receive("B", packet).isEmpty())
        assertTrue(a.receive("B", packet.copy(sequence = 2)).isEmpty())
        assertEquals(1, a.receive("B", packet.copy(kind = CallKind.VIDEO, sequence = 1)).size)
    }
    @Test fun wrongPeerWrongIdAndWrongModeCannotInjectMedia() {
        val (a, _) = connected()
        val packet = CallPacket(a.view!!.id, false, CallKind.AUDIO, 1, ByteArray(100))
        assertTrue(a.receive("attacker", packet).isEmpty())
        assertTrue(a.receive("B", packet.copy(id = UUID.randomUUID().toString())).isEmpty())
        assertTrue(a.receive("B", packet.copy(video = true)).isEmpty())
    }
    @Test fun busyDoesNotReplaceExistingCall() {
        val (a, _) = connected()
        val before = a.view
        val incoming = CallPacket(UUID.randomUUID().toString(), true, CallKind.INVITE)
        assertEquals(CallKind.BUSY, invitation(a.receive("C", incoming)).kind)
        assertEquals(before, a.view)
    }
    @Test fun unansweredCallExpiresAndStopsCapture() {
        val a = machine(); a.start("B", false)
        time += 30_000
        assertTrue(a.tick().contains(CallAction.StopMedia))
        assertEquals(CallPhase.ENDED, a.view!!.phase)
    }
    @Test fun lostReadyRecoversThroughRepeatedAcceptWithoutStartingMediaTwice() {
        val a = machine(); val b = machine()
        b.receive("A", invitation(a.start("B", false)))
        val accept = invitation(b.answer())
        assertTrue(a.receive("B", accept).contains(CallAction.StartMedia))
        time += 2001
        val retry = invitation(b.tick())
        val actions = a.receive("B", retry)
        assertFalse(actions.contains(CallAction.StartMedia))
        assertTrue(b.receive("A", invitation(actions)).contains(CallAction.StartMedia))
    }
    @Test fun endStopsMediaAndOldInvitationCannotRingAgain() {
        val (a, b) = connected()
        val old = CallPacket(a.view!!.id, false, CallKind.INVITE)
        val ended = a.end()
        assertEquals(CallAction.StopMedia, ended.first())
        b.receive("A", ended.filterIsInstance<CallAction.Send>().single().packet)
        b.dismiss()
        assertTrue(b.receive("A", old).isEmpty())
        assertNull(b.view)
    }
    @Test fun heartbeatTimeoutEndsLiveCall() {
        val (a, _) = connected()
        time += 15_000
        assertTrue(a.tick().contains(CallAction.StopMedia))
        assertEquals("Call link lost", a.view!!.reason)
    }
    @Test fun wireRoundTripAndStrictBounds() {
        val packet = CallPacket(UUID.randomUUID().toString(), true, CallKind.VIDEO, 21, ByteArray(3500) { it.toByte() })
        val decoded = CallWire.decode(CallWire.encode(packet))
        assertEquals(packet.id, decoded.id); assertEquals(packet.sequence, decoded.sequence)
        assertTrue(packet.data.contentEquals(decoded.data))
        for (invalid in listOf(packet.copy(data = ByteArray(3501)), packet.copy(video = false), packet.copy(kind = CallKind.PING), packet.copy(sequence = -1))) {
            assertThrows(IllegalArgumentException::class.java) { CallWire.encode(invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { CallWire.decode(byteArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { CallWire.decode(CallWire.encode(packet) + 0.toByte()) }
    }
}
