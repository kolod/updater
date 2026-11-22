# Contract: `:core` public surface

Pure Kotlin/JVM. Functions are total: expected failures are returned as result types, not thrown.
`:app` depends on `:core`; never the reverse.

```kotlin
// version/
class SemVer : Comparable<SemVer> { companion object { fun parse(s: String): SemVer? } }

// release/
fun parseReleases(json: String, app: ManagedApp): ParseResult   // Ok(List<Release>) | BadResponse
fun selectRelease(releases: List<Release>): Release?             // highest eligible SemVer

// status/
fun resolveStatus(app: ManagedApp, installed: InstalledApp?, release: Release?): UpdateStatus

// verify/
fun parseChecksumFile(text: String, assetName: String): String?  // "<hex>  <name>" or bare hex
fun checkDownloadedApk(
    app: ManagedApp, release: Release, apk: ApkFacts, installed: InstalledApp?,
    computedSha256: String, publishedSha256: List<String>,
): VerifyResult   // Ok | Fail(FailureReason); order: sha256, package, version, signer

// net/
fun isAllowed(url: String, app: ManagedApp? = null): Boolean
fun nextRedirect(current: String, location: String, hops: Int): RedirectDecision
```

## `:app` ports

Plain Kotlin interfaces, each with a real implementation and a test fake (the two uses that justify
them under Principle V):

```kotlin
interface ReleaseSource      { suspend fun fetch(app: ManagedApp): FetchResult }
interface InstalledAppReader { fun read(app: ManagedApp): InstalledApp? }
interface ApkDownloader      { suspend fun download(release: Release, onProgress: (Long, Long) -> Unit): DownloadedApk }
interface ApkInspector       { fun inspect(file: File): ApkFacts? }
interface ApkInstaller       { suspend fun install(file: File, app: ManagedApp): InstallResult }
```

## UI contract

`UpdaterViewModel` exposes `UpdaterUiState`: list of `AppState`, an `InstallAttempt` per app,
`lastCheckedAt`, `isChecking`, `canInstallFromHere`. Events: `check()`, `install(appId)`,
`updateAll()`, `cancel(appId | all)`, `retry(appId)`, `onInstallPermissionResult()`. ViewModel tests
drive these events with fakes, so the screen itself only renders state.
