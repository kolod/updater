# Manual run on a device (quickstart §3)

**Date**: 2026-11-22 · **Build**: debug `0.1.0-dev` · **Device**: Android emulator, API 36.1 (`Medium_Phone_API_36.1`)

The emulator already had debug-signed dev builds of Ghost BMS (`1.0`), Hentai Reader (`0.1.0-dev`) and
Circuitopedia (`0.1.0-dev`) installed. They are signed with a different key than the published releases,
which made them a realistic test for the signature check. The updater has no GitHub release yet, so its own row
correctly shows "No release available".

| # | Scenario | Result |
|---|---|---|
| 1 | Open the updater; five rows resolve | **Pass.** All rows resolved within the first seconds, with real data from GitHub (latest: Ghost BMS 1.0.4, Whisker Grid 1.1.0, Hentai Reader 0.1.0, Circuitopedia 0.2.0) |
| 2 | Airplane mode, tap check | **Not run** (covered by ViewModel tests) |
| 3 | Check during a check; rotate | **Not run** (duplicate check covered by tests) |
| 4 | Outdated app shows "update available" | **Pass.** Ghost BMS 1.0 → 1.0.4, Hentai Reader `0.1.0-dev` → 0.1.0, Circuitopedia `0.1.0-dev` → 0.2.0 |
| 5 | Update/Install, confirm, row becomes up to date, cache empty | **Pass for a fresh install of Whisker Grid** (download with progress, system installer, row "Up to date / Installed 1.1.0", `installerPackageName` = `io.github.kolod.updater`) |
| 6 | Decline in the system dialog | **Pass.** After Cancel the row returned to "Not installed + Install", no error, `cache/downloads` empty, package not installed |
| 7 | "Install unknown apps" not granted | **Pass.** Tapping Install opened the system setting; after granting and going back the queued install continued by itself |
| 8 | Update all | **Not run** (sequencing covered by tests) |
| 9 | Hentai Reader shows the Android release | **Pass.** Latest `0.1.0`, never the newer `windows-v0.4.0` |
| 10 | Tampered / wrong-key update | **Partly.** Different signing key: **Pass**, Ghost BMS update was refused before installing with the "signed with a different key... nothing was removed" message. Wrong checksum was not exercised on a device (covered by tests) |
| 11 | 24 h without opening, no traffic | **Not run.** Statically guaranteed: no scheduler APIs (CI gate), manifest has no services/receivers |
| 12 | All six languages | **Not run on a device.** `LocaleCoverageTest` and Lint (`MissingTranslation`) pass |

Also verified outside the device:

- Ghost BMS `v1.0.4` APK has `versionName='1.0.4'` (tag minus `v`), so tag/`versionName` matching holds for it too.
- The release APK declares only `INTERNET` and `REQUEST_INSTALL_PACKAGES` (plus the signature-level permission AndroidX Core
  adds on its own, see constitution 1.0.1).

## Observation

The system confirmation dialog ("Install this app?") is shown for every install, as required. During the first
run an install completed while my scripted taps were landing on the screen; the second run showed the dialog
waiting for input until it was declined. No code path installs without the system dialog.
