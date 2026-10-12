# Phase 3 — experimental multi-hop mesh

Version 0.3.0 implements bounded multi-hop forwarding over explicitly verified Nearby P2P_CLUSTER links. Every phone can originate, receive and (with its relay switch enabled) forward frames. No permanent master, cloud routing server or fabricated radio link is used. All participating phones must install 0.3.0; its service ID/wire protocol is deliberately separate from 0.2.0.

## Components

`core:mesh/RoutingEngine` is deterministic, single-owner JVM code behind transport and authentication interfaces. `PacketCodec` uses a bounded canonical binary origin-signed body with separate relay path metadata. `MeshCoordinator` owns endpoint messaging/encryption and consumes routing actions. `DirectMeshRepository` serializes session/coordinator operations and maps stable node IDs to transient, verified Nearby endpoints.

Packets include version, UUID, source/destination node IDs, type, creation/expiration, signed hop limit, payload, source public key and signature. Supported types are PRESENCE, DATA, ACK, LINK_DOWN and reserved ROUTE_REQUEST. PRESENCE advertisements and unknown-destination traffic use controlled bounded flooding. Authenticated arrivals learn reverse routes, including up to three alternative next hops. Known destinations prefer the shortest live cached route. Duplicate arrivals can update alternatives but cannot forward/deliver twice. Source-scoped packet dedup prevents one sender's UUID from suppressing another sender's traffic.

Disconnected links invalidate matching cached paths and generate signed LINK_DOWN notifications. If no valid cached route remains, subsequent traffic floods only eligible verified neighbors. Periodic signed presence refreshes reachability; routes expire if not refreshed. Forwarding excludes the incoming node and visited nodes. Hop limit is at most eight, packets expire within two minutes, and routing/queue/rate bounds limit resource use. This is a hybrid cached-route/bounded-flood prototype, **not a completed AODV implementation**.

## Messaging and trust

Origin signatures use the installation's Android Keystore identity. Signed presence binds its display name, fresh mesh-session ID and ephemeral Tink HPKE encryption public key to that identity. Relayed peers are not human-verified just because a relay announced them. Both endpoint users must compare remote Profile fingerprints through a trusted channel before chatting; previously code-verified direct identities remain pinned. Nearby link acceptance still requires numeric comparison every time.

Text payloads use Tink 1.18.0 HPKE (`DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM`). Encryption context binds sender, intended recipient and recipient mesh session. Relays forward ciphertext, not text, and cannot decrypt it with their own session key. The origin-signed packet binds ciphertext and headers. Final-recipient signed ACK binds the message UUID and sender mesh session; relay ACKs cannot mark delivery. ACK means accepted into the recipient process, not durable storage or human reading.

Origin pending messages are capped at 32, retried at ten-second intervals with at most five send attempts and expire after 60 seconds. New routing packet IDs carry the same inner message UUID, allowing a different path while suppressing duplicate recipient text after a lost ACK. Queues/history remain volatile. Queued means no confirmed handoff; relayed means handed to an intermediate next hop, not delivered. A failed radio send remains queued. Stop/process death loses pending work.

## UI and lifecycle

People, inbox, dashboard and conversation use live direct/relayed peers and hop counts. Relayed conversations require fingerprint approval before enabling the composer. Network Map lists confirmed local direct links and observed remote route metrics; it does not draw invented remote physical links. Diagnostics include route alternatives, forwarding, rejection, duplicates, retries and route changes. Start Mesh includes relay opt-out and can resume a two-minute discovery window without tearing down current links. A mesh session is capped at 30 minutes; there is no foreground service or guaranteed background connectivity.

## Verification and limits

Command: `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :core:mesh:test --console=plain`.

Verification on 2026-10-12: debug APK build succeeded; all 21 JVM tests passed with zero failures or errors. No emulator or physical-device tests were run.

JVM coverage includes deterministic four-node forwarding, diamond rerouting, partition healing, TTL/hop limits, signature/body tampering, source-scoped dedup, route expiry, codec validation, relay opt-out, encrypted three-hop endpoint exchange, wrong-recipient decryption failure, context/ciphertext tampering, lost-ACK retry without duplicate text, malicious relay ACK rejection, queued alternate-path recovery, unapproved recipient refusal, message expiry and radio send failure recovery.

The entire protocol has **not** received independent security review or physical-device validation. Simulator tests validate logic, not Android radio capability, Nearby simultaneous links, Android Keystore runtime or battery behavior. Do not advertise this milestone as production-secure messaging.

Relay paths/hop counts are authenticated reports from a verified immediate neighbor, not cryptographic attestation of every physical edge. A malicious relay can drop, delay, misreport paths or deny service; it cannot forge an approved origin signature or decrypt the intended recipient's payload. Traffic metadata (IDs, timing, sizes and route reports) remains visible. Presence replay within its short validity window can temporarily disrupt availability after a key/session change. Phone wall clocks should be within 30 seconds; substantial skew can reject valid packets. There is no key revocation/rotation UI, durable encrypted storage, reboot recovery, group protocol, file transfer or media relay. Those remain later milestones.

See [physical-device guide](PHASE3_TESTING.md). Phase 2 documents are historical; this document supersedes their statements about missing relaying/encryption, four-kilobyte text limits, service ID and discovery reset behavior.
