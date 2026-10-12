package com.wibrio.mesh

interface DirectLinkListener {
    fun found(endpoint: String, name: String)
    fun lost(endpoint: String)
    fun verification(endpoint: String, name: String, digits: String)
    fun connected(endpoint: String)
    fun disconnected(endpoint: String, reason: String)
    fun received(endpoint: String, bytes: ByteArray)
}
interface DirectLinkTransport {
    suspend fun start(name: String, listener: DirectLinkListener)
    suspend fun request(endpoint: String, name: String)
    suspend fun accept(endpoint: String)
    suspend fun reject(endpoint: String)
    suspend fun send(endpoint: String, bytes: ByteArray)
    fun disconnect(endpoint: String)
    fun stopDiscovery()
    fun stop()
}
