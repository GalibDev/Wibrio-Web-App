package com.wibrio.app

import com.wibrio.mesh.MeshRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class DiagnosticEvent(val elapsedMs: Long, val kind: String, val detail: String)
data class DiagnosticState(val enabled: Boolean = false, val events: List<DiagnosticEvent> = emptyList(), val lastRecoveryMs: Long? = null, val recoveries: Int = 0)

/** Bounded metadata-only recorder. Route recovery is observed locally, not global convergence. */
class NetworkDiagnostics(private val elapsed: () -> Long) {
    private val mutable = MutableStateFlow(DiagnosticState())
    val state = mutable.asStateFlow()
    private var previous = emptyMap<String, String>()
    private val lost = linkedMapOf<String, Long>()
    @Synchronized fun configure(enabled: Boolean) { previous = emptyMap(); lost.clear(); mutable.value = DiagnosticState(enabled) }
    @Synchronized fun event(kind: String, detail: String) {
        if (!mutable.value.enabled) return
        mutable.value = mutable.value.copy(events = (mutable.value.events + DiagnosticEvent(elapsed(), kind.take(40), detail.take(160))).takeLast(200))
    }
    @Synchronized fun routes(routes: List<MeshRoute>) {
        if (!mutable.value.enabled) return
        val now = elapsed()
        val current = routes.groupBy { it.destination }.mapValues { it.value.minWith(compareBy({ r -> r.hops }, { r -> r.nextHop })).nextHop }
        lost.entries.removeAll { now - it.value > 120_000 }
        (previous.keys - current.keys).forEach { if (lost.size < 128) lost.putIfAbsent(it, now) }
        current.forEach { (node, next) ->
            val missingSince = lost.remove(node)
            if (missingSince != null || (previous[node] != null && previous[node] != next)) {
                val duration = if (missingSince == null) 0 else (now - missingSince).coerceAtLeast(0)
                mutable.value = mutable.value.copy(lastRecoveryMs = duration, recoveries = mutable.value.recoveries + 1)
                event("route_recovered", "${node.take(12)} after ${duration}ms; 0ms means alternate route already available")
            }
        }
        previous = current
    }
    @Synchronized fun resetSession() { previous = emptyMap(); lost.clear(); event("session", "Stopped; persisted pending messages retained until deadline") }
}

object DiagnosticsReport {
    fun encode(fields: Map<String, Any?>, routes: List<MeshRoute>, state: DiagnosticState): ByteArray {
        val root = JSONObject(fields).put("schema", 1).put("physicalTestingVerified", false)
            .put("privacy", "No message text, private keys, encryption keys or signatures. Node identifiers and device metadata are included.")
            .put("diagnosticsEnabled", state.enabled).put("routeRecoveries", state.recoveries)
            .put("lastObservedRouteRecoveryMs", state.lastRecoveryMs ?: JSONObject.NULL)
        root.put("routes", JSONArray().also { list -> routes.forEach { list.put(JSONObject().put("destination", it.destination).put("nextHop", it.nextHop).put("hops", it.hops).put("path", JSONArray(it.path)).put("expiresAt", if (it.expiresAt == Long.MAX_VALUE) JSONObject.NULL else it.expiresAt)) } })
        root.put("events", JSONArray().also { list -> state.events.forEach { list.put(JSONObject().put("elapsedMs", it.elapsedMs).put("kind", it.kind).put("detail", it.detail)) } })
        return root.toString(2).toByteArray()
    }
}
