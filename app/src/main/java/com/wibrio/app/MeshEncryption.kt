package com.wibrio.app

import com.google.crypto.tink.*
import com.google.crypto.tink.hybrid.HybridConfig

/** Ephemeral session keyset. Private key material is never serialized or relayed. */
class MeshEncryption {
    private val handle: KeysetHandle
    init {
        HybridConfig.register()
        handle = KeysetHandle.generateNew(KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"))
    }
    fun publicKey(): ByteArray = TinkProtoKeysetFormat.serializeKeysetWithoutSecret(handle.publicKeysetHandle)
    fun decrypt(ciphertext: ByteArray, context: ByteArray): ByteArray = handle.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java).decrypt(ciphertext, context)
    companion object {
        fun encrypt(publicKey: ByteArray, plaintext: ByteArray, context: ByteArray): ByteArray {
            require(publicKey.size <= 2048)
            HybridConfig.register()
            return TinkProtoKeysetFormat.parseKeysetWithoutSecret(publicKey).getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java).encrypt(plaintext, context)
        }
        fun context(source: String, destination: String, session: String): ByteArray = "Wibrio/v3/$source/$destination/$session".toByteArray(Charsets.UTF_8)
    }
}
