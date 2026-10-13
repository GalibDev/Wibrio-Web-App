package com.wibrio.mesh

/** Current reachable identities only; saved conversations are deliberately excluded. */
data class NetworkOverview(val peers: List<Peer>) {
    val reachable get() = peers.size
    val direct get() = peers.count { it.hops == 1 }
    val relayed get() = peers.count { (it.hops ?: 0) > 1 }
    companion object {
        fun from(peers: List<Peer>) = NetworkOverview(peers.filter { (it.hops ?: 0) in 1..8 }
            .groupBy { it.id }.values.map { copies -> copies.minBy { it.hops!! } }.sortedBy { it.displayName })
    }
}
