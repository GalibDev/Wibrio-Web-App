# Wibrio

Native Android offline mesh communication project. **Phase 3 experimental multi-hop implementation; physical-device validation pending.**

## Open and build

Open this directory in Android Studio. Use JDK 17 or newer, Android SDK 36 and the checked-in Gradle 8.13 wrapper. Configure your SDK through Android Studio or `ANDROID_HOME`; never commit `local.properties`.

```powershell
.\gradlew.bat :app:assembleDebug
```

Package: `com.wibrio.app`. Version 0.3.0. Minimum Android 8 / API 26; target API 36. Google Play services is required for Nearby networking.

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

Discovery is bounded to two minutes and can resume without disconnecting existing links. Mesh sessions end after 30 minutes. History (500 rows) and pending messages (32, up to 60 seconds) are volatile; no durable store-and-forward, encrypted database, guaranteed background/reboot recovery, key management UI, groups, files or WebRTC exists. The complete protocol has not had independent security review.

## Design status

Figma connector access was denied for the original file and shared copy. The owner then supplied `Wibrio — Premium Mesh Communication UI.fig` locally and authorized implementation from its embedded thumbnail. Eight screen layouts have been adapted to native Compose from that 385 × 400 preview. Full editable canvas and exact measurements remain unavailable; this is not a pixel-perfect match. See [design reference and limitations](docs/DESIGN_REFERENCE.md).

## Validation

Android Studio/emulator/physical-device testing is intentionally left to the owner. Follow the [four/five-phone Phase 3 guide](docs/PHASE3_TESTING.md). JVM tests include deterministic topology/recovery, real signed/encrypted coordinator exchange and replay/security cases. Compilation and simulator tests do not prove Android radio behavior. Earlier milestone guides are historical.

## Next milestone

Validate direct and multi-hop messaging on physical phones without internet, then fix device-specific failures. Phase 4 adds persistent encrypted storage, durable queues/receipts, identity/trust management, reliability hardening and security review.
