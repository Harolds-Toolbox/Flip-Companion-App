# PABI Flip Phone Companion

Answer or end calls in supported apps by opening or closing your Samsung zFlip phone.

**[Download the Android installer (v0.24)](https://github.com/Harolds-Toolbox/Flip-Phone-Companion/releases/download/v0.24/PABI-Flip-Companion.apk)** · [Phone-friendly install guide](https://harolds-toolbox.github.io/Flip-Phone-Companion/)

The installer is a signed Android APK. Download it **on the phone**, open the downloaded file, and confirm Android's installation prompt. Android may ask you to allow your browser or Files app to install apps. This project is distributed directly while the app is in development.

**Development diagnostics build:** [Download the separate diagnostics APK (v0.24-diagnostics.1)](https://github.com/Harolds-Toolbox/Flip-Phone-Companion/releases/download/v0.24-diagnostics.1/app-diagnostics.apk). This build installs as **PABI Flip Companion Dev** alongside the public app. The diagnostics card sits below Buy me a Coffee. In the Dev app, turn on **Send diagnostics** to share structured call events with PABI. Sharing starts off. To stop sharing, turn the switch off; **Delete uploaded diagnostics** removes this installation's records from the server. Records are otherwise deleted after 30 days. Caller names, message text, notification text, phone numbers, and audio are not collected. The development APK is signed with a development key, so future builds signed with a different key may require reinstalling it.

For a clean call test, give notification access to **only one** companion app at a time. The public and Dev apps have separate settings and permissions.

In both installers, if a calling app does not identify a call as audio or video, the **audio** switches apply. An explicitly identified video call still uses the video switches. Call action labels and video detection recognize Android CallStyle translations across supported system languages. Third-party apps can use their own wording; the Dev build records the device language and whether an action was absent or unrecognized so we can improve coverage without collecting notification text.

## Set up

1. Install and open **PABI Flip Companion**.
2. Choose the calling app you want to configure and tap **Enable permissions**.
3. Enable notification access for PABI Flip Companion, then return to the app and tap **Check permissions**.
4. Turn on **Answer on open** and/or **Hang up on close**. WhatsApp, Snapchat, Instagram, Facebook Messenger, and Google Meet have separate audio and video controls. Discord has one pair of controls for both call types.

Instagram, Snapchat, and Discord also need **Accessibility access** for Answer on open. Their settings screens include an **Enable answer access** button. A phone with a secure screen lock may still require its passcode before Discord can join a call.

On some Android phones, notification access is a **restricted setting**. To enable access, open **Settings → Apps → PABI Flip Companion → ⋮ → Allow restricted settings**, then return to notification access. [Google's instructions](https://support.google.com/android/answer/12623953).

## Compatibility

- Android 11 or newer (`minSdk 30`).
- A Samsung Galaxy Z Flip with an Android hinge-angle sensor. The call actions were tested on a Samsung zFlip 8; other models have not yet been verified.
- Calls in standard WhatsApp, Snapchat, Instagram, Facebook Messenger, Google Meet, and Discord. WhatsApp Business is not currently supported.

The app can act only after the calling app delivers a call notification. It cannot repair delayed calls or messages. Calling apps may change their notification actions or on-screen controls, which could require an update. Discord does not identify incoming voice and video calls separately to the app, so its controls apply to both.

## Privacy and permissions

Android's notification access permission allows a listener to see notifications broadly. This app's listener filters for supported call notifications and uses their Answer or End action where available. Accessibility is needed to press the in-app Answer control for Instagram, Snapchat, and Discord; it can also be used as a hang-up fallback. The **public release APK** does not have the Android Internet permission or send data to a server. It keeps call state, settings, and limited diagnostics on the device. Raw notification text is not stored in the release build. The separate development diagnostics APK has Internet permission, and sends only structured call events, device model, Android version, and device language when the user enables sharing. It does not send notification or caller text.

PABI Flip Companion is not affiliated with the supported calling services.

## For developers

The native Android project is in `app/`, with Gradle wrapper files at the repository root. The launcher and in-app header use the same `app/src/main/res/drawable-nodpi/pabi_icon.png`. Separate design assets are in `design/`.

The optional diagnostics variant can be built with `./gradlew :app:assembleDiagnostics -PpabiDiagnosticsEndpoint=https://pabi-flip-diagnostics.pabiautomations.workers.dev/v1/events`. The intake Worker and D1 schema are in `diagnostics-worker/`. It is deployed to the `harry@pabiautomations.co.uk` Cloudflare account, with a daily retention trigger. The Worker has no public read endpoint; PABI can inspect events in that account's D1 dashboard.

Install JDK 17 and Android SDK Platform/Build Tools 36. Build a debug APK with:

```sh
./gradlew :app:assembleDebug
```

Distribution builds must be signed with the **same private release key** for every update. The key and passwords are intentionally absent from GitHub. Set `PABI_FLIP_KEYSTORE`, `PABI_FLIP_STORE_PASSWORD`, `PABI_FLIP_KEY_ALIAS`, and `PABI_FLIP_KEY_PASSWORD` in your environment, then run:

```sh
scripts/build-distribution.sh
```

The signed APK is `app/build/outputs/apk/release/app-release.apk`. The current release certificate SHA-256 fingerprint is `71:F5:A1:94:70:CD:5D:D6:33:0C:7A:8A:F5:45:40:96:04:B2:1B:4D:95:16:5C:DD:34:BE:B6:C2:BA:18:DB:F8`.

For a later Play Store launch, provide this existing release key as the **app-signing key** when configuring Play App Signing if you want APK-installed users to update through Play without reinstalling. Use a separate upload key for Play. Preserve a secure backup of the private release key and password; neither belongs in this repository. [Android's signing guidance](https://developer.android.com/studio/publish/app-signing).

An existing development build signed with a different debug key cannot be updated in place by the public release APK. Remove that build first, then install the release APK; this resets the app's local settings and notification access. New users who install the public release can update normally with later APKs signed using the same release key.
