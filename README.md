# OrbitStrap 1.2.0

A local-first Android Roblox companion written in Kotlin and Jetpack Compose. Android 8.0+; non-root.

## Included

- Global Roblox and Roblox VN package detection and launch
- Validated JSON configuration with a raw editor and per-key add/edit/delete
- Flag search, favorites, and primitive JSON type preservation
- Presets: built-in examples, create from current config, search, favorite, apply, delete custom presets
- Local snapshots: up to 30 restore points, restore-to-editor, delete, clear, and snapshot-to-draft comparison
- Full backup/restore for JSON, presets, favorites and history
- Import/export using Android's Storage Access Framework
- JSON formatting, copying, size checks and validation
- Roblox game-link opener with scheme/host validation and last-link persistence
- Device diagnostics: Android version, device model, display refresh rate, battery, thermal status, RAM and network type
- Optional HTTPS reachability check for Roblox web endpoints (not in-game ping)
- Dark/light appearance and five accent choices
- Unit tests for the JSON editing layer
- GitHub Actions workflow runs unit tests, Android lint and a debug APK build

## Build

The included GitHub Actions workflow is the easiest build route from a phone: push the project to GitHub and open **Actions → OrbitStrap Android CI**. It installs JDK 17, Android SDK Platform 35 and Gradle 8.9, runs tests and lint, then uploads the debug APK.

For a local build, use Android Studio with JDK 17, Android SDK Platform 35, Android Gradle Plugin 8.7.3 and Gradle 8.9. Run `testDebugUnitTest`, `lintDebug`, then `assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. This source archive currently uses the GitHub Actions Gradle installation rather than bundling a Gradle wrapper, so the CI workflow is the simplest route on a phone.

## Honest behavior and Android limits

OrbitStrap stores configurations in its own private storage and lets the user import/export them. It does not silently write to Roblox's private data directory. Roblox decides whether a given FFlag is recognized or honored; sample presets are examples, not guaranteed performance profiles.

The join helper accepts `roblox://` and official `roblox.com` links, then delegates them to the installed Global Roblox or Roblox VN app. Links can expire or require access.

Device metrics are informational snapshots. The HTTPS check measures web-service response time only; it is not a measurement of in-game server ping. A separate ordinary Android app cannot reliably read another app's private process metrics, adjust its CPU/GPU governor, control Roblox update channels, or inject an in-game overlay. Mods that require root are not represented as implemented features.

This project does not bypass Roblox allowlists or anti-cheat protections, patch or inject into the client, read its private process memory, intercept gameplay traffic, or promise FPS gains.

## Inspiration and attribution

OrbitStrap independently implements common bootstrapper concepts such as configuration management, presets, diagnostics and game-link launching. It does not copy Voidstrap source files or assets. The current Voidstrap Android project documents helper methods using Shizuku, ADB or root for FastFlag access and states that mods require root; these privileged operations are intentionally not claimed as available in this non-root build. See https://github.com/KloBraticc/Voidstrap.

## Validation status

The source has static checks, a Kotlin serialization compiler plugin, unit tests and a CI build workflow. This runtime does not include Android SDK Platform 35 or a Gradle executable, so a local APK build could not be run here. The first authoritative compile result will be the included GitHub Actions build or an Android Studio build. Do not call the package error-free until that build passes and it has been installed and exercised on a physical device.


## Commit from Android

See [COMMIT_TO_GITHUB.md](COMMIT_TO_GITHUB.md) for the exact upload and Actions steps. Upload the contents of this folder to the repository root, not the ZIP as one file.
## App icon

The OrbitStrap launcher icon is included at `app/src/main/res/drawable-nodpi/ic_orbitstrap.png`. The original branding image is also available at `assets/branding/orbitstrap-logo.png`.
