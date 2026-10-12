package com.wibrio.app

import com.wibrio.mesh.MeshRoute
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NetworkDiagnosticsTest {
    @Test fun recordsBoundedEventsAndObservedRecoveryWithoutInventingConvergence() {
        var time = 1000L; val recorder = NetworkDiagnostics { time }
        recorder.event("ignored", "off"); assertTrue(recorder.state.value.events.isEmpty())
        recorder.configure(true)
        val route = MeshRoute("D", "B", listOf("B", "C", "D"), 90_000)
        recorder.routes(listOf(route)); time += 100; recorder.routes(emptyList())
        time += 750; recorder.routes(listOf(route.copy(nextHop = "E", path = listOf("E", "C", "D"))))
        assertEquals(750L, recorder.state.value.lastRecoveryMs)
        repeat(300) { recorder.event("event", "metadata") }; assertEquals(200, recorder.state.value.events.size)
        recorder.configure(false); assertTrue(recorder.state.value.events.isEmpty()); assertNull(recorder.state.value.lastRecoveryMs)
    }
    @Test fun reportIsValidJsonAndNeverClaimsPhysicalValidationOrContainsMessageContent() {
        val bytes = DiagnosticsReport.encode(mapOf("packetsSent" to 3, "peerCount" to 2), listOf(MeshRoute("D", "B", listOf("B", "C", "D"), 90_000)), DiagnosticState())
        val report = JSONObject(bytes.toString(Charsets.UTF_8))
        assertFalse(report.getBoolean("physicalTestingVerified")); assertEquals(3, report.getInt("packetsSent"))
        assertEquals(3, report.getJSONArray("routes").getJSONObject(0).getInt("hops"))
        assertFalse(report.has("messages")); assertFalse(report.has("privateKey"))
    }
}
