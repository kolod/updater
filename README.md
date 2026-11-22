# Updater

Free, ad-free, open-source Android app that checks GitHub Releases for new versions of
[Ghost BMS (unofficial)](https://github.com/kolod/ghost-bms-unofficial),
[Whisker Grid](https://github.com/kolod/whiskergrid),
[Hentai Reader](https://github.com/kolod/hentai-reader),
[Circuitopedia](https://github.com/kolod/circuitopedia) and itself, and installs them.

## What it does

- Lists the managed apps with their installed and latest versions. Hentai Reader is optional: it appears,
  and is checked, only while it is installed on the device.
- Checks for updates **only** when you open the app or press **Check for updates**. There is no
  schedule, no background work and no notification.
- Downloads an update, verifies it, and hands it to the system installer. You always confirm in the
  standard Android dialog.
- Updates itself the same way (installed last when you use **Update all**).

## Privacy and safety

- Network access is limited to GitHub (`api.github.com`, `github.com` and GitHub's release-asset
  hosts), over HTTPS, anonymously. Nothing about you, your device or your installed apps is sent.
- No ads, analytics or tracking. Permissions: `INTERNET` and `REQUEST_INSTALL_PACKAGES` only.
- Before an APK is installed it must pass: SHA-256 (when the release publishes one), package name,
  version (newer than installed) and a signing-key comparison. Android's own signature check is the
  final authority; an update signed with a different key is refused, never worked around.
- Downloaded files are kept in private storage and deleted after every attempt.

## Languages

English, German, French, Spanish, Japanese and Ukrainian.

## Building

```bash
./gradlew test lint :app:assembleDebug
```

The project has two modules: `core` (pure Kotlin/JVM: catalog, release parsing, version logic,
verification, network policy) and `app` (Android: HTTP, package manager, installer, Compose UI).

## Releasing (maintainer)

One-time setup in the GitHub repository:

1. Create a signing keystore and keep it safe. **The updater can only update itself if every
   release is signed with the same key.**
2. Create an environment named `release` (require approval) and add the secrets
   `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
   `ANDROID_KEY_PASSWORD`.

Then merge to `main` through a pull request and push a tag `vX.Y.Z`. The workflow builds
`updater.apk` and `updater.apk.sha256` and publishes the release.

## Project documents

- [Constitution](.specify/memory/constitution.md)
- [Specification and plan](specs/001-check-and-install-updates/)

## License

GPL-3.0.
