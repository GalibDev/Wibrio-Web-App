# Phase 2 protocol and threat model

## Implemented protections

An installation generates a P-256 signing key in Android Keystore. Its private key is non-exportable through the API; hardware backing is device-dependent and is not claimed. SHA-256 of the encoded public key is the stable node ID. Temporary Nearby endpoint IDs are kept separate.

Unknown connections always require both users to compare Nearby authentication digits and explicitly accept. There is no automatic acceptance of a previously seen key. Display names are untrusted labels. After the verified Nearby link connects, signed HELLO frames exchange public keys and fresh random session challenges. Signed PROOF frames must echo the recipient's current challenge and the sender's challenge before a peer becomes active. Public keys are pinned locally only after this proof completes. Duplicate active identities and conflicting pins are rejected.

Wire frames are versioned JSON envelopes containing a Base64 body and SHA256withECDSA signature over the exact body bytes. Non-HELLO bodies bind sender ID, intended recipient, recipient session challenge and a per-session sequence. The 64-entry replay window permits bounded reordering and rejects reused/stale sequence numbers. Each text has a UUID. The intended recipient returns a signed ACK binding that ID and the sender's session challenge. Only that verified recipient's ACK can mark an outgoing message delivered; transport send completion never does.

Payload size (16 KiB), decoded body size (10 KB), message size (4096 UTF-8 bytes), active links, discovery duration, message history, dedup cache, deferred handshake frames and inbound packet queue are bounded. Per-peer rate limits disconnect floods. Malformed signatures/frames, wrong recipients, expired challenges or failed identity proof close the link. Discovery callbacks from previous transport generations are ignored. HELLO/PROOF/data completion reordering is handled with bounded deferred buffers.

## Explicit limitations

This is an experimental direct-link protocol using established signature primitives; it has not had an independent security review. Nearby provides its own direct connection encryption. Wibrio does **not** implement or claim independently reviewed application end-to-end encryption, forward secrecy, secure group encryption or encrypted local chat storage. No multi-hop forwarding exists.

There is no human account identity, revocation directory, QR verification, key-rotation UI or trusted-device management UI yet. Numeric comparison is only effective when users actually compare the codes with the intended person. A device whose data was cleared has a different node ID and is treated as a new device. Pinned public keys are not secret, but can reveal previously encountered node IDs to a compromised local device. Trust storage is capped at 128 identities and currently requires clearing app data to reset; that also rotates identity and deletes preferences.

Message history and duplicate tracking are volatile, bounded memory. ACKs mean accepted into the recipient process, **not durably stored/read by a human**. No persistent queue, offline retransmission, background service, reboot recovery or guaranteed background session exists. Android may terminate the app and lose history. A recipient might receive a message whose ACK is lost; the sender correctly shows an unconfirmed failure.

Google Play services is required. Nearby may collect SDK usage analytics; see [Google's overview](https://developers.google.com/nearby/connections/overview). Permissions and newer platform consent requirements are based on [Nearby setup documentation](https://developers.google.com/nearby/connections/android/get-started). The project targets SDK 36; Android 17's SDK-37 local-network permission migration must be reviewed before raising the target. Nearby 19.3.0 is pinned because 19.5.1's Kotlin 2.4 metadata is incompatible with the current Kotlin 2.2 compiler.
