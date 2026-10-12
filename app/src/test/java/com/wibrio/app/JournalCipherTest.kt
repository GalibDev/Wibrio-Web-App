package com.wibrio.app

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class JournalCipherTest {
    @Test fun historyCapDoesNotHideMessagesStillInDurableOutbox() {
        val pending = PendingMessage("pending", "peer", "waiting", 1000, 61_000)
        val history = listOf(DirectMessage("pending", "peer", "waiting", true, 1000, com.wibrio.mesh.DeliveryState.QUEUED)) +
            List(501) { DirectMessage("received-$it", "peer", "text", false, 2000L + it, com.wibrio.mesh.DeliveryState.DELIVERED) }
        val bounded = MessageSnapshot(history, listOf(pending)).boundedHistory()
        assertEquals(500, bounded.messages.size)
        assertTrue(bounded.messages.any { it.id == "pending" })
        assertEquals("received-500", bounded.messages.last().id)
    }
    @Test fun freshIvsWrongKeyAndTamperProtection() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = JournalCipher { key }; val text = "private local queue".toByteArray()
        val encrypted = cipher.encrypt(text)
        assertFalse(encrypted.contentEquals(cipher.encrypt(text)))
        assertArrayEquals(text, cipher.decrypt(encrypted))
        val other = JournalCipher { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }
        assertTrue(runCatching { other.decrypt(encrypted) }.isFailure)
        val corrupted = encrypted.clone(); corrupted[0] = (corrupted[0].toInt() xor 1).toByte()
        assertTrue(runCatching { cipher.decrypt(corrupted) }.isFailure)
    }
    @Test fun malformedSnapshotAndOversizedJournalFailClosed() {
        assertTrue(runCatching { MessageSnapshotCodec.decode("{\"version\":99}".toByteArray()) }.isFailure)
        assertTrue(runCatching { MessageSnapshotCodec.encode(MessageSnapshot(pending = List(33) { PendingMessage("id", "peer", "text", 1000, 61_000) })) }.isFailure)
        assertTrue(runCatching { MessageSnapshotCodec.decode(ByteArray(MessageSnapshotCodec.MAX_BYTES + 1)) }.isFailure)
    }
}
