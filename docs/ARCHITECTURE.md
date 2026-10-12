# Architecture and milestones

`app` contains Android entry points, Compose screens, Hilt wiring, ViewModel and DataStore preferences. `core:mesh` is a JVM-only boundary: it depends on coroutines, not Android, Google Nearby or Compose. UI consumes StateFlow through the ViewModel. `DirectMeshRepository` depends on the `DirectLinkTransport` interface; Hilt binds `NearbyTransport`. The adapter owns endpoint callbacks and P2P_CLUSTER operations. The repository owns bounded session state, identity proof and peer mapping, delegating encrypted endpoint messaging and ACK/retry processing to `MeshCoordinator`. `RoutingEngine` owns transport-independent packet forwarding; `DeviceIdentity` owns Android Keystore access. ReplayWindow and routing are independently tested in the JVM module.

Current packages are small to keep Phase 1 understandable. Extract core identity/security/database/transport and feature modules as their implementations arrive; empty Gradle modules add no isolation value. Screen helpers are reusable Compose functions and can be replaced with Figma tokens without changing transport contracts.

## Checklist

- [x] Inspect repository and available environment (initially no Android project or Git repository).
- [x] Create Gradle project, wrapper and module boundaries.
- [x] Theme, navigation, onboarding and profile preferences.
- [x] Runtime permission flow with denial recovery.
- [x] Honest inactive UI and unsupported-feature availability screens.
- [ ] Figma fidelity review (connector access denied).
- [ ] Owner's real-device UI/navigation validation.
- [x] Phase 2 direct wireless transport and identity implementation.
- [ ] Phase 2 two-phone wireless validation by owner.
- [x] Phase 3 routing with deterministic multi-hop simulator tests.
- [ ] Phase 3 real four/five-device validation by owner.
- [x] Phase 4 encrypted Room history/outbox/receipts and restart-aware retry.
- [x] Phase 4 foreground-session ownership, diagnostic export and 4/5/10-node automated tests.
- [ ] Phase 4 physical field acceptance and independent security review.
- [ ] Phase 5 authenticated groups and integrity-checked chunked files.
- [ ] Phase 6 direct WebRTC calling with verified IP connectivity.
- [ ] Advanced routing/transports and release auditing.

## Security boundary

Display name is not a device identity. Nearby endpoint identifiers never serve as permanent IDs. Only an addressed, authenticated final-recipient ACK can mark delivery, after durable recipient storage. Numeric peer verification stays mandatory on each fresh link. Persistent signing and local database encryption keys belong in Android Keystore; ephemeral HPKE private keys stay in session memory. The complete protocol is experimental and has not had independent security review. Production networking binds Nearby only, with no application server; offline operation must still be validated physically.

`MeshForegroundService` owns user-started sessions independently of ViewModel lifetime. `MessageJournal` provides atomic durable snapshots through Room and AES-GCM; the existing mutex serializes state and persistence, and new recipient sessions cause fresh encryption of queued logical messages. `NetworkDiagnostics` records bounded metadata events and observed route recovery; export uses Android's document picker. Simulated transports are confined to test sources.

## Platform considerations

Wireless permissions differ across API 26–30, 31–32, and 33+. Location is requested through API 32 for Nearby discovery. Android 12+ Bluetooth permissions and Android 13+ nearby Wi-Fi access are requested explicitly. A connectedDevice FGS uses declared normal/type permissions; notification access is separately offered on API 33+. Microphone/camera permissions remain deferred. Nearby selects underlying transports; UI priority preferences cannot force them. FGS sessions are non-sticky, user-started and capped at 30 minutes; OEM/Doze/screen-off behavior remains a field-test gate.
