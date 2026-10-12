# Phase 4 — device validation preparation and reliability hardening

Status: implementation and automated validation; **physical mesh connectivity, background radio behavior and outdoor range remain unverified**. This milestone does not certify a working 1 km network. See [field checklist](PHASE4_FIELD_TESTING.md) for the required physical acceptance gate.

## Audit of the starting repository

Android Phase 3 commit `601a869` exists. The local checkout also contains an unrelated web milestone `f186eac`; `origin` points at Wibrio-Web-App and `android-origin` points at Wibrio. Android changes must be published to the latter without publishing the web milestone into Android main. The owner's untracked `.fig` copy is left untouched.

Before changing code, `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest :core:mesh:test` succeeded. The 21 original JVM tests were present. Inspection confirmed P2P_CLUSTER, manual numeric code acceptance, persistent Keystore identity, signed fresh-nonce identity proof, per-link replay window, Tink HPKE endpoint ciphertext, bounded route flooding/cache/expiry, destination-signed ACK and retry/dedup logic. These are implementation facts, not evidence of real wireless connectivity.

Gaps found: queues/history/dedup were volatile; retry ciphertext could not survive fresh endpoint encryption sessions; Nearby transfer completion callbacks were ignored; there was no foreground service, diagnostics export, latency/recovery measurement or ten-node coordinator test. Handshake/discovery timers needed stronger generation guards. Escaped control characters could pass the text byte limit but overflow the encrypted envelope and strand a queued message.

## Reliability changes

- `MessageJournal` preserves the existing coordinator/repository/routing boundaries. `RoomMessageJournal` atomically replaces one bounded encrypted SQLite snapshot containing history, origin outbox and receipt dedup. Single-row storage deliberately avoids an unrelated architecture migration; pagination and incremental writes belong to later optimization.
- Standard AES-256-GCM uses a provider-generated fresh IV and a non-exportable Android Keystore key. Ciphertext authenticates a format-specific context. Backup and device-transfer exclusions prevent moving the database without its device-bound key. Corruption/key loss fails closed, reports failure and does not silently erase or overwrite existing history.
- History is capped at 500 messages, origin outbox at 32, receipt dedup at 1,024 entries retained up to 24 hours. A full live receipt table refuses new messages instead of evicting replay protection. These are intentional prototype limits.
- Persist logical message UUID, text, creation/deadline, attempt count and last attempt. Origin text is encrypted locally at rest; ephemeral HPKE private keys remain memory-only. On retry, encrypt to the latest signed, approved recipient session. Sender/recipient restarts retain the logical ID, preventing duplicate stored text after lost ACK. Each new radio link still requires code verification.
- Save the outbox before handoff; save inbound text and receipt dedup before sending ACK; save outgoing delivered state and remove pending work together before displaying delivery. Intermediate ACKs, wrong origin, wrong recipient, wrong session and broadcast ACKs cannot mark destination delivery.
- Nearby service namespace is now `com.wibrio.app.mesh.v4`, isolating 0.3.0 peers whose ACK did not imply durable storage. All field-test phones must update together. The routing packet format remains version 3; this is an application capability boundary, not a cryptographic guarantee against a modified dishonest endpoint.
- Restart and Stop preserve pending work; reopening normalizes pending state to queued or expired. Absolute 60-second deadlines and the five-attempt budget are not reset by restart. Clocks moving behind message creation fail that pending item closed. Broader clock synchronization is not implemented.
- Retry remains every ten seconds, with maintenance every five seconds. Radio task acceptance alone no longer counts as handoff: send awaits the matching outbound payload's SUCCESS update, with bounded timeouts. Failure, cancellation, disconnect and session stop fail the outstanding transfer. BYTES reception is complete in `onPayloadReceived`, per Google's API documentation.
- Discovery stays bounded to two minutes and can resume while preserving links. A generation token prevents an old timer from ending a new window. Handshake timers match the actual session object as well as epoch, preventing an old timeout from disconnecting a replacement session. Expired identity advertisements are removed from the bounded directory.
- The history cap preserves rows still represented in the pending outbox; high incoming traffic cannot hide a pending message. Relay opt-out is persisted across app restarts.

## Android lifecycle

Start Mesh from the visible activity starts a `connectedDevice` foreground service with a low-importance ongoing notification and Stop action. Required normal/type permissions are declared; wireless permissions are checked before radio use. Android 13+ notification permission can be requested in Diagnostics and is separate from wireless eligibility. Denying notifications does not bypass foreground service rules; inspect Android's active-app/task UI as well.

The service is START_NOT_STICKY, not boot-started, and sessions are capped at 30 minutes. Clearing a ViewModel no longer tears down a user-started mesh. Explicit Stop, service destruction, process death or OS action stops radios; restart requires opening the app and confirming fresh links. An FGS improves lifecycle ownership but does not guarantee radio availability, Doze operation, screen-off relaying or OEM survival. No battery exemption, wake lock, unlimited background loop or automatic trust bypass was introduced.

## Diagnostics

Settings → Network diagnostics provides a persisted recording toggle, bounded 200-event memory ring, connection/discovery state, verified direct/reachable peer counts, observed route table/hops, sent/completed and received counters, routing duplicates/rejections, link replay/rejection counters, ACKs, retries and operation failures. Event recording defaults off. Disable clears event/timing state; aggregate operational counters continue for the process.

Delivery latency runs from origin queue creation until authenticated destination ACK, including outages/retries. The report includes last and mean values for observed ACKs. Route recovery measures loss/reappearance or next-hop replacement at this device using elapsed realtime; 0 ms means an alternate was already available, not instantaneous network-wide convergence. Outages older than two minutes are no longer timed. Diagnostics must be enabled before inducing an outage.

Export JSON through Android's Create Document picker to local storage while offline. Reports include app/device/OS identifiers, local/route node IDs, endpoint state, permissions, counters, timing and bounded events. They exclude message content, encryption/signing keys and signatures. Public node IDs and device metadata can identify participants: share with the test team intentionally. Events/counters are not crash-persistent, so export after each experiment. JSON always states physical testing is unverified; attach an independent field record to document actual observations.

## Automated coverage and evidence

Production continues to bind only `NearbyTransport`; simulated networking is under `src/test` only. The four-, five- and ten-node simulator runs the actual coordinator with P-256 signatures, Tink encryption, encoded packets and an injected clock. It verifies simultaneous bidirectional messaging while a relay is also an endpoint, duplicate traffic and alternate paths. Ten-node topology includes shortcuts to respect the unchanged eight-hop maximum; ten nodes does not mean nine-hop line support.

Additional tests cover sender/recipient restart, retained queue UUID/deadline/attempt budget, durable lost-ACK dedup, receipt/outbox disk-write failures, old-session ciphertext replay, relay opt-out, addressed ACK requirement, queue limits/expiry, escaped-text envelope bounds, outbound payload completion matching/disconnect/failure, bounded diagnostics/recovery and valid JSON export. Robolectric tests actual Room/SQLite close/reopen and ciphertext-at-rest with a JVM AES key; cryptographic tests cover wrong key, IV uniqueness and tampering. They do not exercise hardware Keystore or Google Play services radios.

Validation command:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:lint :app:test :core:mesh:check --console=plain
```

Final build/test counts and lint findings are recorded in [verification results](PHASE4_RESULTS.md). No physical-device success is inferred from those results.

## Remaining limits and Phase 5 recommendations

Physical direct/multi-link/send-receive-relay/offline/range/background/reboot scenarios remain an acceptance gate. Independent protocol/security review remains pending. A malicious verified relay can drop/delay/misreport paths and cause denial of service; route metadata is not cryptographic evidence of each physical edge. There is no ratcheting/forward-secrecy claim for the complete protocol. Signed packet expiry depends on reasonably aligned wall clocks.

Only the origin owns a durable outbox. Relays do not durably store ciphertext for arbitrarily delayed store-and-forward. Delivery means durable recipient acceptance, not human reading or indefinite retention beyond bounded history. If all five attempts are exhausted after a lost ACK, state may expire despite actual recipient acceptance. Uninstall/clear-data/key loss destroys access to local history and identity. There is no delete/export/retention/trust-revocation UI yet. Full-snapshot encryption/SQLite writes and serialized radio operations need device performance measurement before scaling traffic.

Phase 5 should first fix failures discovered in the field and review cryptography/trust revocation, then optimize incremental storage/retention and diagnostics. Only after that gate, add authenticated group membership/encryption and checksum-verified chunked direct file transfer with cancellation/resume; validate direct transfers before multi-hop files. Calling still requires a separately verified local IP/media architecture.

## Primary platform references reviewed

- [Nearby strategies](https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Strategy): P2P_CLUSTER permits M-to-N topology; hardware/OS capabilities still require device validation.
- [Nearby connection management](https://developers.google.com/nearby/connections/android/manage-connections): authentication comparison and explicit accept/reject.
- [Nearby data exchange](https://developers.google.com/nearby/connections/android/exchange-data): BYTES receipt versus payload completion callbacks.
- [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) and [background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start): connectedDevice eligibility and visible-user start.
- [Room releases](https://developer.android.com/jetpack/androidx/releases/room): pinned Room version and supported Android baseline. Dependency upgrades are separate from this reliability milestone.
