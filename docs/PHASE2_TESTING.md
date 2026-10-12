# Phase 2 — two physical phones

Install the same 0.2.0 debug APK on two Android 8+ devices with working Google Play services. Android Studio is not needed to run this manual test. Internet is not required for the direct session; installing/updating Google Play services may require it beforehand.

1. Set distinct display names on A and B. Keep both apps visible and phones nearby.
2. Enable Bluetooth and Wi-Fi. On Android 12 and older, enable system Location for discovery. Grant Wibrio's requested wireless permissions on both phones.
3. Open Home → Manage network → Start Mesh Network on both phones.
4. Open People on A. Tap Connect next to B. Start from only one phone to avoid simultaneous request races.
5. Compare the displayed numeric code on both phones. Only accept when both match. Accept on **both** phones. Any Google Play services consent/picker also needs user approval.
6. Both should show a verified direct peer. Open it on A, send `hello`, verify B receives it. A should change from sending to delivered only after B's signed ACK arrives.
7. Reply from B. Test Bangla, emoji and a short multiline message. Maximum text size is 4096 UTF-8 bytes; extremely escaped/control-heavy input can exceed the packet limit and fail visibly.
8. Open diagnostics: verified peers, packet counts and ACK counts should increase. Map should show only the confirmed direct peer; no relay nodes.
9. Disconnect from the conversation or stop the network on B. A must lose connected state and disable sending. Pending messages fail after disconnect or after a 20-second ACK timeout. A timeout means delivery is unconfirmed, even if B received the text.
10. Reconnect from People and compare the new code again. If the discovery window expired or the device is absent, stop/start both sessions. Reconnection is user-initiated; there is no silent auto-accept or automatic retransmission.
11. Reject a connection on one phone: neither side should become a verified messaging peer. Leave a verification request unanswered: it expires after 45 seconds.
12. Deny a required permission, turn Bluetooth/Wi-Fi off, or remove/update Play services: app must display the actual startup/connection failure. Grant permissions through Settings → Permissions, then retry.
13. Disable mobile data and disconnect Wi-Fi from an internet router while keeping the Wi-Fi radio on; repeat bidirectional messages. Confirm they work without internet.
14. Rotate a device while connected: ViewModel/session should survive activity configuration recreation. Leave/reopen the app and force-stop it: do not expect reliable background connectivity. Restart networking after process death.
15. Profile fingerprint should survive ordinary process restarts. Clearing app data/uninstalling creates a new identity. History is in process memory only and disappears after process death; do not use this version for important messages.

Discovery/advertising stop after two minutes to bound battery usage; established direct links remain until stopped, disconnected or killed by Android. Starting a new discovery session after that window resets existing links. At most 8 sessions, 64 discovered entries, 128 pinned identities, 500 message rows and 1024 message dedup IDs are retained. No files/calls/groups/multi-hop forwarding exist yet.

If testing fails, report both device models, Android versions, the step, the error text and each phone's diagnostics screenshot. The developer has compiled the app and run JVM tests; **actual wireless behavior has not been validated on physical devices yet**.
