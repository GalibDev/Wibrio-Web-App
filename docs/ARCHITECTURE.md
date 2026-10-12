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
- [ ] Phase 4 encrypted reliable persistent messaging and security review.
- [ ] Phase 5 authenticated groups and integrity-checked chunked files.
- [ ] Phase 6 direct WebRTC calling with verified IP connectivity.
- [ ] Advanced routing/transports and release auditing.

## Security boundary

Display name is not a device identity. Nearby endpoint identifiers must never serve as permanent IDs. Relays must never mark a message delivered; final authenticated recipient ACK is required. Future transport must reject unknown peers until the user confirms authentication. Private keys belong in Android Keystore. No encryption/security claim is made for this foundation. There is no INTERNET permission or server dependency in current application runtime; build dependency downloads require internet.

## Platform considerations

Wireless permissions differ across API 26–30, 31–32, and 33+. Location remains needed through API 32 for planned Nearby discovery. Android 12+ Bluetooth permissions and Android 13+ nearby Wi-Fi access are requested explicitly. Notification, microphone, camera and foreground-service permissions are deferred until corresponding functionality exists. Nearby selects its own underlying transports; UI priority preferences cannot force them.
