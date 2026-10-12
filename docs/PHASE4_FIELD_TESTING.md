# Wibrio Phase 4 physical field checklist

All rows start **NOT RUN**. Automated tests are not a substitute for recording these results. Use the [CSV template](PHASE4_FIELD_RESULTS.csv), one row per scenario/phone pair; keep exported JSON reports from every device.

## Equipment and baseline

- [ ] At least four Android phones with Google Play services; five for alternative routes, ten for larger topology. Include multiple vendors and API 26–30, 31–32, 33–36 when available. Record model, API, Play services version, app version and APK SHA-256.
- [ ] Install the same 0.4.0 APK on every phone. Service namespace v4 intentionally isolates 0.3.0's non-durable ACK semantics; mixed versions will not discover one another. Updating from 0.3.0 should retain Keystore fingerprint and trusted peer pins; previous volatile message history cannot be recovered. Screenshot full fingerprints before and after update/reboot.
- [ ] Use names A/B/C/D/E…, unique test-run ID and numbered non-sensitive text. Align device clocks before going offline; signed packet validity allows limited skew.
- [ ] Disable mobile data and Internet access. Keep Bluetooth and Wi-Fi enabled with no Internet-connected access point; alternatively use airplane mode and explicitly re-enable those radios if supported. Record settings and verify that a browser cannot reach a website. Airplane mode with all radios disabled is not a mesh test.
- [ ] Grant requested wireless permissions. On older devices, enable Location services if Nearby requires it. Allow notifications in Diagnostics to see the service notification. Record denials and recovery behavior separately.
- [ ] Open Wibrio → Settings → Network diagnostics → enable recording **on every device before starting**. Start Mesh from the visible app. Confirm persistent service notification/active-app entry. Export a baseline JSON locally using the document picker.

## Mandatory secure link setup

Create only A–B, B–C and C–D links initially, using People → Connect. Compare numeric codes on both physical phones, accept only matching recognized devices, wait for identity proof completion. Never approve a name alone. Restart discovery after its two-minute window if needed; existing links must remain active. Do not create A–C, A–D or B–D shortcuts for the line test. Record confirmed direct edges and A's reported route to D (three hops).

Relayed endpoints A and D must independently compare the full Profile fingerprints over a trusted channel and approve the remote identity. For B's own messaging with C, use their verified direct link. Incorrect code/fingerprint must be rejected. Reconnecting after any stop/restart still requires numeric code comparison; saved trust does not auto-accept radio links.

## Acceptance scenarios

| Scenario | Procedure | Required evidence |
| --- | --- | --- |
| Two-phone offline | A↔B: 20 numbered texts each direction, Wi-Fi/BT on and Internet disabled | Each UUID appears once; destination ACK required; exports from A/B |
| Four-phone line | A→D and D→A: 20 texts each direction; no endpoint shortcut links | Correct text/UUID on recipient, three-hop route at A, relay forwarding counters; no readable transit text in relay chats |
| Simultaneous roles | While A↔D traffic runs, B↔C exchange their own numbered text at about one message/second per sender | B/C send and receive their own traffic while forwarding A/D; count loss/duplicates/latency; do not assume API topology support proves this |
| Authentication negative | Reject mismatching/unrecognized code and leave remote fingerprint unapproved | No accepted link or outbound composer for unapproved peer; no auto-reconnect bypass |
| Relay disabled | Turn relay off at B, send A→D, but send B↔C own traffic | Transit stays unconfirmed/queued/expires; B's own traffic still works; re-enable and retry a new message |
| Alternative route (five phones) | Add A–E and E–C, wait for advertisements; disconnect B's relevant links or stop B; send A→D | Surviving route via E, destination ACK, observed local recovery timing; exports from all five |
| Partition and heal | Disconnect A–B and A–E, create one pending message immediately; reconnect a route with code verification before its 60-second deadline | Same message UUID eventually delivered once; retry count and latency include outage |
| Persistent origin queue | Queue while partitioned, force-stop A, reopen, Start Mesh and reverify link before deadline | Queued UUID survives restart; attempts/deadline do not reset; only destination ACK marks delivered |
| Persistent recipient/ACK loss | Interrupt recipient link immediately after receipt but before sender ACK (repeat until observed), restart recipient, reconnect before deadline | One stored recipient UUID after retry, sender eventually delivered or honest expiry; record if timing could not induce ACK loss |
| Expiry/reboot | Queue while partitioned, force-stop/reboot and leave >60 seconds; reopen | Queue is expired, not delivered; durable prior history/fingerprint remain; radios do not auto-start |
| Discovery resume | Wait >2 minutes with live links, resume discovery, wait another full window | Existing messages/links remain; window stops as expected |
| Background | Active FGS: Home, screen off/lock for 1/5/15 minutes; exchange messages through background relay | Record delivery/loss, notification state, battery/OEM policy, whether OS killed service; no guaranteed success assumed |
| Lifecycle shutdown | Stop from notification; stop in UI; OS active-app Stop; swipe task; 30-minute cap | Explicit stops end radios; process death does not silently restart; pending deadlines/history recover on reopening; record OEM swipe behavior |
| Permission/radio disruption | Revoke permission or toggle Bluetooth/Wi-Fi off mid-session; restore permissions/radios and reconnect | Honest error/state, no fabricated delivery, no crash, code verification again |
| Export and privacy | Export to local Documents without Internet after each scenario | JSON opens, peer/routes/counters/timing present; no chat content, private keys or signatures; cancellation causes no crash |

Duplicate, replay, wrong-recipient, malformed-packet and disk-failure injection are covered automatically. Do not label them physically verified without an authorized device harness that can inject signed/encoded traffic and record the rejection. No such production backdoor is included.

## Outdoor distance progression toward 1 km

1. Choose a permitted open pedestrian area with clear line of sight, a person at each phone and safe communication between testers. Record weather, obstacles, device height/orientation, pocket/body obstruction, battery/temperature and radio settings. Mark measured positions; route hops alone do not measure distance.
2. Establish a reliable single link at 5–10 m. Send at least 20 numbered messages both ways at each position. Increase spacing in measured 10–25 m steps and repeat; record attempts, ACK count, missing/duplicate UUIDs, last/mean latency from reports and independently measured end-to-end elapsed times.
3. Move back inside the distance with consistently successful delivery, then add relays one at a time. Test a measured three-hop A–B–C–D line first. Internet stays off; endpoints must not have a direct shortcut. Record actual local direct links, not inferred physical edges from a route path.
4. Extend total corridor length only while each adjacent link is repeatably reliable. Target intermediate totals of 100/250/500/750 m before attempting 1,000 m. These are test targets, **not expected or guaranteed coverage**. If a link fails, reduce spacing/add a relay within protocol limits or record the target as unattainable with that equipment.
5. Protocol maximum is eight hops (up to nine phones in a simple end-to-end line). A nine-hop ten-phone line exceeds the limit. The ten-node automated topology uses shortcuts; it does not establish outdoor reach. A 1 km eight-hop line would require roughly 125 m per link, which ordinary phones/Nearby may not support. Do not remove limits or imply a stronger radio to obtain a claimed target.
6. At each achieved total distance, run both directions, B's own endpoint traffic plus relay traffic, a relay drop, and an alternate-path recovery if terrain permits. Repeat with screen-off/background policies and different vendors. Export per-phone reports before Stop, since diagnostic event rings are memory-only.
7. Report measured longest repeatable distance, number of hops, delivery ratio (`destination ACKs / attempted messages`), duplicate count, median/p95 latency calculated from field timestamps if enough samples, observed recovery, and failed configurations. Distinguish unacknowledged-but-received from never received. Preserve failures as well as successes.

## Optional ADB observations (not Android Studio)

Use authorized USB devices only: `adb devices -l`, `adb shell dumpsys activity services com.wibrio.app`, `adb shell dumpsys package com.wibrio.app`, and timestamped `adb logcat` around a failure. `adb shell am force-stop com.wibrio.app` deliberately stops radios; reopening is required. Do not clear app data/uninstall when testing restart persistence. Redact unrelated logs before sharing.

Send back per-device JSON reports, the completed CSV, exact numbered messages/UUIDs that failed and whether each run was foreground/locked/background. These observations are the input for subsequent reliability fixes.
