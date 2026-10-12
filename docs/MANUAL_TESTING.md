# Owner's Phase 1 device checklist

1. Import and sync in Android Studio with SDK 36 and JDK 17+; install debug APK on Android 8+.
2. Fresh install: splash → welcome → name setup. Blank name cannot continue; max 40 characters. Save a name and restart: home should open and retain it.
3. Permission screen: grant, deny, deny permanently, return from app settings. Verify truthful permission state. Check Android 11, 12 and 13+ if devices are available.
4. Home/nearby/chats/map/settings tabs and every quick action: Back works, tabs do not accumulate duplicates.
5. Start Mesh in each priority mode: it must state transport is unavailable and must not show peers, discovery or active connectivity. Reset returns inactive.
6. Profile edit and dark-theme toggle persist across relaunch; rotate while editing the name.
7. Try large font, screen reader, landscape, narrow device and tablet. Check contrast, readable text, scrolling and reachable controls.
8. Conversation/calls/groups/files availability screens must not pretend to send, dial, create groups or transfer anything.
9. Diagnostics show zero traffic and no identity or routes. Network map shows only this device.
10. Force stop/reopen and verify preferences persist without suggesting a running wireless service.

No wireless messaging, encryption, group or call test can pass in Phase 1. Those require later milestones and multiple physical devices with internet disabled.
