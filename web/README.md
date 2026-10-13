# Wibrio Web · Phase 2 text transport

Responsive Next.js App Router interface alongside the existing Android project. Demo screens stay local. `/connect` offers a real, manually paired WebRTC text channel. There is no backend or real mesh routing.

The default workspace is **Live mode**. Dashboard counters, connected people, conversation transcripts, and direct connection details subscribe to the actual WebRTC provider. An unpaired browser shows zero peers and empty states. Real group transport, voice/video, and file transfers show unsupported states. Sample profiles, seeded chats, illustrative topology, and previews are available only after explicitly choosing **Demo mode** in the header. This opt-in is stored per tab in sessionStorage and survives reload; existing provider preferences do not silently enable samples. Returning to Live mode never converts sample chats into real peers. A real session can stay open while exploring demos and is shown in a separate banner.

## Run

Requires Node.js 22 or later.

```sh
cd web
npm ci
npm run dev
```

Open http://localhost:3000. For offline page caching, use a production build:

```sh
npm run build
npm run start
```

## Verify

```sh
npm run lint
npm run typecheck
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

## Architecture

- `src/app`: App Router layouts, screens, chat routes, loading/error/not-found boundaries.
- `src/components`: shared shell, responsive screens, topology, chat, shadcn-compatible button primitive.
- `src/lib/network.ts`: capability contracts and DemoNetworkProvider, WebRtcNetworkProvider, NativeCompanionProvider.
- `src/lib/webrtc.ts`: one ephemeral, bounded WebRTC data-channel session, manual ICE exchange, certificate comparison, peer confirmations, receipt acknowledgements, and teardown.
- `src/lib/db.ts`: Dexie chat repository. Messages are scoped by conversation and labeled `local-demo`.
- `src/lib/store.ts`: Zustand preferences, hydrated after mount; session state is intentionally ephemeral.
- `public/sw.js`: production same-origin page and static asset cache; excludes Next RSC requests. Only previously visited pages work offline. New routes need an online visit; cached fallback explains this limitation.

Light/dark themes, accessible navigation, mobile bottom tabs, searchable contacts/inbox, private/group demo chats, provider setup, file selection/download/removal, call previews, topology, profile, and settings are included. Files are session-only, not transmitted or persisted. Calls never access media devices. Sample chats and topology do not represent real peers or routes. Local storage is not encrypted. Browser storage clearing removes demo chats and identity; profile device identifiers are demo UUIDs, not cryptographic keys.

Figma connector access to the supplied file was denied. Visual direction follows the supplied local design thumbnail and the authorized navy/blue/cyan fallback branding; this is not a pixel-exact Figma implementation. Existing Android files and local `.fig` references are preserved.

## Real text chat: manual pairing

1. Open `/connect` on both browsers using localhost or HTTPS. A plain `http://192.168...` LAN origin is not a secure context; use a trusted HTTPS host for another physical device. No camera or microphone permissions are requested.
2. Browser A chooses **Create offer** and privately sends its full pairing JSON to B through a trusted channel.
3. Browser B chooses **Join invitation**, pastes the offer, and selects **Accept offer & create answer**. B privately sends its answer JSON back to A.
4. A pastes the answer and selects **Apply answer**. Wait for the actual data channel to open on both sides.
5. Compare the entire verification code by voice or in person, then confirm on both browsers. Sending remains disabled until both have confirmed.
6. Exchange real text. **Acknowledged by peer app** means the receiving app accepted the message into memory; it does not mean read or durably stored. Missing acknowledgement after ten seconds is **unconfirmed**; a later genuine ACK may update it. Nothing is automatically resent.
7. Disconnect to end the session. The other browser may report disconnected or failed according to its actual transport events. Start a fresh pairing to retry. In-app navigation keeps the session; reload or a new pairing discards its transcript and requires fresh verification.

WebRTC uses DTLS for the data channel. The displayed 96-bit comparison value hashes the sorted SHA-256 certificate fingerprints from both session descriptions. This is a manual session check, not account authentication, a persistent identity, or independently audited security. Pairing accepts only a single data media section and single SHA-256 fingerprint to avoid ambiguous certificate selection.

There are no configured ICE servers: host-only candidates work best on the same LAN; cross-network connectivity is not guaranteed. Browser mDNS behavior, local network permissions, firewalls, AP isolation, or VPNs can block connection even on the same LAN. Pairing expires after five minutes; ICE gathering and connection have explicit timeouts. Retry uses fresh codes, not old routes or stored keys. Codes include network metadata and are not persisted or logged by the app. Sessions are limited to 500 messages, 4,000 characters per message, bounded input packets and buffered output. Real text is not written to IndexedDB, mixed into fictional conversations, or passed to the Android engine.

Native companion, real group transport, file transfers, voice/video, signaling infrastructure, STUN/TURN, and multi-hop mesh routing remain future work. Browser tests use actual RTCPeerConnection data channels between two pages; physical-device and cross-network validation are still required.

Dependency audit: production dependencies report zero vulnerabilities at implementation time. npm reports five high-severity entries in the development-only Next ESLint → fast-glob → micromatch → braces chain. The current braces release has no patched version; npm's suggested automatic fix would downgrade Next's lint configuration incompatibly. Recheck when a patched lint dependency is released.
