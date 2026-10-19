# LUMA Music Player

A light, Liquid Glass-inspired Android local music player built with Kotlin and Jetpack Compose.

## Features
- Scan, search and play local audio files
- Favourites saved locally across app restarts
- Foreground playback service for local music while the app is backgrounded
- No in-app ads or analytics SDKs
- Spotify OAuth 2.0 Authorization Code with PKCE
- View the current Spotify account's playlists and Liked Songs metadata
- Open Spotify tracks and playlists in Spotify for playback; LUMA does not extract or stream Spotify audio
- Search and play separate, publicly available Creative Commons BY/BY-SA audio from Internet Archive

## Spotify setup
1. Create a Spotify Developer app and copy its Client ID.
2. In the app's redirect URI allowlist, add exactly:
   `http://127.0.0.1:43825/callback`
3. Open LUMA, choose Spotify, paste the Client ID, and tap **Connect Spotify**.
4. Sign in through Spotify and approve playlist/library read access.

Spotify's current Development Mode eligibility includes a Premium requirement for the developer account, a limit of one development Client ID per developer, and up to five authorized users. Access to the API is controlled by Spotify; LUMA cannot grant API access or Premium features. Track playback remains in Spotify and follows Spotify's own account and advertising rules.

## Build
The repository includes a GitHub Actions workflow that builds a debug APK and uploads it as the `LUMA-debug-apk` artifact when a commit is pushed to `main`. Artifacts are temporary and expire after 14 days.
Android Studio can also import the project and build it locally. The app uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Java 17, and Compose BOM 2024.12.01.

## Notes
- Spotify access tokens are kept in app memory; reconnect after restarting LUMA.
- The Spotify Client ID is a public app identifier. Never put a Spotify Client Secret in the Android app.
- LUMA has no in-app advertisements, but it cannot remove ads from Spotify Free or provide Spotify Premium playback.
