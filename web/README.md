# Wibrio Web · Phase 1

Responsive Next.js App Router interface alongside the existing Android project. This is a local demonstration, with no backend or real mesh transport.

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
- `src/lib/db.ts`: Dexie chat repository. Messages are scoped by conversation and labeled `local-demo`.
- `src/lib/store.ts`: Zustand preferences, hydrated after mount; session state is intentionally ephemeral.
- `public/sw.js`: production same-origin page and static asset cache; excludes Next RSC requests. Only previously visited pages work offline. New routes need an online visit; cached fallback explains this limitation.

Light/dark themes, accessible navigation, mobile bottom tabs, searchable contacts/inbox, private/group demo chats, provider setup, file selection/download/removal, call previews, topology, profile, and settings are included. Files are session-only, not transmitted or persisted. Calls never access media devices. There are no delivery receipts, real peers, real routing metrics, credentials, or authentication claims. Local storage is not encrypted. Browser storage clearing removes chats and identity; device identifiers are demo UUIDs, not cryptographic keys.

Figma connector access to the supplied file was denied. Visual direction follows the supplied local design thumbnail and the authorized navy/blue/cyan fallback branding; this is not a pixel-exact Figma implementation. Existing Android files and local `.fig` references are preserved.

Future providers must implement their actual capability reporting, pairing, permission handling, transport, and delivery semantics before real connectivity can be enabled. WebRTC and native companion starts currently return explicit unsupported errors.

Dependency audit: production dependencies report zero vulnerabilities at implementation time. npm reports five high-severity entries in the development-only Next ESLint → fast-glob → micromatch → braces chain. The current braces release has no patched version; npm's suggested automatic fix would downgrade Next's lint configuration incompatibly. Recheck when a patched lint dependency is released.
