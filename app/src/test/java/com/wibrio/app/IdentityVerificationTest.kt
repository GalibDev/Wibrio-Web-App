package com.wibrio.app

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class IdentityVerificationTest {
    private fun keys() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    @Test fun authenticatedPacketAcceptsOnlyOriginalBodyAndKey() {
        val sender = keys()
        val stranger = keys()
        val body = "recipient:A;nonce:current-session;id:123;text:hello".toByteArray()
        val signature = DeviceIdentity.encode(Signature.getInstance("SHA256withECDSA").run { initSign(sender.private); update(body); sign() })
        val public = DeviceIdentity.encode(sender.public.encoded)
        assertTrue(DeviceIdentity.verify(public, body, signature))
        assertFalse(DeviceIdentity.verify(public, "recipient:B;nonce:current-session;id:123;text:hello".toByteArray(), signature))
        assertFalse(DeviceIdentity.verify(DeviceIdentity.encode(stranger.public.encoded), body, signature))
        assertFalse(DeviceIdentity.verify(public, body, "not a signature"))
    }
    @Test fun nodeIdBindsTheEntirePublicKey() {
        val key = DeviceIdentity.encode(keys().public.encoded)
        assertEquals(64, DeviceIdentity.nodeId(key).length)
        assertEquals(DeviceIdentity.nodeId(key), DeviceIdentity.nodeId(key))
        assertNotEquals(DeviceIdentity.nodeId(key), DeviceIdentity.nodeId(DeviceIdentity.encode(keys().public.encoded)))
    }
    @Test fun malformedOrOversizedKeyIsRejected() {
        assertFalse(DeviceIdentity.verify("?", byteArrayOf(1), "?"))
        assertFalse(DeviceIdentity.verify("a".repeat(257), byteArrayOf(1), "a"))
    }
}
