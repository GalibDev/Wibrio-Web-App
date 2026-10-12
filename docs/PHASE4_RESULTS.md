# Phase 4 verification and deliverables — 2026-10-12

Physical status: **NOT RUN**. `adb devices -l` returned no attached devices. There was no Android Studio, emulator, instrumentation, real wireless, hardware Keystore, screen-off/OEM or outdoor range test. Phase 4 physical acceptance remains open.

## Automated results

Final command:

Final result: **BUILD SUCCESSFUL in 1m 12s**, 131 actionable tasks (20 executed, 111 up-to-date). The preceding full runs compiled/tested both variants; the final serial run completed the current source and recovered the locked release artifact.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:test :core:mesh:check --no-daemon --max-workers=2 --console=plain
```

| Suite | Tests | Result |
| --- | ---: | --- |
| IdentityVerificationTest | 3 | Pass |
| JournalCipherTest | 3 | Pass |
| MeshCoordinatorTest | 5 | Pass |
| MeshEncryptionTest | 1 | Pass |
| MeshRecoveryTest | 11 | Pass |
| MeshSizeTest (4/5/10 nodes) | 3 | Pass |
| NetworkDiagnosticsTest | 2 | Pass |
| RoomMessageJournalTest (Robolectric/SQLite) | 1 | Pass |
| PayloadTransfersTest | 3 | Pass |
| ReplayWindowTest | 3 | Pass |
| RoutingEngineTest | 9 | Pass |
| **Distinct tests** | **44** | **Zero failures/errors** |

App's 29 tests run for both debug and release; core's 15 tests run once: **73 test executions**, zero failures/errors. The Robolectric Room test uses a JVM AES key and simulated Android/SQLite, not hardware Keystore. In-memory mesh transports exist only in test sources; signatures, packet serialization, Tink encryption and coordinator behavior are real implementation code.

Both debug and release lint pass with **0 errors, 27 warnings each**. Warnings cover newer dependency/toolchain versions, API-specific attributes, existing launcher bitmap conventions and suggested KTX style. Dependencies remain pinned for compatibility, including Nearby 19.3.0/Kotlin 2.2.21. These are not a zero-warning or production-release claim. Local `local.properties` still names an unavailable C: SDK; the build uses installed SDK via ANDROID_HOME. The owner's file was not overwritten.

An intermediate verification overlapped another Gradle run and failed to replace the Windows-locked release `classes.jar`. Gradle/compiler daemons were stopped and the final command was run alone with a single-use daemon. This environment failure is recorded separately from the final verification result; do not run concurrent builds against the same checkout.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (installable debug signing, v0.4.0). Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk` (build verified, unsigned; not ready for installation/distribution). Generated APKs, reports and keys are excluded from commits.

Debug APK SHA-256: `59fde469b6cadc49d20b412e37c2ccfad0bd8c06342194fec5c4cfaf5db45fca`.

## Files changed

| Area | Files |
| --- | --- |
| Build/lifecycle/resources | `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `MeshForegroundService.kt`, `res/drawable/ic_mesh_notification.xml`, `res/xml/data_extraction_rules.xml` |
| Persistent reliability | `MessageJournal.kt`, `RoomMessageJournal.kt`, `JournalCipher.kt`, `MeshCoordinator.kt` |
| Transport/repository | `NearbyTransport.kt`, `DirectMeshRepository.kt`, `core/mesh/.../PayloadTransfers.kt` |
| Diagnostics/UI | `NetworkDiagnostics.kt`, `AppState.kt`, `MainActivity.kt`, `DirectScreens.kt`, `DesignScreens.kt` |
| App tests | `MeshCoordinatorTest.kt`, `MeshReliabilityTest.kt`, `JournalCipherTest.kt`, `RoomMessageJournalTest.kt`, `NetworkDiagnosticsTest.kt`, `SimulatedMesh.kt`, `TestMessageJournal.kt` |
| Core tests | `core/mesh/.../PayloadTransfersTest.kt` |
| Documentation | `README.md`, `docs/ARCHITECTURE.md`, `docs/STATUS.md`, `docs/PHASE4.md`, `docs/PHASE4_RESULTS.md`, `docs/PHASE4_FIELD_TESTING.md`, `docs/PHASE4_FIELD_RESULTS.csv` |

App Kotlin filenames above are under `app/src/main/java/com/wibrio/app/`; app tests under `app/src/test/java/com/wibrio/app/`. Core Kotlin paths end in `com/wibrio/mesh/`. Existing identity/routing architecture and Phase 3 tests were preserved. No web implementation or owner `.fig` asset was changed.

## Physical instructions and remaining gate

Install v0.4.0 on all phones (v4 Nearby namespace isolates earlier non-durable ACK clients), enable diagnostic recording, turn Internet off while keeping required radios on, compare every direct-link code and both remote endpoint fingerprints. Start with A–B–C–D only; then test simultaneous B/C endpoint traffic, E alternate path, partitions, restart persistence and background behavior. Export JSON from each phone after every scenario. Follow [the complete checklist](PHASE4_FIELD_TESTING.md) and fill [the NOT_RUN CSV template](PHASE4_FIELD_RESULTS.csv).

Outdoor progression measures actual adjacent-link distances and extends toward 1,000 m only when repeatable links allow it. Eight-hop limit remains; a ten-phone nine-hop line is outside protocol limits. No 1 km reach or physical delivery guarantee is claimed.

Known limits: 500-history/32-outbox/1,024-receipt bounds, 60-second queue expiry/five attempts, origin-only durable queue, no persistent relay store, clock-skew sensitivity, unreviewed complete cryptographic protocol, malicious-relay denial of service, bounded non-sticky 30-minute FGS, unverified OEM/Doze behavior, memory-only diagnostic event rings and full-snapshot write overhead. Uninstall/clear-data/key loss makes old local data inaccessible. See [detailed implementation and limits](PHASE4.md).

Recommended Phase 5: resolve measured physical failures first; independently review protocol/trust revocation and retention; optimize storage/diagnostic performance; then implement authenticated groups and checksummed chunked direct file transfers with cancellation/resume before extending files to multi-hop. Calling needs separate validated local IP/media transport.
