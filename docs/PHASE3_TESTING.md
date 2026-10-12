# Phase 3 owner validation — four or five phones

Install the same **0.3.0 APK** on all phones. Google Play services is required. Keep apps visible, Wi-Fi/Bluetooth on and grant wireless permissions. Keep phone clocks close; disable mobile data/internet connectivity while leaving radio hardware enabled.

1. First repeat direct A ↔ B messaging with this APK. Confirm rejection, permission-denial recovery and numeric-code verification still work.
2. Start Mesh on A, B, C and D. Enable "Relay nearby traffic" on intermediate phones.
3. Establish and verify **only** A–B, B–C and C–D. Do not accept A–C/A–D/B–D links. Discovery can see other phones without connecting them; discovery visibility does not count as a mesh edge. If discovery expires, tap Resume discovery to keep existing links.
4. Wait around 20 seconds for signed presence advertisements. A should list B as Direct, C as 2 hops and D as 3 hops. Diagnostics should confirm the actual direct endpoint set; never infer multi-hop success if A also has a direct D connection.
5. On A, open D's conversation → Verify remote fingerprint. Compare the full fingerprint with D's Profile using a trusted channel. Do the equivalent on D for A. Approve on both endpoints.
6. Send text A → D and D → A. Only endpoint devices should show the conversation text; intermediate B/C should show forwarding counts. Sender should become delivered only after the recipient ACK returns. Test Bangla and emoji. Text is capped at 3072 UTF-8 bytes.
7. Reject/skip remote fingerprint approval: relayed composer must remain disabled. Turning off a relay's switch must stop transit forwarding; it can still receive its own messages. Cached route reports can take up to their expiry to disappear after relay opt-out.
8. For recovery, add E with verified A–E and E–C links. Wait for presence so A has alternatives. Disconnect/stop B, leaving A–E–C–D intact. A's next hop should change to E and messages should continue, possibly after one retry interval. Re-establishing a physical Nearby connection still needs explicit code verification; routing recovery does not silently accept devices.
9. Partition A from all next hops. A must show no reachable remote path after link-down handling/expiry. Already pending messages may remain queued and expire after 60 seconds, never become delivered without a final ACK. Restore an already trusted path within that interval to check queue recovery. The physical links must be re-verified as needed.
10. Monitor messages/diagnostics for duplicate text under reconnect/retry, accurate hop counts, route changes, retries and rejected/duplicate counters. Do not force invalid test frames into another person's device; malformed/replay tests already run in the deterministic simulator.
11. Stop Mesh: connections, routes and discovery should clear. Restart: fresh session encryption keys are announced. Old pending ciphertext is not migrated into the new session. Process death loses history and pending messages.
12. Background/reboot behavior is intentionally not guaranteed. Discovery windows are two minutes and the full session ends after 30 minutes; keep testing in the foreground.

If a device cannot hold multiple P2P_CLUSTER connections, capture its model, Android/Google Play services versions and both endpoint diagnostics. Simulator route recovery does not prove hardware supports the desired physical topology. Actual multi-hop and two-phone wireless validation remain pending until the owner reports results.
