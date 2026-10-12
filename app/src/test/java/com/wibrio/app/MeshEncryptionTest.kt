package com.wibrio.app

import org.junit.Assert.*
import org.junit.Test

class MeshEncryptionTest {
    @Test fun onlyRecipientCanDecryptAndContextIsBound() {
        val recipient = MeshEncryption(); val relay = MeshEncryption()
        val context = MeshEncryption.context("A", "D", "fresh-session")
        val encrypted = MeshEncryption.encrypt(recipient.publicKey(), "secret".toByteArray(), context)
        assertEquals("secret", recipient.decrypt(encrypted, context).toString(Charsets.UTF_8))
        assertTrue(runCatching { relay.decrypt(encrypted, context) }.isFailure)
        assertTrue(runCatching { recipient.decrypt(encrypted, MeshEncryption.context("A", "E", "fresh-session")) }.isFailure)
        val modified = encrypted.copyOf(); modified[modified.lastIndex] = (modified.last().toInt() xor 1).toByte()
        assertTrue(runCatching { recipient.decrypt(modified, context) }.isFailure)
    }
}
