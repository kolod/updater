<!--
Sync Impact Report
==================
Version change: (template) → 1.0.0
Bump rationale: initial ratification (self-update and the Ghost BMS applicationId were settled
before ratification was finalized).

Modified principles (template slot → new title):
  - [PRINCIPLE_1_NAME] → I. Free, Ad-Free, Minimal Network (NON-NEGOTIABLE)
  - [PRINCIPLE_2_NAME] → II. Safe Installation (NON-NEGOTIABLE)
  - [PRINCIPLE_3_NAME] → III. User-Initiated Checks Only
  - [PRINCIPLE_4_NAME] → IV. Declarative App Catalog
  - [PRINCIPLE_5_NAME] → V. Simplicity
  - VI. Testability
  - VII. Localization
  - VIII. Version Control
  - IX. Distribution
Added sections: Technical Constraints; Development Workflow & Quality Gates
Removed sections: none

Templates requiring updates:
  ✅ .specify/templates/plan-template.md      — Constitution Check is derived from this file by /speckit-plan
  ✅ .specify/templates/spec-template.md      — no change needed
  ✅ .specify/templates/tasks-template.md     — no change needed (tests are mandatory per Principle VI;
       localization tasks per Principle VII are added by /speckit-tasks)
  ✅ .specify/templates/checklist-template.md — no change needed

Follow-up TODOs: none.
-->

# Updater Constitution

Updater is a free, ad-free, open-source Android app, written in Kotlin, that checks whether new
versions of the owner's own Android apps are published on GitHub Releases and installs them. The
managed apps are:

| App | Repository | applicationId | Release tag shape | APK asset |
|---|---|---|---|---|
| Ghost BMS (unofficial) | `kolod/ghost-bms-unofficial` | `io.github.kolod.ghostbms` | `vX.Y.Z` | `ghost-bms-unofficial.apk` |
| Whisker Grid | `kolod/whiskergrid` | `io.github.kolod.whiskergrid` | `vX.Y.Z` | `whisker-grid.apk` |
| Hentai Reader | `kolod/hentai-reader` | `io.github.kolod.hentaireader` | `android-vX.Y.Z` (monorepo; Windows tags `windows-v*` MUST be ignored) | `hentai-reader.apk` (+ `.sha256`) |
| Circuitopedia | `kolod/circuitopedia` | `io.github.kolod.circuitopedia` | `vX.Y.Z` | `circuitopedia.apk` |
| Updater (itself) | `kolod/updater` | `io.github.kolod.updater` | `vX.Y.Z` | `updater.apk` |

## Core Principles

### I. Free, Ad-Free, Minimal Network (NON-NEGOTIABLE)

- The app MUST NOT contain ads, in-app purchases, paywalled features or subscriptions.
- The app MUST NOT include analytics, tracking or crash-reporting SDKs.
- Network access exists for one purpose only: reading release metadata and downloading APKs from
  GitHub (`api.github.com` and the release-asset download hosts GitHub redirects to). No other
  host MAY be contacted.
- Requests MUST be anonymous: no account, no token, no cookies, no device or user identifiers, and
  no list of installed apps sent anywhere. The only installed-app data the updater reads (package
  presence and `versionName`/`versionCode`) stays on the device.
- The `INTERNET` permission is declared because the core feature requires it; no other permission
  MAY be added unless justified in the spec that introduces it.

**Rationale**: The managed apps promise privacy and no tracking; their updater must not be the
weakest link. Network use is the unavoidable cost of the feature, so it is limited to the minimum.

### II. Safe Installation (NON-NEGOTIABLE)

- An APK MUST NOT be handed to the installer unless its integrity has been verified:
  - if the release publishes a SHA-256 (a `.sha256` asset or GitHub's asset `digest` field), the
    downloaded file MUST match it; a mismatch MUST discard the file and report an error;
  - the downloaded APK's package name MUST equal the catalog entry's `applicationId`, and its
    version MUST be newer than the installed one (no downgrade, no reinstall of the same version).
- Android's own signature check is the final authority: an update signed with a different key than
  the installed app MUST be reported to the user as a failure, never worked around (no
  uninstall-and-reinstall shortcut without explicit, informed user confirmation).
- Installation MUST use the platform `PackageInstaller` session API. The user MUST be able to see
  and confirm what is being installed; the app MUST NOT attempt to hide or bypass the system
  confirmation. Silent/unattended updates, if ever added, require a constitution amendment.
- Downloads MUST use HTTPS only, MUST be written to app-private storage, and MUST be deleted
  after installation, failure or cancellation.
- Pre-releases and drafts MUST be ignored unless the spec of a feature explicitly adds an opt-in.

**Rationale**: An updater is a remote-code-installation channel. Hash, package-name and signature
checks make sure that only the exact artifact CI published can replace an installed app.

### III. User-Initiated Checks Only

- Update checks MUST run only (a) when the app is launched manually by the user and (b) when the
  user presses the check button in the app.
- The app MUST NOT schedule background work for checking or downloading: no `WorkManager`,
  `AlarmManager`, boot receivers, foreground services kept alive for polling, push or any other
  scheduler.
- A running check or download MAY continue while the user keeps the app on screen; it MUST be
  cancellable and MUST NOT outlive the user's session as a hidden background task.
- The result of the last check (time, per-app outcome) MAY be cached locally for display, but a
  cached result MUST NOT be presented as a fresh check.
- Check failures (offline, GitHub rate limit, malformed release) MUST be shown per app, in a
  localized message, and MUST NOT block the other apps' checks.

**Rationale**: The owner explicitly wants no schedule — only manual launch and a button. This
keeps battery, network and attention costs predictable and the app trivially auditable.

### IV. Declarative App Catalog

- Managed apps are described by a bundled catalog: repository, `applicationId`, display name,
  release tag pattern, APK asset name (and optional checksum asset name).
- Adding or changing a managed app MUST be a catalog change plus translations, with no change to
  update-check or install logic.
- The catalog is part of the APK and is NOT downloaded at runtime; it MUST NOT be influenced by
  any remote source.
- Version comparison MUST follow Semantic Versioning, including pre-release suffixes
  (`0.1.0-dev` < `0.1.0`), and compare the release tag (after stripping the catalog's tag prefix)
  with the installed `versionName`. Tags that do not match an entry's pattern MUST be ignored,
  which is how the monorepo's Windows releases are skipped.
- An app that is not installed MUST be shown as "not installed" and be installable through the
  same verified flow; an installed app is "up to date", "update available" or "check failed".

**Rationale**: The four repositories already differ in tag shape and asset naming; encoding that
as data keeps the code generic and makes the trust boundary (what may be installed) explicit.

### V. Simplicity

- Every new library MUST be justified in the spec: what it replaces and why the Kotlin standard
  library or platform API (`HttpURLConnection`, `PackageInstaller`, `PackageManager`,
  `org.json`) is insufficient. Kotlin stdlib and official AndroidX/Jetpack libraries are
  preferred; Jetpack Compose is the UI toolkit, consistent with the managed apps.
- No speculative abstractions: interfaces, extra layers, DI frameworks and generic engines MUST NOT
  be introduced until at least two concrete uses exist (YAGNI).
- Release parsing, version comparison, catalog matching and checksum verification MUST live in
  pure Kotlin with no `android.*` imports. Android APIs are confined to the installer, package
  queries, storage and UI.
- Functions MUST be small and readable; clarity takes precedence over cleverness.

**Rationale**: A small, dependency-light codebase is easy to audit — important for an app that
installs other apps — and keeps the core logic testable on the plain JVM.

### VI. Testability

- Pure logic (version comparison, tag matching, release JSON parsing, checksum verification,
  update-state decisions) MUST be covered by fast JVM unit tests, including edge cases:
  pre-release vs release, equal and lower versions, missing asset, missing checksum, mismatched
  checksum, monorepo tags of another platform, empty release list, malformed JSON.
- Tests MUST use recorded GitHub API response fixtures; they MUST NOT hit the real network.
- Decision logic MUST be developed test-first: failing tests are written from the spec's expected
  outcomes before the implementation.
- UI behavior that carries logic (state display, error mapping, button enablement) MUST be tested;
  purely visual layout does not require tests.
- All tests MUST pass before any merge into `main` or any release.

**Rationale**: The decision "is this file safe and newer?" is the product's critical path; it must
be provable without a device and without GitHub.

### VII. Localization

- The development language (code, comments, commits, specs, docs) is English.
- Supported app languages: English (`en`, base and fallback), German (`de`), French (`fr`),
  Spanish (`es`), Japanese (`ja`), Ukrainian (`uk`) — the same set as the managed apps.
- User-facing strings MUST NOT be hardcoded; all text goes through Android string resources,
  using plurals resources wherever a count is shown (Ukrainian has several plural forms, Japanese
  has none).
- Dates, times, numbers and file sizes MUST be formatted according to the user's locale.
- Catalog display names are proper nouns and are not translated; descriptions, statuses and error
  messages are.
- A feature is not complete until all six locales contain its translations. Missing keys MUST fall
  back to English and MUST NOT cause a crash or display raw resource keys.

**Rationale**: Consistency with the apps being managed; an installer's error messages must be
understood by the user.

### VIII. Version Control

- Git is used for all work. Each spec is developed on its own branch named after the spec using
  Spec Kit numbering (e.g. `001-check-and-install`).
- A branch MAY merge into `main` only when all of the spec's tasks are complete and all tests pass;
  `main` MUST always build and pass tests.
- Commits MUST be small, focused and written in English, in Conventional Commits format
  (`feat: ...`, `fix: ...`, `docs: ...`).
- The repository MUST NEVER contain signing keys, keystore passwords, local IDE settings, build
  artifacts or downloaded APKs.

**Rationale**: One branch per spec keeps history traceable from requirement to code.

### IX. Distribution

- The app is distributed as a signed APK via GitHub Releases, with a SHA-256 published alongside
  it, using the same release pipeline conventions as the managed apps. Google Play-specific
  dependencies (Play Services, Play Billing, Play Core) MUST NOT be used.
- App versions follow Semantic Versioning; every release MUST ship with an English changelog.
- Release builds MUST be reproducible from a tagged commit (`vX.Y.Z`) built by CI. Signing keys and
  keystore passwords live only in CI secrets.
- The updater's own `applicationId` is `io.github.kolod.updater`.
- The updater MUST update itself: it is listed in its own catalog (repository `kolod/updater`, tag
  `vX.Y.Z`, asset `updater.apk`) and goes through exactly the same verified flow (Principle II).
  Its own update is installed last, after the other apps, because the system restarts the process
  when the updater is replaced.

**Rationale**: The updater is itself outside any store, so it follows the same transparent,
verifiable release path it enforces on the apps it manages.

## Technical Constraints

- Language: Kotlin. Target platform: Android, `minSdk` 26, `targetSdk` at the current stable level
  used by the managed apps (37 at ratification).
- UI: Jetpack Compose, portrait-first, following the orientation approach of the managed apps
  unless a spec decides otherwise.
- Permissions: `INTERNET` and `REQUEST_INSTALL_PACKAGES` (required to install APKs). Visibility of
  the managed apps MUST be obtained via explicit `<queries>` entries for the catalog's package
  names, NOT via `QUERY_ALL_PACKAGES`.
- Networking: GitHub REST API (`/repos/{owner}/{repo}/releases`), unauthenticated. The unauthenticated
  rate limit (60 requests/hour) MUST be respected: one request per managed app per check, the
  `ETag`/conditional request mechanism MAY be used, and a rate-limit response (HTTP 403/429) MUST
  stop further requests and be shown to the user with the reset time when provided.
- Retries: failed requests are retried a bounded number of times with exponential backoff; a user
  cancel stops everything immediately.
- Networking, hashing and package installation work MUST NEVER run on the UI thread.
- Architecture: a pure-Kotlin core (catalog, release model, version logic, verification) with no
  Android dependencies, plus a thin Android layer (HTTP/download, `PackageInstaller`, UI).

## Development Workflow & Quality Gates

1. `/speckit-specify` creates the spec on a numbered branch; it MUST list the data sent over the
   network, any new dependency and any new permission.
2. `/speckit-plan` MUST pass the Constitution Check; any violation is recorded with justification
   in the plan's Complexity Tracking table or the design is changed.
3. `/speckit-tasks` MUST include test-first tasks for the decision logic and translation tasks for
   all six locales.
4. Before merge: all tasks done, all tests green, no hardcoded strings, no new hosts contacted,
   no background scheduling code, all locales translated.
5. Before release: version bumped per SemVer, English changelog written, signed APK and its
   SHA-256 built by CI from a tagged commit.

## Governance

- This constitution supersedes all other practices. Every spec, plan and task list MUST be checked
  against it; violations MUST be explicitly justified in the plan's Complexity Tracking section or
  rejected. Principles I and II are NON-NEGOTIABLE and cannot be waived by justification.
- Amendments require a documented rationale, an update to this file (including the Sync Impact
  Report) and a version bump of the constitution:
  - MAJOR: removing or redefining a principle;
  - MINOR: adding a principle or section, or materially expanding guidance;
  - PATCH: clarifications and wording.
- After each amendment, the dependent templates (`.specify/templates/plan-template.md`,
  `spec-template.md`, `tasks-template.md`) MUST be updated to stay consistent.
- Compliance is reviewed at each Constitution Check during planning and again before every merge
  into `main`.

**Version**: 1.0.0 | **Ratified**: 2026-11-22 | **Last Amended**: 2026-11-22
