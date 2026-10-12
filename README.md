# Wibrio

Native Android offline mesh communication project. **Phase 2 direct-link implementation; physical-device validation pending.**

## Open and build

Open this directory in Android Studio. Use JDK 17 or newer, Android SDK 36 and the checked-in Gradle 8.13 wrapper. Configure your SDK through Android Studio or `ANDROID_HOME`; never commit `local.properties`.

```powershell
.\gradlew.bat :app:assembleDebug
```

Package: `com.wibrio.app`. Version 0.2.0. Minimum Android 8 / API 26; target API 36. Google Play services is required for Nearby networking.

## Current capabilities

- Compose Material 3 light/dark theme, supplied original Wibrio logo.
- Splash, onboarding, local display-name setup and persistent DataStore preferences.
- API-specific wireless permission requests and app-settings recovery.
- Device Wi-Fi/Bluetooth status read from platform services, with permission-aware fallback.
- Dashboard, mesh controls, nearby people, inbox, conversation shell, group availability, file/transfer availability, network map, profile, settings, security, diagnostics, voice/video availability screens.
- Hilt application/ViewModel injection and lifecycle-aware StateFlow collection.
- Independent Kotlin mesh module with transport, identity, messaging and mesh contracts.

Phase 2 adds real Nearby P2P_CLUSTER advertising/discovery, explicit numeric-code confirmation on both phones, Android Keystore P-256 identity, signed challenge/proof handshake, direct text exchange and signed final-recipient ACKs. People/inbox/conversation/map/diagnostics now reflect actual connection and traffic state. Disconnecting disables sending; reconnecting always requires user verification. No demo peers or fabricated delivery status are used.

Discovery is bounded to two minutes. Connections and message history are foreground, best-effort session state; history is capped at 500 messages and lost on process termination. There is no persistent offline queue, automatic retransmission, guaranteed background/reboot recovery, multi-hop routing, QR, application E2EE, groups, files or WebRTC yet. See [security limits](docs/PHASE2_SECURITY.md).

## Design status

Figma connector access was denied for the original file and shared copy. The owner then supplied `Wibrio — Premium Mesh Communication UI.fig` locally and authorized implementation from its embedded thumbnail. Eight screen layouts have been adapted to native Compose from that 385 × 400 preview. Full editable canvas and exact measurements remain unavailable; this is not a pixel-perfect match. See [design reference and limitations](docs/DESIGN_REFERENCE.md).

## Validation

Android Studio/emulator/physical-device testing is intentionally left to the owner. Follow the [two-phone Phase 2 guide](docs/PHASE2_TESTING.md); the [Phase 1 UI checklist](docs/MANUAL_TESTING.md) is historical. JVM signature/identity and replay-window tests are included. Build/test results are tracked in [development status](docs/STATUS.md). Compilation and these tests do not prove working wireless behavior.

## Next milestone

First validate Phase 2 on two physical phones without internet and fix any transport/device-specific failures. Then Phase 3: transport-independent multi-hop packets, bounded forwarding and deterministic topology/recovery tests. Persistent encrypted messaging and production security review remain required before release.
