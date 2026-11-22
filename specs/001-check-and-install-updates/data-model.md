# Data Model: Check and Install Updates

Pure Kotlin types in `:core` (package `io.github.kolod.updater.core`), immutable data classes /
sealed interfaces. No Android types. Names are indicative; tasks define exact signatures.

## ManagedApp (catalog entry — fixed at build time)

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | Stable catalog key, e.g. `whiskergrid` |
| `displayName` | `String` | Proper noun, not translated |
| `packageName` | `String` | Android `applicationId`; verified against the downloaded APK |
| `repoOwner`, `repoName` | `String` | GitHub repository |
| `tagPrefix` | `String` | `v` or `android-v`; tag must start with it and the rest must be SemVer |
| `apkAssetName` | `String` | Exact asset file name, e.g. `whisker-grid.apk` |
| `checksumAssetName` | `String?` | Only hentai-reader (`hentai-reader.apk.sha256`) |
| `isSelf` | `Boolean` | True for the updater; ordered last in "Update all" |
| `optional` | `Boolean` | Listed, checked and updated only while installed (Hentai Reader); never offered for installation, no network request while absent |

Validation: package name matches `[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+`; `tagPrefix` non-empty; catalog has
unique `id` and `packageName`; exactly one `isSelf`. Entries: see [contracts/catalog.md](contracts/catalog.md).

## SemVer

`major`, `minor`, `patch: Int`; `preRelease: List<Identifier>`; build metadata ignored in precedence.
`Comparable`. `parse(String): SemVer?` returns null for invalid input (never throws).

## Release (one candidate parsed from GitHub)

| Field | Type | Notes |
|---|---|---|
| `tag` | `String` | Raw tag name |
| `version` | `SemVer` | Parsed from tag after removing `tagPrefix` |
| `isDraft`, `isPreRelease` | `Boolean` | Both must be false to be eligible |
| `apk` | `ReleaseAsset` | The asset named `apkAssetName` |
| `checksumAsset` | `ReleaseAsset?` | Present when the catalog expects one and the release has it |

`ReleaseAsset`: `name`, `size: Long`, `downloadUrl: String`, `sha256: String?` (lower-case hex from `digest`).

## InstalledApp (read from the device, passed in as plain values)

`packageName`, `versionName: String?`, `versionCode: Long`, `signerSha256: Set<String>`; absence is `null`
(not installed). Refreshed on every check and after every install attempt.

## UpdateStatus (result of one check for one ManagedApp)

Sealed interface; every variant carries `checkedAt` (epoch millis) via the wrapper `AppState`:

| Variant | Meaning |
|---|---|
| `UpToDate(installed, latest)` | latest ≤ installed |
| `UpdateAvailable(installed, release)` | latest > installed |
| `NotInstalled(release)` | not installed, a release exists |
| `NoRelease` | repository has no eligible release (or only pre-releases / other platforms) |
| `NoInstallableFile(latest)` | an eligible tag exists but lacks the APK asset |
| `Failed(reason)` | check failed; see FailureReason |

`AppState(app, installed?, status, freshness)` where `freshness` is `Fresh` (from this session's
check) or `Stale(since)` (restored from the snapshot, or kept after a failed check).

## FailureReason (also used for install failures)

`Offline`, `RateLimited(resetAtEpochSeconds?)`, `ServerError(httpCode)`, `BadResponse`, `StorageFull`,
`ChecksumMismatch`, `WrongPackage`, `VersionNotNewer`, `SignatureMismatch`, `InstallerRejected(code)`,
`Cancelled` (not shown as an error), `NotAllowedToInstall`. Each maps to one string resource
(`ui/Messages.kt`); Ukrainian/Japanese plural rules apply only to counts, not to reasons.

## InstallAttempt (UI/ViewModel state per app, not persisted)

State machine:

```text
Idle ──start──▶ Downloading(progress) ──ok──▶ Verifying ──ok──▶ AwaitingConfirmation
  ▲                 │ cancel/fail                │ fail                │
  │                 ▼                            ▼                     ├─ user confirms ─▶ Installed ─▶ (re-check row) ─▶ Idle
  └──────────── Failed(reason) / Cancelled ◀────┴─────────────────────┴─ user declines ─▶ Declined ─▶ Idle
```

Rules: at most one attempt per app and one check at a time; the downloaded file is deleted on every
transition into `Idle`, `Failed`, `Cancelled`, `Declined`, `Installed`. `Declined` is not an error.
"Update all" is a queue of apps in `Idle` with `UpdateAvailable`/`NotInstalled`, self last.

## LastCheckSnapshot (persisted in `filesDir`, optional)

`{ "version": 1, "apps": { "<id>": { "latest": "x.y.z"|null, "status": "...", "checkedAt": 0 } } }`.
Contains no personal data and no installed-app inventory beyond the five catalog ids. Unknown
`version` or malformed content ⇒ ignored.
