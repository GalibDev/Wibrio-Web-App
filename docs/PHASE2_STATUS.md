# Phase 2 implementation status

Implemented: Nearby P2P_CLUSTER discovery/advertising, explicit accept/reject with authentication digits, persistent Android Keystore signing identity, endpoint-to-node mapping after signed challenge proof, verified direct text messaging, recipient-specific signed ACK, bounded volatile history, duplicate/replay suppression, bounded incoming queue, disconnect handling, user-driven reconnect and live diagnostics.

The status is **implementation ready for physical-device validation**, not a claim that wireless behavior has already been proven. No Android Studio, emulator, instrumented or physical-device test was run by the developer. JVM tests cover signature verification/tampering/key binding (3) and replay-window duplicate/reordering/stale-sequence cases (3). They do not cover the complete Nearby callback lifecycle or Android Keystore runtime.

Build uses Kotlin 2.2.21, AGP 8.13.2, target SDK 36 and Nearby 19.3.0. Nearby 19.5.1 was rejected by the Kotlin compiler because it contains Kotlin 2.4 metadata; no metadata compatibility check was disabled.

Start/permission failures are visible. Advertising and discovery must both start successfully, or the session is shut down. Discovery stops after two minutes; restart after expiry resets links. Pending verification times out after 45 seconds. Nearby Tasks time out after 20 seconds. The app never auto-accepts or silently reconnects trusted peers. Automatic retry, persistent queues, durable receipts, multi-hop routing, full E2EE, file/group/call/background service remain out of scope.

Read `PHASE2_TESTING.md` before two-phone validation and `PHASE2_SECURITY.md` before interpreting any security/delivery claims. The original `STATUS.md` below remains the historical foundation build record.

Latest checked command: `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :core:mesh:test --console=plain` — successful. Six JVM tests passed with zero failures/errors. The local Android Studio-generated `local.properties` points to a missing SDK directory; Gradle successfully used the installed SDK through environment configuration. That machine-local file is ignored by Git and has not been overwritten.
