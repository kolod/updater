# Implementation Plan: Check and Install Updates

**Branch**: `001-check-and-install-updates` | **Date**: 2026-11-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-check-and-install-updates/spec.md`

## Summary

A single-screen Compose app lists five catalog apps (four managed apps and the updater itself). On a
manual launch and on a button press it asks the GitHub Releases API (one request per app) for the
newest eligible release, compares it with the installed version and shows a status per app. For an
outdated or missing app it downloads the APK from the release, verifies SHA-256 / package name /
version / signing certificate, and installs it through a `PackageInstaller` session with the system
confirmation. All decision logic (catalog, release selection, semantic-version comparison, checksum
and policy checks, host allow-list) lives in a pure-Kotlin `:core` module tested on the JVM against
recorded GitHub fixtures; the `:app` module is a thin Android layer (HTTP, storage, package queries,
installer, Compose UI). No scheduling of any kind exists. See [research.md](research.md) for the
decisions.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (same as the managed apps); JDK as in sibling projects' `gradle-daemon-jvm.properties`

**Primary Dependencies**: AndroidX only — Core KTX, Activity Compose, Lifecycle (ViewModel/Runtime Compose), Compose BOM 2026.09.00 + Material 3, AGP 9.5.0. `org.json` (platform-provided at runtime; `org.json:json` as `compileOnly` + `testImplementation` in `:core`). Test-only: JUnit 4, `kotlinx-coroutines-test`. No HTTP, JSON, DI or image libraries (justified in research R1–R3).

**Storage**: none persistent except an optional last-check snapshot in app-private files (R9); downloaded APKs in `cacheDir/downloads`, deleted after every attempt

**Testing**: JUnit 4 JVM unit tests in `:core` (fixtures) and `:app` (ViewModel with fake ports); a locale-coverage test and Android Lint (`MissingTranslation` as error) as in Circuitopedia; manual device run per [quickstart.md](quickstart.md)

**Target Platform**: Android 8.0+ (`minSdk` 26, `targetSdk`/`compileSdk` 37)

**Project Type**: mobile-app (Android) — two Gradle modules: `:core` (pure Kotlin/JVM) and `:app`

**Performance Goals**: all five rows resolved in <10 s on a normal connection (SC-001): checks run in parallel, one API request each

**Constraints**: INTERNET + REQUEST_INSTALL_PACKAGES only; hosts limited to `api.github.com`, `github.com` and GitHub's release-asset hosts; HTTPS only; no background work; unauthenticated GitHub limit 60/h (5 requests per check ⇒ ≥12 full checks/hour)

**Scale/Scope**: 5 catalog entries, 1 screen (+ system dialogs), ~6 locales × ~40 strings

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|---|---|---|
| I. Free, ad-free, minimal network | No ads/analytics/accounts; only GitHub hosts; anonymous requests; only INTERNET (+ REQUEST_INSTALL_PACKAGES per Technical Constraints) | ✅ Host allow-list is a tested pure function ([contracts/network-policy.md](contracts/network-policy.md)); `User-Agent` is a generic `Updater` string, no identifiers |
| II. Safe installation | Hash, package, version, signing pre-check, system confirmation, HTTPS, private storage, cleanup, no pre-releases | ✅ Verification pipeline in `:core` ([contracts/core-api.md](contracts/core-api.md)); `setRequireUserAction(USER_ACTION_REQUIRED)` set explicitly; `UPDATE_PACKAGES_WITHOUT_USER_ACTION` not declared |
| III. User-initiated checks only | No WorkManager/AlarmManager/boot receivers/services; checks on manual launch + button | ✅ Check triggered from `MainActivity.onCreate` with `savedInstanceState == null` and from the button; no manifest receivers/services; a CI grep gate forbids the scheduler APIs (T-gate in quickstart) |
| IV. Declarative catalog | Catalog bundled, tag pattern per app, semver incl. pre-release, ignore non-matching tags | ✅ `Catalog.kt` data + `ReleaseSelector`; monorepo case covered by `android-v` prefix and fixture |
| V. Simplicity | No unjustified libs; no speculative abstractions; pure core | ✅ Two modules enforce "no `android.*` in core" mechanically. Small port interfaces (`ReleaseSource`, `ApkInstaller`, `InstalledAppReader`) each have two uses: the real implementation and a test fake. No DI framework |
| VI. Testability | JVM tests, recorded fixtures, test-first for decision logic, no real network | ✅ Fixtures in `core/src/test/resources/fixtures/`; tasks must be test-first |
| VII. Localization | 6 locales, plurals, locale formatting, fallback | ✅ Lint `MissingTranslation` = error + locale-coverage test |
| VIII. Version control | Branch per spec, Conventional Commits | ✅ |
| IX. Distribution | Signed APK + SHA-256 via CI from tag; self in catalog | ✅ `release.yml` mirrors Circuitopedia's (two-job, approval-gated signing) and additionally publishes `updater.apk.sha256` |

**Result**: PASS — no violations; Complexity Tracking not needed. **Post-design re-check (after Phase 1)**: PASS — the design added no dependency, permission or host beyond those above.

## Project Structure

### Documentation (this feature)

```text
specs/001-check-and-install-updates/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── github-releases.md   # The external API subset consumed + fixtures
│   ├── catalog.md           # Catalog entry schema and the five entries
│   ├── core-api.md          # Public surface of :core
│   └── network-policy.md    # Allowed hosts, redirects, limits
├── checklists/requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/libs.versions.toml
CHANGELOG.md
.github/workflows/{test.yml,release.yml}

core/                                   # kotlin-jvm, NO android.* imports
├── build.gradle.kts
└── src/
    ├── main/kotlin/io/github/kolod/updater/core/
    │   ├── catalog/     ManagedApp.kt, Catalog.kt
    │   ├── version/     SemVer.kt
    │   ├── release/     Release.kt, GitHubReleaseParser.kt, ReleaseSelector.kt
    │   ├── verify/      Sha256.kt, ChecksumFile.kt, InstallPolicy.kt
    │   ├── net/         NetworkPolicy.kt (host allow-list, redirect rules)
    │   └── status/      UpdateStatus.kt, StatusResolver.kt, FailureReason.kt
    └── test/
        ├── kotlin/io/github/kolod/updater/core/...   # one test class per main file
        └── resources/fixtures/   # recorded GitHub API responses (see contracts/github-releases.md)

app/
├── build.gradle.kts
├── proguard-rules.pro
└── src/
    ├── main/
    │   ├── AndroidManifest.xml          # INTERNET, REQUEST_INSTALL_PACKAGES, <queries>, portrait
    │   ├── kotlin/io/github/kolod/updater/
    │   │   ├── MainActivity.kt          # launch trigger, stop → cancel
    │   │   ├── UpdaterViewModel.kt      # state holder, check/install orchestration
    │   │   ├── data/   GitHubReleaseSource.kt, ApkDownloader.kt, LastCheckStore.kt
    │   │   ├── device/ InstalledAppReader.kt, ApkInspector.kt, SessionInstaller.kt
    │   │   └── ui/     UpdaterScreen.kt, AppRow.kt, Messages.kt (FailureReason → string res)
    │   └── res/values{,-de,-fr,-es,-ja,-uk}/strings.xml (+ plurals)
    └── test/kotlin/io/github/kolod/updater/  # ViewModel tests with fakes, LocaleCoverageTest
```

**Structure Decision**: Two Gradle modules. `:core` is a Kotlin/JVM module so the compiler itself
forbids Android imports (Principle V) and tests run in seconds without an emulator (Principle VI).
`:app` holds everything that touches Android. This mirrors Circuitopedia's `kotlin-jvm` + application
layout, but is one module smaller than hentai-reader's multi-component monorepo, which this project
does not need.

## Complexity Tracking

No constitution violations; table intentionally empty.
