package com.wibrio.mesh

import java.io.*

enum class PacketType { PRESENCE, DATA, ACK, LINK_DOWN, ROUTE_REQUEST }
data class MeshPacket(val id: String, val source: String, val destination: String, val type: PacketType, val createdAt: Long, val expiresAt: Long, val hopLimit: Int, val payload: ByteArray, val publicKey: String, val signature: String = "", val version: Int = 3)
data class RoutedFrame(val packet: MeshPacket, val path: List<String>)
interface PacketAuthenticator {
    fun seal(packet: MeshPacket): MeshPacket
    fun verify(packet: MeshPacket): Boolean
}

/** Canonical binary origin-signed body, with separately bounded relay path metadata. */
object PacketCodec {
    private fun DataOutputStream.body(p: MeshPacket) {
        writeInt(p.version); writeUTF(p.id); writeUTF(p.source); writeUTF(p.destination); writeUTF(p.type.name)
        writeLong(p.createdAt); writeLong(p.expiresAt); writeInt(p.hopLimit); writeUTF(p.publicKey)
        require(p.payload.size <= 6144); writeInt(p.payload.size); write(p.payload)
    }
    fun signingBytes(packet: MeshPacket): ByteArray = ByteArrayOutputStream().also { out -> DataOutputStream(out).use { it.body(packet) } }.toByteArray()
    fun encode(frame: RoutedFrame): ByteArray {
        require(frame.path.size in 1..8)
        return ByteArrayOutputStream().also { out -> DataOutputStream(out).use { stream ->
            stream.body(frame.packet); stream.writeUTF(frame.packet.signature); stream.writeInt(frame.path.size); frame.path.forEach { stream.writeUTF(it) }
        } }.toByteArray().also { require(it.size <= 9000) }
    }
    fun decode(bytes: ByteArray): RoutedFrame {
        require(bytes.size <= 9000)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val version = input.readInt(); val id = input.readUTF(); val source = input.readUTF(); val destination = input.readUTF(); val type = PacketType.valueOf(input.readUTF())
            val created = input.readLong(); val expires = input.readLong(); val hops = input.readInt(); val key = input.readUTF()
            require(id.length <= 36 && source.length in 1..64 && destination.length <= 64 && key.length <= 256)
            val length = input.readInt(); require(length in 0..6144)
            val payload = ByteArray(length); input.readFully(payload)
            val signature = input.readUTF(); require(signature.length <= 256)
            val count = input.readInt(); require(count in 1..8)
            val path = List(count) { input.readUTF().also { require(it.length in 1..64) } }
            require(input.available() == 0)
            return RoutedFrame(MeshPacket(id, source, destination, type, created, expires, hops, payload, key, signature, version), path)
        }
    }
}
