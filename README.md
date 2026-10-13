# Wibrio

## Web app · Phase 1

The responsive Next.js web interface lives in [`web/`](web/README.md). Run `cd web`, `npm ci`, then `npm run dev`. It offers explicitly labeled local demo networking, IndexedDB chats, all required screens, and light/dark themes. It does not use the native Android networking engine below.

Native Android offline mesh communication project. **Phase 4 reliability hardening implemented; physical-device acceptance pending.**

## Open and build

Open this directory in Android Studio. Use JDK 17 or newer, Android SDK 36 and the checked-in Gradle 8.13 wrapper. Configure your SDK through Android Studio or `ANDROID_HOME`; never commit `local.properties`.

```powershell
.\gradlew.bat :app:assembleDebug
```

Package: `com.wibrio.app`. Version 0.5.0. Minimum Android 8 / API 26; target API 36. Google Play services is required for Nearby networking.

## Current capabilities

- Compose Material 3 light/dark theme, supplied original Wibrio logo.
- Splash, onboarding, local display-name setup and persistent DataStore preferences.
- API-specific wireless permission requests and app-settings recovery.
- Device Wi-Fi/Bluetooth status read from platform services, with permission-aware fallback.
- Dashboard, mesh controls, nearby people, inbox, conversation shell, group availability, file/transfer availability, network map, profile, settings, security, diagnostics, voice/video availability screens.
- Hilt application/ViewModel injection and lifecycle-aware StateFlow collection.
- Independent Kotlin mesh module with transport, identity, messaging and mesh contracts.

Phase 2 adds real Nearby P2P_CLUSTER advertising/discovery, explicit numeric-code confirmation on both phones, Android Keystore P-256 identity, signed challenge/proof handshake, direct text exchange and signed final-recipient ACKs. People/inbox/conversation/map/diagnostics now reflect actual connection and traffic state. Disconnecting disables sending; reconnecting always requires user verification. No demo peers or fabricated delivery status are used.

Phase 3 adds transport-independent bounded flooding, cached/alternative routes, link-down invalidation, route expiry, hop limits, multi-hop forwarding and an explicit relay switch. Relayed peer identities require fingerprint approval. Text is encrypted to the recipient using Tink HPKE and origin-signed; only a signed final-recipient ACK marks delivery. Bounded origin queues retry pending messages over recovered routes without duplicate text. See [protocol and security limits](docs/PHASE3.md).

Phase 4 adds encrypted Room history/outbox/receipt persistence, retry across fresh endpoint sessions, durable-before-ACK delivery, outbound Nearby transfer completion tracking, generation-safe lifecycle timers, a user-started connected-device foreground service and configurable diagnostics with JSON export. History is capped at 500 rows, outbox at 32 messages/60 seconds/five attempts and persistent receipt dedup at 1,024 entries. Simulated four-, five- and ten-node networks exercise real coordinator signatures/encryption; production still uses only Nearby. See [implementation, audit and limits](docs/PHASE4.md).

Discovery is bounded to two minutes and resumes without disconnecting existing links. Mesh sessions end after 30 minutes. Start Mesh from the visible app; Stop is available in the notification/UI. Process death does not auto-start radios; reopen and confirm fresh link codes to resume pending work before its original deadline. Durable relay storage, guaranteed background/reboot connectivity, key/trust management UI, groups, files and WebRTC remain unavailable. The complete protocol has not had independent security review.

## Design status

Figma connector access was denied for the original file and shared copy. The owner then supplied `Wibrio — Premium Mesh Communication UI.fig` locally and authorized implementation from its embedded thumbnail. Eight screen layouts have been adapted to native Compose from that 385 × 400 preview. Full editable canvas and exact measurements remain unavailable; this is not a pixel-perfect match. See [design reference and limitations](docs/DESIGN_REFERENCE.md).

## Validation

Run `.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:lint :app:test :core:mesh:check --console=plain`. Tests cover deterministic routing, actual signed/encrypted coordinator exchange, persistence/restart failures, replay/ACK guards, transfer completion and diagnostics. Robolectric tests actual Room close/reopen with a JVM key; hardware Keystore and radios remain unverified. See [verification results](docs/PHASE4_RESULTS.md).

Follow the [physical field checklist and outdoor progression](docs/PHASE4_FIELD_TESTING.md), export JSON from Diagnostics on every phone and fill the [results CSV](docs/PHASE4_FIELD_RESULTS.csv). No physical testing or 1 km coverage is claimed. Earlier guides are historical.

## Next milestone

Complete the physical acceptance gate and fix device-specific failures, review security/trust revocation and storage performance, then begin authenticated groups and checksum-verified chunked direct files. Multi-hop media requires separate transport validation.

## Direct calls and live dashboard (0.5.0)

Reachable/Direct/Relayed tiles open live peer lists. Verified direct links take precedence over duplicate relayed identities; saved conversations never inflate counts. Conversation screens provide experimental direct voice and low bandwidth video calls over the existing signed, nonce-bound Nearby link, with explicit incoming acceptance, runtime microphone/camera permissions, mute, speaker, video pause and end controls. Both devices need 0.5.0 and must keep Wibrio visible. Video is small JPEG frames at up to 2 fps, not WebRTC-quality motion. Calls stop when the activity leaves the foreground, the direct peer disconnects, media/signalling stalls or the session limit is reached. No relayed media or background ringing is implemented. See [implementation and physical acceptance steps](docs/DIRECT_CALLS.md); actual microphone, camera, radio throughput and duplex quality remain unverified here.
