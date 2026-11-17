# PABI Flip Companion

Answer or end WhatsApp calls by opening or closing a compatible Samsung flip phone.

**[Download the Android installer](https://github.com/Harolds-Toolbox/Flip-Companion-App/releases/latest/download/PABI-Flip-Companion.apk)** · [Phone-friendly install guide](https://harolds-toolbox.github.io/Flip-Companion-App/)

The installer is a signed Android APK. Download it **on the phone**, open the downloaded file, and confirm Android's installation prompt. Android may ask you to allow your browser or Files app to install apps. This project is distributed directly while a Play Store listing is being prepared.

## Set up

1. Install and open **PABI Flip Companion**.
2. Open **WhatsApp Calls** and tap **Enable permissions**.
3. Enable notification access for PABI Flip Companion, then return to the app and tap **Check permissions**.
4. Choose **Hang up on close** and/or **Answer on open** for audio and video calls.

If Android says notification access is a **restricted setting**, go to **Settings → Apps → PABI Flip Companion → ⋮ → Allow restricted settings**, then return to notification access. Android only offers this control after you choose to trust the app. [Google's instructions](https://support.google.com/android/answer/12623953).

## Compatibility

- Android 11 or newer (`minSdk 30`).
- A Samsung Galaxy Z Flip with an Android hinge-angle sensor. The call actions were tested on a Samsung SM-F776B; other models have not yet been verified.
- Standard WhatsApp (`com.whatsapp`). WhatsApp Business is not currently supported.

The app can act only after WhatsApp delivers a call notification. It cannot repair delayed WhatsApp calls or messages. WhatsApp may change its notification action labels, which could require an app update.

## Privacy and permissions

Android's notification access permission allows a listener to see notifications broadly. This app's listener filters for WhatsApp call notifications and uses their Answer or End action. It does not have the Android Internet permission or send data to a server. It keeps call state, settings, and limited diagnostics on the device. Raw WhatsApp notification text is not stored in the release build. Accessibility is an optional hang-up fallback and is not required when WhatsApp supplies an End action in its notification.

PABI Flip Companion is not affiliated with WhatsApp.

## For developers

The native Android project is in `app/`, with Gradle wrapper files at the repository root. The launcher and in-app header use the same `app/src/main/res/drawable-nodpi/pabi_icon.png`. Separate design assets are in `design/`.

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
