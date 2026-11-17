# PABI Flip Companion

Native Kotlin Android app for a Samsung flip phone. The package ID is `uk.co.flipwhatsapphangup`, so updates retain app settings and notification access. The app follows the device light or dark theme and uses the PABI Finance palette, onboarding header, and settings-style navigation. Its cartoon launcher icon shows a closed Z Flip8-style phone from the cover-screen side, with two cameras at the lower right, an unlabelled top hinge, and its folded layers visible against a purple background. A borderless badge over the top-left corner reads “Your flip, Your way,” with both “Your” words italicized in green and purple. The same `pabi_icon` resource appears on the home screen and in the app's header.

The final icon is `app/src/main/res/drawable-nodpi/pabi_icon.png`. The separate badge and the phone artwork before compositing are in `design/`.

The home screen shows the PABI brand header and one WhatsApp Calls card. The card's heading reads “Answer or end WhatsApp calls by opening / closing your flip” and opens the call settings screen. Permission controls remain at the bottom of call settings; the former Status, Access, and Diagnostics sections are removed from home.

## WhatsApp call handling

The WhatsApp Calls screen provides separate Audio Call and Video Call switches for **Hang up on close** and **Answer on open**. Hang up options default on; answer options default off. An incoming call is answered only after the phone moves from fully closed to fully open (at least 160°). An ongoing call ends after it moves from open to fully closed (at most 10°); if the flip closes during the short transition from answered to ongoing, the app checks the closed position when WhatsApp publishes the ongoing notification. It sends Hang up once per notification. The app reads WhatsApp call notifications and sends their Answer or Hang up action; it monitors the standard hinge-angle sensor while a configured call is present. Notification access is required. The optional accessibility fallback applies only to hang-up when a notification action is unavailable.

The four call setting rows use the handset, video, and end-call vector shapes from the installed WhatsApp Android build, with green answer and red end-call badges.

**Enable permissions** opens the app's notification access settings. **Check permissions** reports whether that access is enabled. The feature does not require Accessibility when WhatsApp supplies usable notification actions.

## Build and install

Install JDK 17 and Android SDK platform 36. Run `./gradlew :app:assembleDebug`. The APK is `app/build/outputs/apk/debug/app-debug.apk`. Use `adb install -r app-debug.apk` to update an existing installation signed with the same debug key. Version 0.17 updates the launcher and in-app header icon.

## Device checks, 16 November 2026

- Notification access alone was sufficient for Hang up on close on the SM-F776B. Voice and video calls disconnected when fully closed, including a background or locked voice call and a call after reboot. A half-folded video call remained connected.
- With Audio Answer on open enabled, an incoming voice call was detected while the flip was closed. Opening to 180° sent WhatsApp's Answer action; the user confirmed connection. Closing again sent Hang up, cleared the call notification, and the user confirmed disconnection.
- With Video Answer on open enabled, an incoming video call was detected while closed. Opening to 180° sent WhatsApp's Answer action; the user confirmed connection. Closing again sent Hang up, cleared the notification, and the user confirmed disconnection.
- After **Clear all** in recent apps, WhatsApp's process was absent. Incoming attempts stayed at Calling on the second phone and reached the flip only after WhatsApp was opened. This also occurred with PABI Flip Companion's notification access disabled. By contrast, pressing Home left WhatsApp running in the background: a call rang while the flip was closed, answered on opening, and ended on closing. The user confirmed both actions. Pinning WhatsApp to Recents preserved its process after Clear all, but the user rejected leaving WhatsApp in Recents, so the pin was removed. WhatsApp already had unrestricted battery use and background data, was absent from Samsung's sleeping and deep sleeping lists, and Play Store offered no update. The exact reason WhatsApp does not restart for its incoming push is not established.
- Version 0.8 addresses an answer-to-ongoing timing gap and avoids duplicate Hang up actions. In a live voice call after installation, the flip answered on opening and hung up on closing; the user confirmed disconnection. The app logged exactly one Hang up action.
- WhatsApp was added to **Device care → Memory → Excluded apps** on the flip. After a reboot and **Close all**, an incoming WhatsApp call rang and a WhatsApp text arrived promptly without opening WhatsApp. This is a phone setting, not a feature of PABI Flip Companion. Longer-term reliability has not yet been established.

## Limits

WhatsApp notification text and action labels can change by version or language. Calls delayed by WhatsApp itself cannot be answered by this app until Android receives an incoming-call notification. A different development machine may sign with a different debug key and require uninstalling the old build, which resets app settings.
