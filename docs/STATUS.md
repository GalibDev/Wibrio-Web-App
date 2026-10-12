# Development status

**Current milestone:** Phase 2 direct connectivity is implemented; see [Phase 2 status](PHASE2_STATUS.md) and [two-device test steps](PHASE2_TESTING.md). The following Phase 1 notes are historical and do not describe current transport availability.

Phase 1 foundation implementation and debug build are complete. Owner's UI/navigation validation remains pending. No Android Studio, emulator, instrumentation, unit or physical-device tests have been run, in accordance with the owner's manual testing workflow. Figma connector access remains denied. Eight screen layouts now follow the embedded thumbnail in the owner's local `.fig` export; full editable canvas was not read. See `DESIGN_REFERENCE.md` for fidelity limits.

## Build verification — 2026-10-12

Command: `./gradlew.bat :app:assembleDebug --console=plain`

Latest design update result: **BUILD SUCCESSFUL in 8s**, 44 actionable tasks (6 executed, 38 up-to-date), using installed JBR 21 and SDK 36 with Java 17 bytecode target. Original foundation build also passed.

APK: `app/build/outputs/apk/debug/app-debug.apk` (generated, intentionally excluded from Git).

Initial build compiled Kotlin/Java but failed during packaging due to insufficient C: disk space. A build-output relocation attempt hit the KSP Windows cross-drive path limitation and was removed. Retrying the normal project layout succeeded once space became available. No personal files were deleted.

## Created source groups

- Root Gradle Kotlin DSL, Gradle wrapper, Git configuration rules.
- `app`: manifest/resources, application/activity, reusable Compose components, ViewModel and DataStore profile store.
- `core/mesh`: transport-neutral node, peer, state, delivery, transport, messaging and identity contracts.
- `docs`: architecture, milestone checklist, build status and manual device checklist.

## Remaining scope

Exact Figma synchronization, responsive/accessibility visual verification and real-device testing are pending. Cryptographic identity and QR, Nearby discovery/verification/messaging, Room storage, routing, encryption, groups/files, calls and background networking belong to later milestones. Unavailable screens do not simulate those features.

Next recommended milestone: Phase 2 direct connectivity with persistent cryptographic identity, explicit peer authentication and a two-device offline messaging validation plan.
