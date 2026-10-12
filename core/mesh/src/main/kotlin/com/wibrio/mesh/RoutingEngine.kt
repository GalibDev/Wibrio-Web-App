package com.wibrio.mesh

import java.util.UUID

data class MeshRoute(val destination: String, val nextHop: String, val path: List<String>, val expiresAt: Long) { val hops get() = path.size }
sealed interface RoutingAction {
    data class Send(val nextHop: String, val frame: RoutedFrame) : RoutingAction
    data class Deliver(val packet: MeshPacket) : RoutingAction
}
data class RoutingMetrics(val forwarded: Long = 0, val duplicates: Long = 0, val rejected: Long = 0, val routeChanges: Long = 0)

/** Single-owner deterministic engine. No Android, radio, clock, thread or crypto provider dependency. */
class RoutingEngine(private val local: String, private val key: String, private val auth: PacketAuthenticator, private val now: () -> Long) {
    private val neighbors = linkedSetOf<String>()
    private val table = linkedMapOf<String, MutableList<MeshRoute>>()
    private val seen = linkedMapOf<String, Long>()
    private val rates = mutableMapOf<String, Pair<Long, Int>>()
    var metrics = RoutingMetrics(); private set
    fun routes(): List<MeshRoute> { expire(); return table.values.flatten().sortedWith(compareBy({ it.destination }, { it.hops }, { it.nextHop })) }
    fun routeTo(destination: String): MeshRoute? = routes().firstOrNull { it.destination == destination }
    fun neighborUp(node: String) { require(node != local && node.length in 1..64 && neighbors.size < 8); neighbors += node; learn(node, listOf(node), Long.MAX_VALUE) }
    fun neighborDown(node: String): List<RoutingAction> {
        neighbors -= node; rates -= node
        invalidate(local, node)
        return originate(PacketType.LINK_DOWN, "", node.toByteArray())
    }
    fun create(type: PacketType, destination: String, payload: ByteArray, lifetime: Long = 90_000, hopLimit: Int = 8): MeshPacket {
        require(lifetime in 1..120_000 && hopLimit in 1..8 && destination.length <= 64 && payload.size <= 6144)
        val time = now()
        return auth.seal(MeshPacket(UUID.randomUUID().toString(), local, destination, type, time, time + lifetime, hopLimit, payload, key))
    }
    fun originate(type: PacketType, destination: String, payload: ByteArray): List<RoutingAction> = send(create(type, destination, payload))
    fun send(packet: MeshPacket): List<RoutingAction> {
        require(packet.source == local && auth.verify(packet))
        expire()
        if (packet.expiresAt <= now() || seen.size >= 2048) return emptyList()
        seen[packet.id] = packet.expiresAt
        return forward(RoutedFrame(packet, listOf(local)), null)
    }
    fun receive(incoming: String, frame: RoutedFrame): List<RoutingAction> {
        expire()
        val p = frame.packet; val path = frame.path; val time = now()
        val rate = rates[incoming] ?: (time to 0)
        val updated = if (time - rate.first >= 1000) time to 1 else rate.first to rate.second + 1
        if (incoming in neighbors) rates[incoming] = updated
        val valid = incoming in neighbors && updated.second <= 30 && p.version == 3 && p.hopLimit in 1..8 && path.size in 1..p.hopLimit && path.first() == p.source && path.last() == incoming && path.distinct().size == path.size && local !in path && p.source.length in 1..64 && p.destination.length <= 64 && p.payload.size <= 6144 && p.expiresAt > time && p.createdAt <= time + 30_000 && p.expiresAt - p.createdAt in 1..120_000 && runCatching { UUID.fromString(p.id).toString() == p.id && auth.verify(p) }.getOrDefault(false)
        if (!valid) { metrics = metrics.copy(rejected = metrics.rejected + 1); return emptyList() }
        // Duplicate arrivals can prove an alternative path, but never trigger delivery/forwarding twice.
        learn(p.source, path.reversed(), p.expiresAt)
        if (seen.containsKey(p.id)) { metrics = metrics.copy(duplicates = metrics.duplicates + 1); return emptyList() }
        if (seen.size >= 2048) { metrics = metrics.copy(rejected = metrics.rejected + 1); return emptyList() }
        seen[p.id] = p.expiresAt
        if (p.type == PacketType.LINK_DOWN) invalidate(p.source, p.payload.toString(Charsets.UTF_8))
        val actions = mutableListOf<RoutingAction>()
        if (p.destination.isEmpty() || p.destination == local) actions += RoutingAction.Deliver(p)
        if (p.destination != local && path.size < p.hopLimit) actions += forward(frame.copy(path = path + local), incoming)
        return actions
    }
    private fun forward(frame: RoutedFrame, incoming: String?): List<RoutingAction> {
        val candidates = neighbors.filter { it != incoming && it !in frame.path }
        val cached = if (frame.packet.destination.isNotEmpty()) routeTo(frame.packet.destination)?.nextHop else null
        val targets = if (cached in candidates) listOf(cached!!) else candidates
        metrics = metrics.copy(forwarded = metrics.forwarded + targets.size)
        return targets.map { RoutingAction.Send(it, frame) }
    }
    private fun learn(destination: String, path: List<String>, expires: Long) {
        if (destination == local || path.isEmpty() || path.first() !in neighbors || path.size > 8 || path.distinct().size != path.size || local in path) return
        if (destination !in table && table.size >= 128) return
        val list = table.getOrPut(destination) { mutableListOf() }
        val direct = destination in neighbors && path.size == 1
        val route = MeshRoute(destination, path.first(), path.toList(), if (direct) Long.MAX_VALUE else expires)
        val old = list.firstOrNull { it.nextHop == route.nextHop }
        if (old != null && old.hops < route.hops && old.expiresAt > now()) return
        list.removeAll { it.nextHop == route.nextHop }; list += route
        list.sortWith(compareBy({ it.hops }, { it.nextHop })); while (list.size > 3) list.removeAt(list.lastIndex)
        if (old?.path != route.path) metrics = metrics.copy(routeChanges = metrics.routeChanges + 1)
    }
    private fun invalidate(a: String, b: String) {
        table.values.forEach { routes -> routes.removeAll { route ->
            val nodes = listOf(local) + route.path
            route.nextHop !in neighbors || nodes.zipWithNext().any { (x, y) -> (x == a && y == b) || (x == b && y == a) }
        } }
        table.entries.removeAll { it.value.isEmpty() }
        metrics = metrics.copy(routeChanges = metrics.routeChanges + 1)
    }
    fun expire() {
        val time = now(); seen.entries.removeAll { it.value <= time }
        table.values.forEach { it.removeAll { route -> route.expiresAt <= time || route.nextHop !in neighbors } }
        table.entries.removeAll { it.value.isEmpty() }
    }
}
