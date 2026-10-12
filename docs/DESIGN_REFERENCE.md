# Local Figma design implementation

Source: `Wibrio — Premium Mesh Communication UI.fig`, exported 2026-10-12. Archive opens successfully and contains `canvas.fig`, `meta.json` and a 385 × 400 thumbnail showing eight screens. The editable binary canvas has not been decoded or opened in Figma. Connector reads of both shared files were denied. The owner explicitly requested implementation from the available local preview.

The thumbnail is documentation only, never an application asset. The Compose implementation approximates its visible layout; exact typography, measurements and unreadable text cannot be recovered from this low-resolution reference. Original supplied logo is retained in onboarding/profile.

## Implemented design structure

- Start Mesh: welcome heading, pale mint illustration panel, blue/teal decorative dots, selected transport rows, blue primary action.
- Dashboard: navy mesh status card, compact three-column metrics, transport status, horizontal quick-action tiles, recent conversations.
- People and Messages: search field, filter chips, roster/inbox region. Data remains empty until real discovery/messaging is available.
- Conversation: route banner, message area, bottom attachment/composer region. No fabricated messages or send action.
- Call: dark navy full-screen layout, teal avatar treatment, disabled media controls, prominent red close button. No active call or elapsed timer.
- Files: blue-tinted send panel, two-column category tiles, recent transfers. Category taps clearly explain availability; no file is read or sent.
- Network map: central blue local node and connection summary. Sample remote nodes/links are omitted because no topology is confirmed.
- Bottom navigation follows Home / People / Messages / Files / Map. Settings remains available through the toolbar.

Modular screen functions live in `DesignScreens.kt`; shared components in `Components.kt`; navigation and theme in `MainActivity.kt`. Search/filter and connection preference selection are local UI interactions, not claims of functioning wireless transport.

## Verification

Compile with `./gradlew.bat :app:assembleDebug --console=plain`. Device/UI rendering remains for the owner's manual testing as requested. No pixel-perfect or runtime visual-validation claim is made. Reconcile exact details when full-size exports or editable Figma access become available.
