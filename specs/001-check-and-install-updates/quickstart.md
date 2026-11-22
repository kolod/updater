# Quickstart: Validate Check and Install Updates

## Prerequisites

- JDK and Android SDK as for the sibling apps (Gradle wrapper in the repo); an Android device or
  emulator with API 26+.
- For the end-to-end run: at least one managed app installed at an older version, e.g. the
  Circuitopedia `v0.1.0` APK from its GitHub release.

## 1. Automated checks (no device, no network)

```bash
./gradlew test lint            # :core fixtures, :app ViewModel fakes, locale coverage, Lint
./gradlew :app:assembleDebug
```

Expected: all green. Covers FR-005, 007, 011, 017, 019 and SC-003, 005, 007, 008, per
[contracts/core-api.md](contracts/core-api.md) and [contracts/network-policy.md](contracts/network-policy.md).

## 2. Policy gates (also a CI step in `test.yml`)

```bash
! grep -rEn "WorkManager|AlarmManager|JobScheduler|BOOT_COMPLETED|startForegroundService|QUERY_ALL_PACKAGES|UPDATE_PACKAGES_WITHOUT_USER_ACTION" app/src core/src
```

Expected: no matches (Principles I–III, SC-004). Manifest permissions are exactly `INTERNET` and
`REQUEST_INSTALL_PACKAGES`.

## 3. Manual scenarios on a device

| # | Do | Expect |
|---|---|---|
| 1 | Install the updater debug build; open it | Five rows resolve within 10 s with correct versions and statuses (US1, SC-001) |
| 2 | Airplane mode, tap check | Every row "check failed – offline"; earlier versions visible, marked not fresh (US1.4) |
| 3 | Tap check during a check; rotate the screen | No duplicate check; state preserved |
| 4 | Install Circuitopedia `v0.1.0`, open the updater | Row "update available 0.1.0 → 0.2.0" |
| 5 | Tap Update | Progress, system confirmation, then row "up to date"; `cacheDir/downloads` is empty (US2, SC-006) |
| 6 | Tap Update, decline in the system dialog | Row back to "update available", no error, file deleted (US2.3) |
| 7 | Revoke "install unknown apps" for the updater, tap Update | Guided to the setting; after granting, the flow continues (US2.5) |
| 8 | "Update all" with two outdated apps | One system dialog per app, sequential; the updater last if outdated (US2.6, US4) |
| 9 | Look at the Hentai Reader row | Android release `0.1.0`, never `windows-v0.4.0` (edge case) |
| 10 | Tampered asset (debug test hook with a wrong checksum) | Install refused, file deleted, message shown (US3.1) |
| 11 | Leave the app untouched for 24 h with a network monitor | Zero requests, zero notifications (SC-004) |
| 12 | Switch the system language through all 6 languages | All strings translated; Ukrainian plurals correct (SC-007) |

## 4. Release path

Tag `vX.Y.Z` on `main`; `release.yml` builds `updater.apk` and `updater.apk.sha256`. Install the
previous release, publish a newer tag, open the updater: its own row offers the update (US4).
