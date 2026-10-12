# Connection reliability hotfix (0.4.1)

The reported symptom was a phone seeing an emulator's advertised name but failing to connect. No physical connection has been reproduced or verified here; seeing an advertisement alone does not demonstrate a working data link.

Two implementation defects were corrected:

- Request and acceptance tasks previously waited inside the same state mutex needed by verification/connection callbacks. They now await outside that mutex; failure cleanup rechecks both the mesh epoch and the exact session object. The verification watchdog starts before requesting a connection. Incoming peers appear as `Verify code` even when reverse discovery has not found them.
- The two-minute scan timeout also stopped advertising. It now pauses scanning only; advertising remains active until explicit Stop, service shutdown, or the existing 30-minute session cap. Resuming scanning does not start advertising twice.

Numeric-code approval on both devices and signed identity verification remain mandatory. The production transport is still Google Nearby P2P_CLUSTER. No simulated network is used in production.

## Retest

1. Install the 0.4.1 APK on both participants, stop any old mesh session, grant wireless permissions and start mesh on both. Ensure Bluetooth and Wi-Fi are enabled.
2. Open People and connect from whichever participant discovers the other. Reverse discovery is not required for an incoming connection. Compare the numeric code on both screens and approve only if it matches.
3. Wait for Connected, then exchange messages in both directions. A displayed name or code prompt alone is not success.
4. Stop/restart both sessions. Leave A active for more than two minutes, then start B and connect from B to A. A must still advertise. Resume discovery on A and verify the existing link survives.
5. Test rejection, timeout and immediate retry; an earlier failed attempt must not remove a replacement session.
6. Repeat with two physical Android phones. Emulator connectivity is a separate compatibility test; emulator radio simulation, host networking and Google Play services can affect it. This patch does not claim physical radio passthrough or a verified phone-to-emulator link.
7. Enable diagnostics before testing and export reports from both devices if connection still fails. Record Android/emulator versions, error text, whether both code dialogs appeared, and which participant initiated.

Four automated regression cases cover callback progress while a request is pending, stale failure isolation, current failure reporting and avoiding requests after stop. These tests model task scheduling, not wireless connectivity.

## Automated verification (2026-10-12)

Final source passed `:app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:test :core:mesh:check` in one serial Gradle run. Debug and unsigned release APKs built. App tests: 29 debug and 29 release executions; core tests: 19. Total: 48 distinct tests / 77 executions, zero failures, errors or skips. Each lint variant reports zero errors and 27 warnings. No device was attached to ADB for final retesting; phone-to-emulator and phone-to-phone connectivity remain unverified.
