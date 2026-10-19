# LUMA Music Player

A monochrome, glass-inspired Android local music player built with Kotlin and Jetpack Compose.

## Features
- Dark neutral Liquid Glass-inspired interface
- Scan and play audio files on your Android device
- Search the local music library
- Favourites (kept in memory while the app is running in this starter version)
- Mini-player with play/pause and next-track controls
- No in-app advertisements or analytics SDKs
- Spotify playlist-link page: save a Spotify playlist URL during the current app session and open it in Spotify or a browser

## Build
1. Install Android Studio on a computer.
2. Open the `LUMA` folder in Android Studio.
3. Allow Gradle to sync and install the Android SDK requested by the project.
4. Run on an Android device or emulator.

This project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Java 17, and Compose BOM 2024.12.01.

## Notes
- Local playback is for audio files available on the device. Spotify playlist links open in the Spotify app or browser; LUMA does not stream Spotify's licensed catalogue itself.
- LUMA has no in-app ads, but it cannot remove ads from Spotify Free or provide Spotify Premium features.
- Spotify playlist links are currently held in memory for the current app session and are not persisted across app restarts. A full Spotify account/API integration is not yet implemented.
- Favourites are not yet persisted across app restarts in this starter build.
- Gradle wrapper scripts are not included; Android Studio can import and sync the project.
