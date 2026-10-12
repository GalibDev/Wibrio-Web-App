# Wibrio

Native Android offline mesh communication project. **Phase 1 foundation only.**

## Open and build

Open this directory in Android Studio. Use JDK 17 or newer, Android SDK 36 and the checked-in Gradle 8.13 wrapper. Configure your SDK through Android Studio or `ANDROID_HOME`; never commit `local.properties`.

```powershell
.\gradlew.bat :app:assembleDebug
```

Package: `com.wibrio.app`. Minimum Android 8 / API 26; target API 36.

## Current capabilities

- Compose Material 3 light/dark theme, supplied original Wibrio logo.
- Splash, onboarding, local display-name setup and persistent DataStore preferences.
- API-specific wireless permission requests and app-settings recovery.
- Device Wi-Fi/Bluetooth status read from platform services, with permission-aware fallback.
- Dashboard, mesh controls, nearby people, inbox, conversation shell, group availability, file/transfer availability, network map, profile, settings, security, diagnostics, voice/video availability screens.
- Hilt application/ViewModel injection and lifecycle-aware StateFlow collection.
- Independent Kotlin mesh module with transport, identity, messaging and mesh contracts.

No demo peers, routes or delivery states are used. No transport is installed. Start Mesh reports that limitation; calls, groups, messages and transfers cannot start. Counts are zero because this foundation has no networking. There is no cryptographic identity, QR verification, Room message storage, E2EE, background service or WebRTC yet.

## Design status

Figma file `c7FiFlxLRArsjSTztQvG42`, Start Mesh `2:2`, Dashboard `2:28`, Chats `2:147`. The connector denied file access, including the explicit Start Mesh frame. The UI follows the supplied logo and navy/blue/cyan brief with independent Compose components. **It has not been visually synchronized with Figma.** Revisit when connector access is available.

## Validation

Android Studio/emulator/physical-device testing is intentionally left to the owner. See [manual checklist](docs/MANUAL_TESTING.md). Compile results are tracked in [development status](docs/STATUS.md). Compilation is not proof of working wireless behavior.

## Next milestone

Phase 2: Android Keystore identity, Nearby P2P_CLUSTER transport, explicit authentication confirmation, ephemeral endpoint-to-node mapping and real two-device direct messaging. Do not enable production sending before identity/trust and authenticated message handling exist.
