package com.wibrio.app

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Standard AES-GCM with a provider-generated IV per snapshot; production key is non-exportable. */
class JournalCipher(private val key: () -> SecretKey) {
    fun encrypt(bytes: ByteArray): ByteArray {
        require(bytes.size <= MessageSnapshotCodec.MAX_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(AAD)
        check(cipher.iv.size == 12)
        return cipher.iv + cipher.doFinal(bytes)
    }
    fun decrypt(bytes: ByteArray): ByteArray {
        require(bytes.size in 29..MessageSnapshotCodec.MAX_BYTES + 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))); cipher.updateAAD(AAD)
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size))
    }
    companion object { private val AAD = "Wibrio/message-journal/v1".toByteArray() }
}
