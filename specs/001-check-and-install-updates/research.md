# Research: Check and Install Updates

All Technical Context unknowns are resolved below. Format: Decision / Rationale / Alternatives.
Facts about GitHub were verified against the real repositories on 2026-11-22; Android API behavior
was checked against developer.android.com documentation.

## R1. HTTP client — `HttpURLConnection`

- **Decision**: Use the platform `HttpURLConnection` for both the API call and the APK download,
  with redirects followed **manually** (max 5 hops), every hop checked against the host allow-list
  ([contracts/network-policy.md](contracts/network-policy.md)), HTTPS only.
- **Rationale**: Principle V prefers platform APIs. Manual redirects are required anyway: GitHub's
  `browser_download_url` redirects from `github.com` to a different host
  (`release-assets.githubusercontent.com` / `objects.githubusercontent.com`), and Principle I allows
  only GitHub-owned hosts, so an automatic redirect follower would bypass the allow-list.
- **Alternatives**: OkHttp (extra dependency, same need to hand-check redirects); Android
  `DownloadManager` (system-owned, follows redirects invisibly, leaves files in shared storage,
  weakens cleanup and host control).

## R2. JSON — `org.json`

- **Decision**: Parse GitHub responses with `org.json`. The Android runtime provides it; `:core`
  (plain JVM) declares `org.json:json` as `compileOnly` + `testImplementation`, so it is not packaged
  twice and parsing is tested on the JVM against fixtures. Exact version pinned at implementation.
- **Rationale**: Only ~8 fields are read; no new runtime library. Parsing is on the critical safety
  path, so it must be JVM-testable (Principle VI) — which rules out parsing in `:app`.
- **Alternatives**: kotlinx.serialization (plugin + runtime dependency for 8 fields); Moshi/Gson
  (dependency); parsing in `:app` (untestable on the JVM without Robolectric).

## R3. Concurrency — coroutines via Lifecycle ViewModel

- **Decision**: `viewModelScope` with structured concurrency; the five checks run in parallel
  (`async`), blocking I/O wrapped in `runInterruptible` on `Dispatchers.IO` so cancellation closes
  the connection promptly. `kotlinx-coroutines-test` as the only extra test dependency.
- **Rationale**: Lifecycle ViewModel already brings `kotlinx-coroutines-android` transitively, so
  there is no new runtime dependency; cancellation requirements (FR-008) fit coroutines naturally.
- **Alternatives**: raw threads/executors (manual cancellation bookkeeping); RxJava/Flow-only
  libraries (dependency).

## R4. Choosing the release — list endpoint, not `/releases/latest`

- **Decision**: One request per app: `GET /repos/{owner}/{repo}/releases?per_page=30`. The selector
  takes the first release (API order = newest first) that is not a draft, not a pre-release, whose
  tag matches the catalog's tag pattern, parses as SemVer after stripping the prefix, and contains
  the expected APK asset. Highest SemVer among matches wins if the order is ever unreliable.
- **Rationale**: `/releases/latest` returns the newest release of *any* platform — in
  `kolod/hentai-reader` that is `windows-v0.4.0`, not the Android release. Verified: the list there
  contains `windows-v0.4.0` followed by `android-v0.1.0`. Taking the maximum SemVer among matching
  tags is safe against ordering quirks; 30 per page keeps the response small but deep enough for a
  monorepo with many other-platform releases. Hits no more than one API request per app (FR-017).
- **Alternatives**: `/releases/latest` (wrong for monorepos); `/tags` (no assets/draft info);
  scraping HTML (fragile, not an API).

## R5. Integrity sources — API `digest` and `.sha256` asset

- **Decision**: Verify the downloaded APK against **every** SHA-256 available for it:
  (a) the asset's `digest` field (`"sha256:<hex>"`, present on all inspected assets: circuitopedia
  v0.2.0, hentai-reader android-v0.1.0); (b) the catalog-declared checksum asset when the app has one
  (hentai-reader's `hentai-reader.apk.sha256`, parsed in `sha256sum` format `<hex>  <name>` or bare
  hex). Any mismatch refuses the install. If neither exists, rely on package/version/signature checks
  and say so in the UI row details.
- **Rationale**: Satisfies Principle II "if the release publishes a SHA-256, it MUST match" using
  data that already exists, with no release-process changes for the four apps.
- **Limit (accepted)**: Both digest and APK come from the same GitHub account, so this detects
  corruption/CDN tampering, not a compromised account. The real authenticity guarantee is Android's
  signature check (R8, R7).

## R6. Version matching

- **Decision**: SemVer 2.0.0 parse (`MAJOR.MINOR.PATCH[-pre][+build]`, precedence rules incl.
  numeric vs alphanumeric pre-release identifiers; `0.1.0-dev` < `0.1.0`). Release version = tag with
  the catalog's `tagPrefix` removed (`v` or `android-v`). Installed version = `PackageInfo.versionName`.
  A non-parsable installed `versionName` is shown as "unknown version" and treated as older
  (offering the update), never crashing.
- **Rationale**: Verified in `circuitopedia/.github/workflows/release.yml`:
  `versionName = ${GITHUB_REF_NAME#v}` (and `versionCode = run_number`, which is per-repo and **not**
  comparable to tags) — so tag-minus-prefix equals the installed `versionName` for released builds.
  Version codes are used only in the post-download check (R8), where the system itself requires
  `new versionCode > installed`.
- **Alternatives**: comparing `versionCode` with tags (impossible); string equality (no ordering).

## R7. Installation — `PackageInstaller` session, user confirmation always

- **Decision**: `PackageInstaller.createSession(MODE_FULL_INSTALL)` with
  `setRequireUserAction(USER_ACTION_REQUIRED)`, stream the verified file into the session, `commit`
  with an `IntentSender` from a **mutable** `PendingIntent.getBroadcast` (the system adds status
  extras). A `BroadcastReceiver` registered **dynamically** for the duration of one session (not in
  the manifest) receives status: `STATUS_PENDING_USER_ACTION` → launch the provided confirmation
  intent while the user is in the app; `STATUS_SUCCESS` / `STATUS_FAILURE_*` / `STATUS_FAILURE_ABORTED`
  (user declined) → map to outcomes. `UPDATE_PACKAGES_WITHOUT_USER_ACTION` is **not** declared and
  `USER_ACTION_REQUIRED` is explicit, so no OS version can make an install silent (FR-012).
- **Permission flow**: before the first install, `PackageManager.canRequestPackageInstalls()`; if
  false, open `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with `package:<own id>`, and continue
  automatically on return when the check passes (FR-015).
- **Rationale**: Documented, supported path (verified in the Android docs for `PackageInstaller`,
  `SessionParams.setRequireUserAction`, `canRequestPackageInstalls`). Sessions give explicit status
  codes (needed for FR-013 signature-conflict messaging via `STATUS_FAILURE_CONFLICT` /
  `STATUS_FAILURE_INCOMPATIBLE`) and avoid exposing a `FileProvider`.
- **Alternatives**: `ACTION_VIEW`/`ACTION_INSTALL_PACKAGE` with a `FileProvider` URI (no result
  codes, needs a provider, deprecated direction); root/Shizuku silent install (violates Principle II).

## R8. Pre-install verification of the downloaded file (Android layer, rules in core)

- **Decision**: `PackageManager.getPackageArchiveInfo(path, GET_SIGNING_CERTIFICATES)` gives package
  name, `versionName`, `longVersionCode` and signer certificates of the downloaded APK. `:core`'s
  `InstallPolicy` decides (pure function over plain values): package == catalog id; archive
  `versionName` == release version; archive version > installed version (SemVer) and archive
  versionCode > installed versionCode when installed; signer SHA-256 set intersects the installed
  app's signer set when installed. Each failure has its own `FailureReason`.
- **Rationale**: Gives FR-013 a clear pre-install message ("signed with a different key") before the
  system fails; the system's own check remains the final authority and is still handled (R7).
  Keeping the decision pure keeps it in the JVM-tested core.
- **Alternatives**: rely only on the system rejection (poorer message, a wasted confirmation).

## R9. Last-check snapshot for "not fresh" data (FR-006)

- **Decision**: After each check write a small JSON file in `filesDir` (per app: latest version,
  time, status) and load it at start to show prior data, flagged "not fresh", until a check
  completes. Atomic write (temp + rename). Corrupt/missing file ⇒ treated as no data.
- **Rationale**: Without persistence, a cold start while offline would show nothing, defeating the
  spec's offline scenario. A file is the simplest store (no database/DataStore dependency).
- **Alternatives**: SharedPreferences (acceptable but stringly typed); DataStore (dependency).

## R10. When checks run (Principle III)

- **Decision**: Check starts in `MainActivity.onCreate` when `savedInstanceState == null` (a fresh
  user launch; rotation/process-restore are not new launches) and on the button. `onStop` (not caused
  by a configuration change) cancels running checks/downloads, matching the spec's assumption.
  There is no `WorkManager`, `AlarmManager`, `JobScheduler`, boot/package receiver, foreground
  service or notification code anywhere; a CI grep gate enforces this.
- **Rationale**: Directly encodes FR-002 / SC-004. The installer's status receiver is dynamic and
  exists only while a session is being committed from a visible app.
- **Alternatives**: `onStart`-based checks (would re-check on every return — rejected by spec
  assumption).

## R11. Seeing the managed apps without broad permissions

- **Decision**: `<queries>` with five `<package android:name="…"/>` entries (the four managed ids;
  the updater itself is always visible) and no `QUERY_ALL_PACKAGES`. `getPackageInfo` throwing
  `NameNotFoundException` ⇒ "not installed".
- **Rationale**: Required by the constitution; package visibility (API 30+) otherwise hides them.
- **Alternatives**: `QUERY_ALL_PACKAGES` (forbidden); `<queries>` intent filters (broader than needed).

## R12. Update all and self-update ordering

- **Decision**: "Update all" builds a queue of outdated apps ordered with the updater itself last,
  and runs strictly one at a time: download → verify → commit → wait for the installer result →
  next. The next download starts only while the app is visible (resumes on return to foreground after
  the system dialog). Interrupted queue = remaining rows stay "update available". The updater's own
  update ends with the OS restarting the process; the next manual launch re-checks and shows the new
  version.
- **Rationale**: One system dialog at a time is the only workable UX; ordering protects the queue
  from being killed by the self-replacement.

## R13. UI

- **Decision**: One Compose Material 3 screen (top bar with check button + last-checked line, a
  lazy list of rows, an "Update all" action). Row = name, installed → latest, status text/icon,
  progress bar, action button (Install/Update/Retry/Cancel). No navigation library. Rotation-safe via
  ViewModel. System dialogs handle confirmation and permission.
- **Rationale**: Mirrors the managed apps' toolkit; one screen needs no navigation framework.

## R14. Rate limit and error mapping

- **Decision**: HTTP 403/429 with `X-RateLimit-Remaining: 0` ⇒ `RateLimited(resetAt)` from
  `X-RateLimit-Reset` (epoch seconds); remaining checks in that run are skipped (not sent) and
  marked with the same reason. Other mapping: `UnknownHostException`/`SocketTimeout`/`ConnectException`
  ⇒ `Offline`; 404 ⇒ `NoRelease` (repo/releases missing); non-2xx ⇒ `ServerError(code)`; JSON failure ⇒
  `BadResponse`; no matching release/asset ⇒ `NoRelease` / `NoInstallableFile`; `ENOSPC` ⇒ `StorageFull`.
  Bounded retry (2 retries, exponential backoff 1 s/2 s) only for connection-level failures and 5xx.
- **Rationale**: Matches spec User Story 5 and the constitution's retry rule; unauthenticated limit is
  60/h so no speculative retries on 403.

## R15. Downloads and cleanup

- **Decision**: Download to `cacheDir/downloads/<appId>-<version>.apk.part`, rename on success;
  verify; always delete in a `finally` (success, failure, decline, cancel). On process start, wipe
  `cacheDir/downloads`. Free-space pre-check from the asset `size` (+10% margin) via `StatFs`.
  Hash is computed while streaming (single pass, 64 KiB buffer).
- **Rationale**: FR-014, SC-006; `cacheDir` is private and cleared by the system under pressure.

## R16. Localization verification

- **Decision**: Same mechanism as Circuitopedia: Lint `MissingTranslation`, `ExtraTranslation`,
  `MissingDefaultResource` as errors, plus a JVM `LocaleCoverageTest` that compares string and
  plural keys across `values`, `values-de/fr/es/ja/uk`. Plurals used for "N updates available";
  Ukrainian gets `one/few/many/other`, Japanese `other` only.
- **Rationale**: SC-007 must be machine-checked, not reviewed by eye.

## R17. CI and release

- **Decision**: `test.yml` runs `./gradlew test lint` plus the Principle III/I grep gate (no
  scheduler APIs, no extra hosts, no `QUERY_ALL_PACKAGES`). `release.yml` copies Circuitopedia's
  hardened two-job design (tag must be on `main`, signing job in an approval-gated environment with
  read-only permissions, publish job without secrets), names the asset `updater.apk`, and also
  uploads `updater.apk.sha256`. `versionName` from the tag, `versionCode` from `run_number`.
- **Rationale**: Reuses a proven pipeline; satisfies Principle IX and makes the updater's own release
  verifiable by the same code path it applies to others.

## Open items carried to tasks

- Pin exact versions for `org.json:json` and `kotlinx-coroutines-test`.
- Confirm Ghost BMS `versionName` = tag-minus-`v` by checking the first installed build on a device
  (its `release.yml` derives `appVersionName` from `steps.version.outputs.name`; same pattern as the
  other apps).
- Create the signing key, `release` environment and secrets for `kolod/updater` before the first tag.
