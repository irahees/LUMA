# LUMA — Android starter

A monochrome, iPhone-inspired Liquid Glass music-app UI starter built with Kotlin and Jetpack Compose.

## Current status
- Working Compose UI scaffold: Home, Search, Library, mini-player and navigation.
- Monochrome silver/pearl glass styling; no blue accents.
- No advertising SDKs or in-app ad placements.
- Spotify authentication and real playback are **not connected yet**. The Connect button is a placeholder, not a real login.

## Build APK from a phone
1. Create/sign in to a GitHub account.
2. Create a repository named `LUMA`.
3. Upload the contents of this folder to the repository (not the ZIP itself).
4. Open the repository's **Actions** tab and enable Actions if prompted.
5. Choose **Build LUMA APK** and tap **Run workflow** (or push to `main`).
6. Open the completed workflow run, then download the `LUMA-debug-apk` artifact.
7. Extract the downloaded artifact ZIP and install `app-debug.apk` on your Android phone. Android may ask you to allow installs from your browser/files app.

## Requirements
- Android SDK / Gradle cloud runner (provided by GitHub Actions workflow)
- Java 17
- For local builds, Android Studio and Android SDK are recommended.

## Spotify integration next
Use Spotify's current official Android authorization guidance and App Remote SDK documentation. Keep client secrets out of the APK, request only necessary scopes, and test against Spotify's current developer-mode eligibility and account limits. Spotify Free does not become ad-free through this app; the app itself contains no third-party ads, but Spotify's own playback/ad rules still apply.

Official docs:
- https://developer.spotify.com/documentation/android
- https://developer.spotify.com/documentation/android/tutorials/authorization
- https://developer.spotify.com/policy
