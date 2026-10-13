# Live network overview and experimental direct calling — 0.5.0

The owner reported successful text messaging on devices. This update preserves the text protocol, journal and mesh transport. It does not claim physical audio/video validation.

## Live counters

Reachable is the number of unique currently reachable peer identities; Direct counts one-hop links and Relayed counts paths longer than one hop. Invalid/missing paths do not count. Verified direct sessions take precedence over duplicate presence advertisements. An unapproved remote fingerprint can be reachable but still requires approval before encrypted chat. Each dashboard tile opens its corresponding live list and conversation. A saved conversation remains after disconnect, while all live counts can correctly become zero. Relayed means reachable through another peer, not the number of messages forwarded.

## Calls

- Start Voice or Video from a conversation with a verified, currently connected direct peer. Signed HELLO advertises call protocol support; buttons are disabled for older versions and remote multi-hop peers. Both participants must install 0.5.0, stop/restart mesh and confirm matching connection codes again.
- Caller permission is requested only after tapping a call button; incoming capture requires the receiver to tap Accept and grant permission. An INVITE/ACCEPT/READY exchange precedes media. Incoming calls ring only while Wibrio is visible; background invitations receive Busy.
- Audio uses AudioRecord/AudioTrack, 8 kHz mono PCM16, up to 1,600 bytes per packet (100 ms), with device-supported acoustic echo cancellation/noise suppression and audio focus. Video captures the front camera when available, otherwise a device camera; frames are rotated/mirrored, limited to 160 pixels per edge, JPEG <=3,500 bytes, at up to 2 fps. This is an experimental low bandwidth live video baseline.
- All controls and media use the existing Google Nearby encrypted direct connection and P-256 signed link envelope, destination/session nonce checks and link replay window. Calls do not use the mesh HPKE text queue or an Internet/server fallback. No relay receives or forwards call media. The overall protocol has not had independent security review.
- Bounded media queues drop older unsent frames/speech when congested. Radio awaits happen outside the mesh state lock. Media sequences reject duplicate/out-of-order playback separately for audio/video. A call UUID and short ended-call tombstone prevent re-ringing an ended invitation within the retention window.
- Ring/setup expires after 30 seconds; setup control retries every two seconds. Active calls use heartbeats and stop after 15 seconds without valid peer traffic, on direct-link loss, media transport timeout, audio focus loss or hardware failure. Explicit End stops local capture immediately before notifying the peer. A stale media callback cannot end a different call.
- Microphone/camera capture belongs to the visible activity. Home, explicit screen lock, activity recreation/rotation and leaving the app end the call and release camera, audio devices and focus. The active call requests screen-on to prevent idle display timeout; manual locking still ends it. The existing connected-device service continues mesh ownership but is not used to capture camera/microphone in the background. No microphone/camera foreground service, OS Telecom integration, background incoming call notification, Bluetooth headset route selection, camera switching or smooth high-resolution video is claimed.

## Physical acceptance checklist

1. Install the new APK on **two physical Android devices**. Disable mobile data and Internet access while leaving Bluetooth/Wi-Fi enabled; Google Play services must remain available. Start mesh, connect, compare codes and verify a text message plus destination ACK first.
2. Inspect Home while connected: Reachable=1, Direct=1, Relayed=0. Tap each tile and check its list. Disconnect: live counts become zero, saved text remains. Reconnect and verify counts recover.
3. For relayed counts, connect A–B–C without A–C. After presence exchange, A should list B direct and C relayed. Inspect current route and approve C's fingerprint for text. Audio/video buttons for C remain disabled.
4. Start Voice A→B. Verify incoming ringing, explicit Accept, both call screens and intelligible audio in **both directions**. Use headphones or separate rooms to avoid acoustic feedback during initial testing. Test mute/unmute and speaker/earpiece; device support and quality must be measured.
5. End from either participant: microphone indicators disappear and audio stops on both. Reject a call, leave one unanswered, try simultaneous outgoing calls and retry after each. Capture must never start for an unanswered/rejected incoming call.
6. Start Video. Grant camera only at the call prompt. Verify both remote frames and local preview change with physical movement, correct orientation, incrementing received-frame count, voice in both directions, video pause/resume and hang-up. Expect visibly low frame rate; measure actual throughput, delay, drops, audio breakup and heating.
7. Deny microphone/camera; start while another app owns audio/camera; interrupt with a cellular call; revoke permission; press Home, rotate, manually lock and disconnect a peer. Verify clean stop, no lingering microphone/camera indicator, and successful subsequent reconnect/call/text.
8. While calling, send a text and observe delivery ACK. Check that media does not indefinitely block network controls or text maintenance. Stress a poor radio path; bounded queues should drop stale media or end the call rather than grow memory indefinitely.
9. Test a 0.4.1 peer: text stays compatible, call buttons remain unavailable. Test 0.5.0 on both devices after restart to confirm capability negotiation.
10. Enable/export Diagnostics on both phones before/after tests. Record phone models, Android versions, active direct links, permissions, failures, packet counters, received video frames, delay and duplex quality. Emulator results do not substitute for this checklist.

Automated tests cover the call state machine/wire bounds, consent ordering, duplex packet flow, duplicate/reordered media, wrong peer/session/mode, Busy, retries, timeouts, hang-up replay and live overview aggregation. They do not exercise physical microphones, cameras, audio routes, radio throughput or echo cancellation.

## Verification — 2026-10-13

Final source passed `:app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:test :core:mesh:check`. Debug and unsigned release APKs built successfully. App tests: 29 debug and 29 release executions; core tests: 33. Total: 62 distinct tests / 91 executions, zero failures, errors or skips. Both lint variants have zero errors and 27 warnings. No physical call or updated live dashboard acceptance test was performed here; the checklist above remains required.
