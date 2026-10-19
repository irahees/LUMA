# LUMA Music

A clean, Liquid Glass-inspired Android music app built with Kotlin and Jetpack Compose.

## What LUMA does

- Browse and play audio files stored on your device.
- Save local tracks to Favourites.
- Continue local playback while the app is in the background.
- Connect a Spotify account using OAuth 2.0 Authorization Code with PKCE.
- Browse Spotify playlist and Liked Songs metadata where the account and Spotify API access allow it.
- Open Spotify tracks and playlists in the official Spotify app for playback.
- Discover separate Creative Commons BY / BY-SA audio from Internet Archive.
- No in-app ads or analytics SDKs.

> **Playback note:** LUMA does not extract or stream Spotify audio. Spotify tracks open in Spotify and follow Spotify's own account, playback, and advertising rules. LUMA's ad-free interface does not remove ads from Spotify Free or unlock Premium features.

## Spotify connection setup

Each person connects their own Spotify account from inside LUMA. The Spotify Client ID identifies the developer app; it is not a user's password.

1. Create a Spotify developer app in the Spotify Developer Dashboard.
2. Add this redirect URI to the app's Redirect URIs list exactly as shown:
   `http://127.0.0.1:43825/callback`
3. Copy the app's **Client ID**.
4. Open LUMA, choose Spotify, paste the Client ID, and select **Connect Spotify**.
5. Sign in on Spotify's own page and review the requested permissions.

LUMA uses PKCE and does not need a Spotify Client Secret embedded in the APK. **Never commit a Client Secret, access token, refresh token, password, or personal account data.** The Client ID is a public identifier, not a secret.

### Spotify API access limits

Spotify controls API access and eligibility. A developer app in Development Mode may have account and authorized-user limits, and Spotify may change its requirements. A public GitHub repository does **not** automatically give every downloader access to the Spotify API. For a wider public release, check Spotify's current Development Mode and quota-extension requirements and apply for the appropriate access if eligible. Do not promise that every user can connect until Spotify has approved the app for the intended audience.

Spotify may also change API endpoints, scopes, and policies. If connecting or loading library data fails, check the Spotify Developer Dashboard and current Spotify Web API documentation.

## Download a debug APK

- **Latest successful build:** open the [GitHub Actions builds](https://github.com/irahees/LUMA/actions), select a successful **Android build** run, and download the `LUMA-debug-apk` artifact.
- Build artifacts are temporary and currently expire after 14 days.
- To create a versioned APK release, push a tag such as `v1.0.1`. The release workflow builds a debug APK and attaches it to the GitHub Release.

The APK produced by these workflows is a **debug build for testing**, not a signed production release for Play Store distribution. Do not install APKs from untrusted sources.

## Build locally

Requirements: Android Studio, JDK 17, and Android SDK platform 35.

1. Clone this repository.
2. Open the project in Android Studio and allow Gradle sync to finish.
3. Run the app on an Android device or emulator, or build from a terminal with Gradle installed:

   ```bash
   gradle --no-daemon assembleDebug
   ```

4. Find the APK at `app/build/outputs/apk/debug/app-debug.apk`.

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Java 17, and Compose BOM 2024.12.01.

## Privacy and limitations

- Spotify access tokens are held in app memory; reconnect after restarting LUMA.
- Spotify playlist and liked-track entries are metadata and links; Spotify audio plays in Spotify.
- Local music playback uses files available on the device.
- No analytics SDK or in-app ad SDK is included in the current project.
- Review the app's permissions and source before installing or contributing.

## Contributing

Issues and pull requests are welcome. Please do not include API secrets, tokens, passwords, or private user data in issues, screenshots, logs, or commits.
