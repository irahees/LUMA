# 👺 LUMA Music

**A music space with an original Liquid Glass-inspired interface.**

LUMA is an Android music app built with Kotlin and Jetpack Compose. It brings local music, favourites, Spotify library metadata, and separately sourced free-to-listen audio into one soft, glassy interface.

## Features in this build

- Local audio library and search.
- Background playback for local audio.
- Favourites and a mini-player / now-playing screen.
- Spotify playlist and Liked Songs metadata through Spotify's official OAuth/API where app access allows.
- Open Spotify tracks and playlists in Spotify for playback.
- Creative Commons BY / BY-SA audio discovery from Internet Archive.
- Translucent glass panels, ambient colour blooms, and rounded floating controls.
- No in-app advertising or analytics SDK is included in the current project.

## Spotify sign-in

LUMA no longer asks each person to paste a Client ID. The app can be built with the maintainer's Spotify OAuth Client ID and the user signs in on Spotify's official authorization page. LUMA never asks for or stores a Spotify password.

### One-time setup for the app maintainer

Spotify's official OAuth flow requires an app Client ID. Set the repository's **Actions secret** named `SPOTIFY_CLIENT_ID` to the Client ID belonging to the LUMA app. The Android build workflow injects it at build time; it is not entered by each user. A Client Secret must not be embedded in this Android APK.

If this secret is not configured, the sign-in button can still open Spotify's login page, but that web login by itself does not authorize LUMA to read playlists or Liked Songs. Configure the Client ID once to enable official OAuth library sync in the build.

In the Spotify Developer Dashboard, register this redirect URI exactly:

`http://127.0.0.1:43825/callback`

Spotify controls API eligibility and usage limits. As of the current Development Mode rules, the app owner must meet Spotify's Premium requirement and Development Mode apps are limited to a small number of authorized users. That makes Development Mode unsuitable for unlimited public distribution. End users should use accounts they are authorized to access, and LUMA does not remove Spotify Free advertisements or Premium restrictions.

### Spotify audio playback

LUMA uses Spotify for account-authorized library metadata and links. It does not extract Spotify session cookies, generate private session tokens, or stream/download Spotify audio. Spotify track playback opens in Spotify. Free audio in LUMA is a separate Creative Commons source.

## Download the APK

Open [LUMA Android build runs](https://github.com/irahees/LUMA/actions), choose a successful run, and download the **LUMA-debug-apk** artifact. The build job uploads only `app/build/outputs/apk/debug/app-debug.apk`; GitHub delivers workflow artifacts as a ZIP containing that file. Artifacts expire after 14 days.

This is a debug/testing APK, not a production-signed Play Store release.

## Build locally

Requirements: JDK 17, Android SDK platform 35, and Gradle 8.9.

```bash
git clone https://github.com/irahees/LUMA.git
cd LUMA
gradle --no-daemon assembleDebug
```

To embed the app Client ID when building locally, set the `SPOTIFY_CLIENT_ID` environment variable or pass `-PspotifyClientId=YOUR_CLIENT_ID` to Gradle. Keep personal credentials, access tokens, refresh tokens, and any Client Secret out of source control.

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Privacy and limitations

- The current app keeps Spotify tokens in memory and may require reconnecting after restart.
- Spotify library entries are metadata and links; Spotify handles their audio playback.
- Local playback uses audio files available on the device.
- Review permissions and source before installing or contributing.

## Contributing

Issues and pull requests are welcome. Do not publish API secrets, passwords, tokens, or private user data in issues, screenshots, logs, or commits.
