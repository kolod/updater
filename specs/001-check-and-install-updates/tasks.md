---

description: "Task list for 001-check-and-install-updates"
---

# Tasks: Check and Install Updates

**Input**: Design documents from `/specs/001-check-and-install-updates/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: MANDATORY for decision logic (constitution Principle VI): every `…Test.kt` task is written
first and MUST fail before its implementation task is started. Purely visual Compose layout has no tests.

**Organization**: Grouped by user story. Package root for Kotlin sources:
`io/github/kolod/updater` (shortened to `…/updater` below). `core/` = pure Kotlin/JVM module (no
`android.*`), `app/` = Android module.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1…US5 from spec.md; Setup/Foundational/Polish have none

**Story order note**: US3 (verification) is built **before** US2 (installation): the constitution
forbids handing any APK to the installer unverified, so US2 depends on US3's pure checks.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Buildable two-module skeleton, CI, policy gates.

- [X] T001 Create the Gradle skeleton: `settings.gradle.kts` (modules `:core`, `:app`), root `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml` (AGP 9.5.0, Kotlin 2.4.20, Compose BOM 2026.09.00, activity-compose 1.13.0, lifecycle 2.11.0, core-ktx 1.19.1, junit 4.13.2, plus pinned `org.json:json` and `kotlinx-coroutines-test`), and copy the Gradle wrapper + `gradle/gradle-daemon-jvm.properties` (JDK 25 toolchain) from `kolod/circuitopedia`
- [X] T002 [P] Create `core/build.gradle.kts` (kotlin-jvm; `compileOnly` + `testImplementation` `org.json:json`; JUnit 4; test resources dir `src/test/resources`)
- [X] T003 [P] Create `app/build.gradle.kts` (application id `io.github.kolod.updater`, namespace, minSdk 26, compileSdk/targetSdk 37, versionCode/versionName from `-PappVersionCode/-PappVersionName` defaulting to `1` / `0.1.0-dev`, release signing from git-ignored `keystore.properties`, R8 minify, Compose enabled, Lint errors for `MissingTranslation`/`ExtraTranslation`/`MissingDefaultResource`, dependency on `:core`, `kotlinx-coroutines-test` as test dependency) and `app/proguard-rules.pro`
- [X] T004 [P] Create `app/src/main/AndroidManifest.xml` with exactly `INTERNET` + `REQUEST_INSTALL_PACKAGES`, `<queries>` for the four managed package names (no `QUERY_ALL_PACKAGES`), `MainActivity` (`singleTop`, portrait, launcher), no services/receivers/providers; add launcher icon resources under `app/src/main/res/mipmap-*` and a minimal theme in `app/src/main/res/values/themes.xml`
- [X] T005 [P] Create `.github/workflows/test.yml`: JDK per `gradle-daemon-jvm.properties`, `./gradlew test lint :app:assembleDebug`, and the policy-gate step from quickstart §2 (fails on scheduler APIs, `QUERY_ALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`; asserts manifest permissions equal exactly the two allowed)
- [X] T006 [P] Create `CHANGELOG.md` (English, Keep-a-Changelog style, `Unreleased` section) and extend `.gitignore` with `keystore.properties`, `*.jks`, `app/release.keystore.jks`

**Checkpoint**: `./gradlew :core:test :app:assembleDebug` runs (no tests yet).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Pure-core building blocks used by every story. All test-first. **No user story starts before this phase is done.**

- [ ] T007 [P] Write `core/src/test/kotlin/…/updater/core/version/SemVerTest.kt`: parsing (valid, invalid, leading `v` rejected, build metadata), precedence per SemVer 2.0.0 (`0.1.0-dev` < `0.1.0`, `1.0.0-alpha < alpha.1 < alpha.beta < beta < beta.2 < beta.11 < rc.1 < 1.0.0`), equality, numeric vs. alphanumeric identifiers, huge numbers do not crash
- [ ] T008 Implement `core/src/main/kotlin/…/updater/core/version/SemVer.kt` (`parse(String): SemVer?`, `Comparable`) until T007 passes
- [ ] T009 [P] Write `core/src/test/kotlin/…/updater/core/catalog/CatalogTest.kt`: five entries exactly as in [contracts/catalog.md](contracts/catalog.md); unique ids/package names; valid package-name syntax; exactly one `isSelf`; the self entry is last; every asset/tag expectation matches the contract
- [ ] T010 Implement `core/src/main/kotlin/…/updater/core/catalog/ManagedApp.kt` and `Catalog.kt` until T009 passes
- [ ] T011 [P] Write `core/src/test/kotlin/…/updater/core/net/NetworkPolicyTest.kt`: table-driven cases from [contracts/network-policy.md](contracts/network-policy.md) (http scheme, foreign host, lookalike hosts, user-info, port, redirect to foreign host, redirect loop >5, asset URL of another repository, allowed GitHub hosts)
- [ ] T012 Implement `core/src/main/kotlin/…/updater/core/net/NetworkPolicy.kt` (`isAllowed`, `nextRedirect`) until T011 passes
- [ ] T013 [P] Record fixtures into `core/src/test/resources/fixtures/` with `gh api repos/kolod/<repo>/releases?per_page=30` trimmed to the fields in [contracts/github-releases.md](contracts/github-releases.md): `circuitopedia-releases.json`, `hentai-reader-releases.json`, `whiskergrid-releases.json`, `ghost-bms-releases.json`; hand-write `prerelease-and-draft.json`, `no-apk-asset.json`, `empty.json`, `malformed.json`, `rate-limit-headers.txt`
- [ ] T014 [P] Write `core/src/test/kotlin/…/updater/core/release/GitHubReleaseParserTest.kt` and `ReleaseSelectorTest.kt`: monorepo picks `android-v0.1.0` never `windows-v0.4.0`; drafts/pre-releases ignored; tag not matching prefix or not SemVer ignored; missing APK asset → no candidate; `digest` `sha256:` parsed, other algorithms ignored; checksum asset attached for hentai-reader; highest SemVer wins regardless of order; empty list, malformed JSON → `BadResponse`; asset URL outside the repository path rejected
- [ ] T015 Implement `core/src/main/kotlin/…/updater/core/release/Release.kt`, `GitHubReleaseParser.kt`, `ReleaseSelector.kt` until T014 passes
- [ ] T016 [P] Write `core/src/test/kotlin/…/updater/core/status/StatusResolverTest.kt`: up to date, update available, not installed, installed newer than latest (no downgrade offered), dev installed version older than release, unparsable installed `versionName` treated as older, no release, no installable file
- [ ] T017 Implement `core/src/main/kotlin/…/updater/core/status/UpdateStatus.kt`, `FailureReason.kt`, `StatusResolver.kt` (types from [data-model.md](data-model.md)) until T016 passes
- [ ] T018 [P] Write `core/src/test/kotlin/…/updater/core/net/ResponseMapperTest.kt`: status/header → result per the error-mapping table (200, 404, 403/429 with `X-RateLimit-Remaining: 0` and reset time, 403 without it, 5xx, network exception classes → `Offline`), retry decision (≤2, never for 403/429/404, backoff 1 s/2 s, none after cancel)
- [ ] T019 Implement `core/src/main/kotlin/…/updater/core/net/ResponseMapper.kt` until T018 passes
- [ ] T020 [P] Create string scaffolding for all six locales: `app/src/main/res/values/strings.xml` (English base, `app_name`) and `values-de`, `values-fr`, `values-es`, `values-ja`, `values-uk`; write `app/src/test/kotlin/…/updater/LocaleCoverageTest.kt` comparing string and plural keys across all six files (fails on missing/extra keys, checks Ukrainian plural categories `one/few/many/other` and Japanese `other`)

**Checkpoint**: `./gradlew :core:test :app:testDebugUnitTest` green; foundation ready.

---

## Phase 3: User Story 1 — See which apps have updates (Priority: P1) 🎯 MVP part 1

**Goal**: Opening the app (and the button) checks all five apps and shows version + status per row.

**Independent Test**: With some managed apps installed at older versions, open the updater; each row shows correct installed/latest versions and status; the button repeats the check; offline shows "check failed" with stale data; no activity when the app is closed (quickstart scenarios 1–3, 9, 11).

### Tests for User Story 1 (write first, must fail)

- [ ] T021 [P] [US1] Write `app/src/test/kotlin/…/updater/UpdaterViewModelCheckTest.kt` with fakes for `ReleaseSource`/`InstalledAppReader`: all five rows resolved; checks run in parallel; one failing app does not change others; rate-limit stops remaining requests and marks them with the same reason; second `check()` while checking is ignored; cancel stops the run and keeps previous rows; failed check keeps previous versions flagged `Stale`; `lastCheckedAt` updates only on completion
- [ ] T022 [P] [US1] Write `app/src/test/kotlin/…/updater/data/LastCheckStoreTest.kt`: round-trip, atomic write, corrupt file ignored, unknown `version` ignored, no data beyond the catalog ids

### Implementation for User Story 1

- [ ] T023 [P] [US1] Create the ports in `app/src/main/kotlin/…/updater/Ports.kt` exactly as in [contracts/core-api.md](contracts/core-api.md) (`ReleaseSource`, `InstalledAppReader`, `ApkDownloader`, `ApkInspector`, `ApkInstaller` and their result types)
- [ ] T024 [P] [US1] Implement `app/src/main/kotlin/…/updater/data/GitHubReleaseSource.kt`: `HttpURLConnection` GET per [contracts/github-releases.md](contracts/github-releases.md), headers/timeouts/redirects per [contracts/network-policy.md](contracts/network-policy.md) via `NetworkPolicy`, `runInterruptible` on `Dispatchers.IO`, bounded retry via `ResponseMapper`, result through `parseReleases`/`selectRelease`
- [ ] T025 [P] [US1] Implement `app/src/main/kotlin/…/updater/device/InstalledAppReader.kt`: `PackageManager.getPackageInfo` → `InstalledApp` (versionName, longVersionCode, signer SHA-256 set via `GET_SIGNING_CERTIFICATES`); `NameNotFoundException` → not installed
- [ ] T026 [P] [US1] Implement `app/src/main/kotlin/…/updater/data/LastCheckStore.kt` (atomic JSON file in `filesDir`, constructor takes a `File` for testability)
- [ ] T027 [US1] Implement the check part of `app/src/main/kotlin/…/updater/UpdaterViewModel.kt` (`UpdaterUiState`, `check()`, `cancel`, parallel `async` per app, snapshot restore as `Stale`) until T021 and T022 pass
- [ ] T028 [US1] Implement `app/src/main/kotlin/…/updater/MainActivity.kt`: start a check only when `savedInstanceState == null`; cancel running checks/downloads in `onStop` unless `isChangingConfigurations`; no other triggers
- [ ] T029 [P] [US1] Implement UI: `app/src/main/kotlin/…/updater/ui/UpdaterScreen.kt` (top bar with check button, last-checked line, list), `AppRow.kt` (name, installed → latest, status, stale marker), `Messages.kt` (`FailureReason`/status → string resource)
- [ ] T030 [US1] Add all US1 strings (statuses, check button, last checked, stale marker, offline/other failure messages, plural "N updates available") to `values/` and translate into `values-de`, `-fr`, `-es`, `-ja`, `-uk` in `app/src/main/res/`; `LocaleCoverageTest` stays green

**Checkpoint**: US1 independently demonstrable on a device (list + check button, no install).

---

## Phase 4: User Story 3 — Trust and safety of what gets installed (Priority: P1)

**Goal**: A pure, exhaustively tested gate that decides whether a downloaded APK may be installed.

**Independent Test**: Unit tests feed wrong checksum / wrong package / not-newer version / different signer and each is refused with its own reason; the happy path passes (quickstart scenario 10).

### Tests for User Story 3 (write first, must fail)

- [ ] T031 [P] [US3] Write `core/src/test/kotlin/…/updater/core/verify/ChecksumFileTest.kt` and `Sha256Test.kt`: `sha256sum` format (`<hex>  <name>`, `<hex> *<name>`), bare hex, upper/lower case, wrong asset name, garbage; hex comparison is case-insensitive and constant-structure
- [ ] T032 [P] [US3] Write `core/src/test/kotlin/…/updater/core/verify/InstallPolicyTest.kt`: wrong checksum (digest and `.sha256` each), both sources present and one mismatching, no published checksum (allowed), wrong package, archive `versionName` ≠ release version, version equal/older than installed, `versionCode` not newer, different signer set vs. overlapping signer, fresh install without signer comparison; check order sha256 → package → version → signer

### Implementation for User Story 3

- [ ] T033 [US3] Implement `core/src/main/kotlin/…/updater/core/verify/Sha256.kt` and `ChecksumFile.kt` until T031 passes
- [ ] T034 [US3] Implement `core/src/main/kotlin/…/updater/core/verify/InstallPolicy.kt` (`checkDownloadedApk`, `ApkFacts`, `VerifyResult`) until T032 passes
- [ ] T035 [P] [US3] Implement `app/src/main/kotlin/…/updater/device/ApkInspector.kt`: `getPackageArchiveInfo(path, GET_SIGNING_CERTIFICATES)` → `ApkFacts` (package, versionName, versionCode, signer SHA-256 set); `null` if the file is not a parsable APK
- [ ] T036 [US3] Add strings for `ChecksumMismatch`, `WrongPackage`, `VersionNotNewer`, `SignatureMismatch` (explains different signing key, no automatic uninstall) in `app/src/main/res/values*/strings.xml` for all six locales

**Checkpoint**: Verification gate complete and proven on the JVM; nothing can bypass it in US2.

---

## Phase 5: User Story 2 — Install an update or a missing app (Priority: P1) 🎯 MVP part 2

**Goal**: Download → verify → system confirmation → installed, with progress, cancel, cleanup and "Update all".

**Independent Test**: With one app outdated, Update → confirm → row "up to date", `cacheDir/downloads` empty; decline → no error; cancel → file gone; permission missing → guided (quickstart scenarios 4–8).

### Tests for User Story 2 (write first, must fail)

- [ ] T037 [P] [US2] Write `app/src/test/kotlin/…/updater/UpdaterViewModelInstallTest.kt` with fakes for downloader/inspector/installer: happy path ends `Installed` and re-reads the installed version; verification failure never calls `ApkInstaller`; decline → `Declined`, row back to update available, no error; cancel during download → `Cancelled`; file deleted on every terminal state; duplicate install of the same app ignored; permission missing → `NotAllowedToInstall` guidance then continues after `onInstallPermissionResult()`; "Update all" runs strictly sequentially and one failure does not stop the rest; next download starts only while visible
- [ ] T038 [P] [US2] Write `app/src/test/kotlin/…/updater/data/DownloadCleanupTest.kt`: `.part` file renamed only after a complete download; partial file removed on failure/cancel; start-up wipe empties `cacheDir/downloads` (directory injected)

### Implementation for User Story 2

- [ ] T039 [P] [US2] Implement `app/src/main/kotlin/…/updater/data/ApkDownloader.kt`: HTTPS download of the asset via `NetworkPolicy` with manual redirects, progress callback, single-pass SHA-256 while streaming, `cacheDir/downloads/<appId>-<version>.apk.part` → rename, optional checksum-asset fetch + `parseChecksumFile`, cancellation closes the connection
- [ ] T040 [P] [US2] Implement `app/src/main/kotlin/…/updater/device/SessionInstaller.kt`: `PackageInstaller` session (`MODE_FULL_INSTALL`, `setRequireUserAction(USER_ACTION_REQUIRED)`), mutable `PendingIntent.getBroadcast`, dynamic non-exported receiver registered only for the session, `STATUS_PENDING_USER_ACTION` → launch confirmation intent, map `STATUS_SUCCESS`/`ABORTED`/`CONFLICT`/`INCOMPATIBLE`/others to `InstallResult`
- [ ] T041 [US2] Extend `app/src/main/kotlin/…/updater/UpdaterViewModel.kt` with `install(appId)`, `updateAll()`, `cancel`, the `InstallAttempt` state machine from [data-model.md](data-model.md), verification through `checkDownloadedApk`, and cleanup in `finally`, until T037 and T038 pass
- [ ] T042 [US2] Wire the install-permission flow in `MainActivity.kt`: `canRequestPackageInstalls()` check, launch `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for `package:<own id>`, forward the result to `onInstallPermissionResult()`; wipe `cacheDir/downloads` at process start
- [ ] T043 [P] [US2] Extend UI in `ui/AppRow.kt` and `ui/UpdaterScreen.kt`: Install/Update/Cancel buttons, progress bar, "Update all" action, installed/declined/failed row states
- [ ] T044 [US2] Add US2 strings (Install, Update, Update all, Cancel, downloading with progress, verifying, awaiting confirmation, installed, declined, permission guidance) in `app/src/main/res/values*/strings.xml` for all six locales

**Checkpoint**: Core product works end to end (US1 + US3 + US2) — **MVP**.

---

## Phase 6: User Story 4 — Updater updates itself (Priority: P2)

**Goal**: The updater is a normal catalog row, installed last in "Update all", through the same verified flow.

**Independent Test**: Publish a newer updater release, check, install; the app restarts at the new version (quickstart §4).

- [ ] T045 [P] [US4] Write `app/src/test/kotlin/…/updater/UpdaterViewModelSelfUpdateTest.kt`: the self entry goes through the identical verify/install path (no special-casing besides order); "Update all" queue puts the self entry last regardless of catalog order; failure of another app does not skip the self update decision; after a self-update the next check shows the new version
- [ ] T046 [US4] Implement the queue ordering in `UpdaterViewModel.updateAll()` (`isSelf` last) until T045 passes
- [ ] T047 [P] [US4] Create `.github/workflows/release.yml` modelled on `kolod/circuitopedia`: tag `v*.*.*` must be on `main`, signing job in the approval-gated `release` environment with read-only permissions, `versionName` from the tag, `versionCode` from `run_number`, rename to `updater.apk`, generate `updater.apk.sha256` (`sha256sum` format), separate publish job without secrets uploading both files
- [ ] T048 [P] [US4] Document the one-time release prerequisites in `README.md`: create the signing keystore, the `release` environment and the secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`; and that the updater's own APK must keep the same signing key forever

**Checkpoint**: Self-update proven by tests; release pipeline ready for the first tag.

---

## Phase 7: User Story 5 — Understand and recover from problems (Priority: P3)

**Goal**: Specific, localized messages and per-app retry.

**Independent Test**: Offline, rate-limited, missing-file and storage-full conditions each show their own message; retry re-runs only that app (quickstart scenarios 2, 10; US5 acceptance).

- [ ] T049 [P] [US5] Write `app/src/test/kotlin/…/updater/UpdaterViewModelRecoveryTest.kt`: `retry(appId)` re-checks/re-installs only that app; rate-limited state carries the reset time and sends no further requests; `NoInstallableFile` shown only for that app; `StorageFull` when free space < asset size + 10 % (injectable free-space provider) before any download starts; retry after cancel works
- [ ] T050 [US5] Implement `retry(appId)`, the free-space pre-check in `ApkDownloader.kt`/`UpdaterViewModel.kt`, and the `ENOSPC` → `StorageFull` mapping until T049 passes
- [ ] T051 [P] [US5] Extend `ui/Messages.kt` and `ui/AppRow.kt`: per-reason message text (rate-limit message formats the reset time with the user's locale), Retry button on failed rows
- [ ] T052 [US5] Add US5 strings (rate limit with time, no installable file, storage full, retry, server error, bad response) in `app/src/main/res/values*/strings.xml` for all six locales

**Checkpoint**: All five stories independently verified.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T053 [P] Run `./gradlew test lint :app:assembleRelease` locally (unsigned) and fix Lint findings; verify the release APK has no extra permissions via `aapt dump permissions`
- [ ] T054 [P] Run the policy gates from [quickstart.md](quickstart.md) §2 and confirm `test.yml` fails when a forbidden token is introduced (try it on a scratch commit, then revert)
- [ ] T055 Walk through all 12 manual scenarios in [quickstart.md](quickstart.md) §3 on a real device; record results in `specs/001-check-and-install-updates/checklists/manual-run.md`
- [ ] T056 [P] Verify on a device that Ghost BMS `versionName` equals its tag without `v` (open research item); if not, add the needed normalisation to the catalog test and `StatusResolver`
- [ ] T057 [P] Finalize `README.md` (what it does, privacy statement: only GitHub hosts, nothing sent, manual checks only, installation flow) and `CHANGELOG.md` entry for `0.1.0`; keep every user-facing string translated
- [ ] T058 Prepare the first release: ensure the previous bullets are merged to `main` via pull request, secrets in place (T048), then tag `v0.1.0`; verify `updater.apk` and `updater.apk.sha256` are published and that an older updater build offers the update (quickstart §4)

---

## Dependencies & Execution Order

- **Setup (Phase 1)** → **Foundational (Phase 2)** → user stories.
- **US1** needs Phase 2 only. **US3** needs Phase 2 only (pure core) and can run in parallel with US1.
- **US2** needs US1 (ViewModel, ports, UI shell) **and** US3 (verification gate).
- **US4** needs US2 (install pipeline). **US5** needs US1 and US2.
- **Polish** needs all stories.

```text
Setup ─▶ Foundational ─┬─▶ US1 ─────┐
                       └─▶ US3 ─────┴─▶ US2 ─▶ US4
                                         └───▶ US5 ─▶ Polish
```

Within each story: tests first (must fail) → pure core → Android adapters → ViewModel → UI → strings.

## Parallel Opportunities

- Setup: T002–T006 together after T001.
- Foundational: the test/implementation pairs (T007/T008, T009/T010, T011/T012, T014/T015, T016/T017, T018/T019) are independent of each other; T013 and T020 run alongside.
- US1 ∥ US3 (different modules): e.g. T021, T022, T031, T032 can all be written at the same time.
- US2 adapters: T039 ∥ T040 ∥ T043.
- US4: T047 ∥ T048 ∥ T045.

## Implementation Strategy

1. **MVP**: Phases 1–2, then US1 and US3 (parallel), then US2 — stop and validate quickstart scenarios 1–9 on a device. This is the first usable updater.
2. Add US4 (self-update + release pipeline), then US5 (recovery), then Polish and tag `v0.1.0`.
3. Each phase ends with its checkpoint; merge to `main` only when all tasks of the spec are done and tests are green (constitution Principle VIII).
