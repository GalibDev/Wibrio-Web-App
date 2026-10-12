package com.wibrio.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.util.Base64
import com.wibrio.mesh.IdentityRepository
import com.wibrio.mesh.NodeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class DeviceIdentity @Inject constructor() : IdentityRepository {
    private val alias = "wibrio.identity.v1"
    @Synchronized private fun keys(): KeyStore {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256).build())
            }.generateKeyPair()
        }
        return store
    }
    fun publicKey(): String = encode(keys().getCertificate(alias).publicKey.encoded)
    fun sign(bytes: ByteArray): String = encode(Signature.getInstance("SHA256withECDSA").run {
        initSign(keys().getKey(alias, null) as PrivateKey); update(bytes); sign()
    })
    override suspend fun localNodeId() = withContext(Dispatchers.IO) { NodeId(nodeId(publicKey())) }
    override suspend fun fingerprint() = localNodeId().value.chunked(4).joinToString(" ")
    companion object {
        fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
        fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
        fun nodeId(key: String): String = MessageDigest.getInstance("SHA-256").digest(decode(key)).joinToString("") { "%02x".format(it) }
        fun verify(key: String, bytes: ByteArray, signature: String): Boolean = runCatching {
            require(key.length <= 256 && signature.length <= 128)
            val public = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(decode(key)))
            Signature.getInstance("SHA256withECDSA").run { initVerify(public); update(bytes); verify(decode(signature)) }
        }.getOrDefault(false)
    }
}
