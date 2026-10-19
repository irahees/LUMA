# 👺 LUMA Music

**Your music. One clean glass interface.**

LUMA is an Android music app built with Kotlin, Jetpack Compose, and a Liquid Glass-inspired design. It brings local music, Spotify library browsing, favourites, and separately sourced free-to-listen audio into one interface.

## Features

- **Local music** — browse and play audio files on your device.
- **Background playback** — keep local tracks playing when you leave the app.
- **Favourites** — keep your favourite local tracks close.
- **Spotify library** — connect through Spotify OAuth with PKCE and browse playlists and Liked Songs when Spotify API access permits.
- **Free music discovery** — find separate Creative Commons BY / BY-SA audio from Internet Archive.
- **Clean interface** — translucent glass-inspired panels and a focused music-first layout.
- **No in-app ads or analytics SDKs** in the current project.

## Spotify: important details

LUMA uses Spotify for **library metadata and links**. Spotify tracks open in the official Spotify app for playback; LUMA does not download, extract, or stream Spotify audio, and it cannot remove ads or unlock Premium features on Spotify Free.

To connect Spotify, the app needs a Spotify Developer app **Client ID** configured in the app's current connection screen:

1. Create an app in the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. Add this redirect URI exactly: `http://127.0.0.1:43825/callback`
3. Copy the Client ID, then enter it in LUMA's Spotify screen.
4. Sign in on Spotify's official authorization page and approve the requested permissions.

LUMA uses PKCE and does not need a Spotify Client Secret embedded in the APK. Never commit passwords, Client Secrets, access tokens, refresh tokens, or personal account data.

Spotify controls API eligibility and access limits. Development Mode may restrict the developer account and authorized users, and requirements can change. A public APK or repository does not automatically grant every downloader Spotify API access. Check Spotify's current dashboard and policies before distributing the app widely.

## Download the APK

Open [LUMA Android build runs](https://github.com/irahees/LUMA/actions) and choose a successful run. Download the **LUMA-debug-apk** artifact. The artifact contains the APK only; GitHub packages downloadable workflow artifacts as ZIP files. Artifacts expire after 14 days.

The workflow builds `app/build/outputs/apk/debug/app-debug.apk` and uploads that file only. This is a **debug/testing APK**, not a production-signed Play Store release. Install only builds you trust.

## Build locally

Requirements: JDK 17, Android SDK platform 35, and Gradle 8.9.

```bash
git clone https://github.com/irahees/LUMA.git
cd LUMA
gradle --no-daemon assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

Technology: Kotlin, Jetpack Compose, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Java 17, Compose BOM 2024.12.01.

## Privacy and limitations

- Spotify tokens are held in app memory; reconnect after restarting LUMA.
- Spotify playlist and liked-track entries are metadata and links; audio playback is handled by Spotify.
- Local playback uses audio files available on the device.
- The current project does not include an analytics SDK or in-app advertising SDK.
- Review permissions and source code before installing or contributing.

## Contributing

Issues and pull requests are welcome. Do not post API secrets, tokens, passwords, or private user data in issues, screenshots, logs, or commits.
