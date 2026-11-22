# Feature Specification: Check and Install Updates

**Feature Branch**: `001-check-and-install-updates`

**Created**: 2026-11-22

**Status**: Draft

**Input**: User description: "перевірка оновлень і встановлення" — an app that checks for new versions of the owner's Android apps (Ghost BMS, Whisker Grid, Hentai Reader, Circuitopedia) and installs them; checks happen when the app is launched manually and/or via a button in the app, never on a schedule. The updater also updates itself.

## Clarifications

### Session 2026-11-22

- Q: Should Hentai Reader be checked on GitHub while it is not installed? → A: No. Hentai Reader is an optional app: it is listed, checked and updated only while it is installed on the device. While it is not installed it has no row and no network request is made for it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See which apps have updates (Priority: P1)

The user opens the updater. It immediately checks all managed apps that apply (optional apps count only while installed) and shows one row per app: its name, the installed version (or "not installed"), the latest published version, and a status — up to date, update available, not installed, or check failed. The user can press a "Check for updates" button at any time to repeat the check.

**Why this priority**: Knowing what is outdated is the core value and is useful on its own, even before installation works.

**Independent Test**: With some managed apps installed at older versions, open the updater and verify that every app row shows the correct installed version, latest version and status; press the button and verify the check repeats and the "last checked" time updates.

**Acceptance Scenarios**:

1. **Given** an installed app is older than its latest release, **When** the user opens the updater, **Then** the app's row shows "update available" with both versions.
2. **Given** an installed app has the latest version, **When** a check completes, **Then** its row shows "up to date".
3. **Given** a managed app that is not optional is not installed, **When** a check completes, **Then** its row shows "not installed" with the latest available version.
7. **Given** Hentai Reader is not installed, **When** a check runs, **Then** it has no row, is not counted anywhere, and no request about it is made. **Given** it is installed, **Then** it appears and is checked like any other app.
4. **Given** the device is offline, **When** a check runs, **Then** every row shows "check failed" with a clear reason, and the previous known versions remain visible, marked as not fresh.
5. **Given** the updater is not open, **When** time passes, **Then** no check, download or notification is ever triggered by the updater.
6. **Given** a check is in progress, **When** the user cancels it, **Then** it stops promptly and rows keep their previous state.

---

### User Story 2 - Install an update or a missing app (Priority: P1)

For an app with "update available" or "not installed", the user taps "Install" (or "Update"). The updater downloads the new version, shows progress, verifies it, and hands it to the system installer, where the user confirms. When the installation finishes, the row refreshes to show the new version as up to date.

**Why this priority**: Installing is the second half of the feature's purpose; together with Story 1 it forms the complete MVP.

**Independent Test**: With one app outdated, tap "Update", confirm in the system dialog, and verify the app is now at the latest version and the row shows "up to date"; no leftover downloaded file remains.

**Acceptance Scenarios**:

1. **Given** an update is available, **When** the user taps "Update", **Then** a download with visible progress starts, and on completion the system installation confirmation appears.
2. **Given** the download finished and verification passed, **When** the user confirms in the system dialog, **Then** the app is updated and its row shows the new version as up to date.
3. **Given** the user declines the system confirmation, **When** the dialog closes, **Then** the row returns to "update available", the downloaded file is deleted and no error is shown as a failure.
4. **Given** a download is in progress, **When** the user cancels it, **Then** it stops and the partial file is deleted.
5. **Given** the user has not yet allowed this app to install other apps, **When** they tap "Update", **Then** they are guided to the system setting and, after granting it, can continue without restarting the flow.
6. **Given** several apps have updates, **When** the user taps "Update all", **Then** the apps are installed one after another, each requiring its own system confirmation, and a failure in one does not prevent the others.

---

### User Story 3 - Trust and safety of what gets installed (Priority: P1)

The user must be confident that only the genuine, exact build published by the owner is installed. Before handing a file to the system installer, the updater verifies it. If anything is wrong, the file is discarded and the user is told why in plain language.

**Why this priority**: Per the constitution (Principle II) this is non-negotiable; an updater that cannot guarantee this must not install anything.

**Independent Test**: Feed the updater a release whose checksum does not match, one whose package name differs, and one whose version is not newer; verify each is rejected before the system installer is invoked and the file is deleted.

**Acceptance Scenarios**:

1. **Given** a release publishes a SHA-256 checksum, **When** the downloaded file's checksum differs, **Then** installation is refused, the file is deleted, and the user sees a "file is corrupted or tampered with" message.
2. **Given** the downloaded file belongs to a different app than the catalog entry, **When** verification runs, **Then** installation is refused and the file is deleted.
3. **Given** the latest release version is equal to or older than the installed version, **When** a check runs, **Then** the app is shown as up to date and no install action is offered.
4. **Given** the system rejects the update because it is signed with a different key than the installed app, **When** the failure is reported, **Then** the user sees an explanation and no automatic uninstall is attempted.
5. **Given** a release is a draft or pre-release, **When** a check runs, **Then** it is ignored.

---

### User Story 4 - Updater updates itself (Priority: P2)

The updater appears in its own list like any other app. When a new version of the updater is available, the user can install it through the same verified flow. During "Update all" the updater's own update is installed last.

**Why this priority**: Required by the owner but depends on Stories 1–3 working; delivers value only after the main flow exists.

**Independent Test**: Publish a newer updater release, press check, verify the updater row shows "update available", install it, and verify the app restarts at the new version with the list intact.

**Acceptance Scenarios**:

1. **Given** a newer updater release exists, **When** the check completes, **Then** the updater's own row shows "update available".
2. **Given** "Update all" is used and the updater itself is outdated, **When** the other updates finish, **Then** the updater's own update is requested last.

---

### User Story 5 - Understand and recover from problems (Priority: P3)

When a check or installation fails, the user sees which app failed and why (no connection, GitHub request limit reached with the time when it resets, release has no suitable file, corrupted file, storage full), and can retry that single app.

**Why this priority**: Improves robustness and trust; the core flow works without it, but failures are common on mobile networks.

**Independent Test**: Simulate offline, rate-limited, and missing-file conditions; verify each shows its own message and that a retry on one app does not repeat work for the others.

**Acceptance Scenarios**:

1. **Given** GitHub's request limit is reached, **When** a check runs, **Then** the user is told the limit was reached and when checking works again; no further requests are made until the user retries.
2. **Given** a latest release has no file with the expected name, **When** the check completes, **Then** that app shows "no installable file in the latest release" and other apps are unaffected.
3. **Given** one app's check failed, **When** the user taps retry on its row, **Then** only that app is re-checked.

---

### Edge Cases

- The repository contains releases for other platforms (e.g. Windows releases of Hentai Reader): they are ignored; only releases that match the app's release naming are considered.
- A repository has no releases at all, or only pre-releases: the app shows "no release available".
- Installed version string is a development/pre-release version (e.g. `0.1.0-dev`): it is considered older than the same version released.
- The installed version is newer than the latest release (e.g. a local build): shown as up to date, never offered as a downgrade.
- The app is closed or the device restarts in the middle of a download: the partial file is discarded; the next launch shows a clean state.
- Not enough free storage for the download: the user is told before or when the download fails, and no partial file remains.
- The user switches network type mid-download: the download either resumes safely or fails cleanly with a retry option; it never installs a partial file.
- Hentai Reader is installed or uninstalled by other means while the updater is open: the list changes at the next check or launch, not before.
- A managed app is installed but not visible to the updater (system restriction): it is treated as "not installed" only if the system confirms it is absent; otherwise the row shows an explicit problem rather than offering a misleading install.
- The updater is opened repeatedly in quick succession: a check already in progress is not duplicated.
- The latest release of an app changes between the check and the download: the version that was verified is the one installed, or the check is repeated; never an unverified mix.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST manage a fixed catalog of apps: Ghost BMS (unofficial), Whisker Grid, Hentai Reader, Circuitopedia and the updater itself, each with its source repository, package identity, release naming rule and installable file name. Hentai Reader is marked optional (see FR-021).
- **FR-002**: The system MUST run an update check automatically when the user manually opens the app, and when the user presses a "Check for updates" button; it MUST NOT check, download or notify at any other time, including on a schedule, at device start or in the background.
- **FR-003**: For each listed catalog app, the system MUST show the installed version (or "not installed"), the latest available version, and one of the statuses: up to date, update available, not installed, check failed, no release available. The status "not installed" applies only to apps that are not optional.
- **FR-004**: The system MUST determine the latest version only from the owner's published releases, ignoring drafts, pre-releases and releases that do not match the app's release naming rule.
- **FR-005**: The system MUST compare versions by semantic-versioning rules, including pre-release ordering, and MUST offer an install action only when the latest version is strictly newer than the installed one, or the app is not installed.
- **FR-006**: The system MUST show when the last check completed, and MUST indicate that displayed data from a previous check is not fresh when a new check fails.
- **FR-007**: A failed check for one app MUST NOT prevent checking or updating other apps, and each failure MUST be shown on the affected app with a localized, specific reason.
- **FR-008**: Users MUST be able to cancel a running check or download at any time.
- **FR-009**: Users MUST be able to install or update a single app, and to update all outdated apps in one action; with "Update all", the updater's own update MUST be installed last.
- **FR-010**: The system MUST show download progress for each active download and prevent starting a duplicate download or check for the same app.
- **FR-011**: Before requesting installation, the system MUST verify the downloaded file: its checksum MUST match the published checksum when one is available, its package identity MUST match the catalog entry, and its version MUST be newer than the installed version. On any failure it MUST refuse installation, delete the file and tell the user why.
- **FR-012**: Installation MUST go through the operating system's standard installation confirmation; the system MUST NOT install silently or bypass the confirmation.
- **FR-013**: When an update is rejected because the signing key differs from the installed app, the system MUST explain this to the user and MUST NOT uninstall the existing app automatically.
- **FR-014**: The system MUST delete every downloaded file after installation, failure, decline or cancellation, and MUST NOT leave partial files after an interrupted download.
- **FR-015**: If the user has not allowed the updater to install apps, the system MUST guide them to the relevant system setting and let them continue after granting it.
- **FR-016**: After an installation attempt, the system MUST refresh the affected app's row to reflect the actual installed version.
- **FR-017**: The system MUST respect the request limits of the release source: it MUST make at most one metadata request per app per check, stop further requests when the limit is reached, and tell the user when checking becomes available again.
- **FR-018**: The system MUST communicate only with the release source of the catalog apps, MUST send no account, device or installed-app information, and MUST include no ads, analytics or tracking.
- **FR-019**: All user-facing text MUST be available in English, German, French, Spanish, Japanese and Ukrainian, with English as fallback; dates, times, numbers and file sizes MUST follow the user's locale.
- **FR-020**: The catalog MUST be fixed inside the app; it MUST NOT be changed by any remote source or by user input in this version.
- **FR-021**: An optional catalog app (Hentai Reader) MUST be listed, checked and offered for update only while it is installed on the device. While it is not installed, the system MUST NOT list it, count it (including in "N updates available"), offer to install it (also not through "Update all"), or make any network request about it. When it is installed or uninstalled by other means, the list MUST reflect that at the next check or launch.

### Key Entities

- **Managed App**: One entry of the catalog — display name, source repository, package identity, release naming rule, installable file name, optional checksum file name, and whether the app is optional (listed only while installed). Fixed per app version.
- **Installed Version**: The version of a managed app currently installed on the device, or absence of it. Read from the device on every check.
- **Release**: A published version of a managed app — version, whether it is a draft/pre-release, the installable file and its size, and its checksum if published.
- **Update Status**: The outcome of a check for one managed app (up to date, update available, not installed, check failed with reason, no release available), plus the time it was determined.
- **Install Attempt**: One download-verify-install run for one app — progress, verification result, and final outcome (installed, declined by user, failed with reason, cancelled).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After opening the app on a working connection, the user sees the status of every listed catalog app (five, or four while Hentai Reader is not installed) in under 10 seconds.
- **SC-002**: A user can update one outdated app from opening the updater to the app running its new version in no more than 4 taps (open → update → system confirm → done), excluding the one-time permission grant.
- **SC-003**: In 100% of tested cases with a wrong checksum, wrong package identity, or non-newer version, the system installer is never invoked and the downloaded file is deleted.
- **SC-004**: Over a 24-hour period with the updater never opened, it performs zero network requests and shows zero notifications.
- **SC-005**: A failure for one app (offline, missing file, rate limit) never changes the displayed status of any other app that succeeded in the same check.
- **SC-006**: After any completed, declined, failed or cancelled installation, zero downloaded files remain on the device.
- **SC-007**: 100% of user-visible texts introduced by this feature exist in all six supported languages.
- **SC-008**: Version comparison gives the correct result for 100% of a test set that includes pre-release, equal, lower, higher and other-platform release cases.
- **SC-009**: While Hentai Reader is not installed, 100% of checks show no row for it and send zero requests about its repository.

## Assumptions

- The user is the owner of the managed apps and installs them from GitHub Releases; all managed apps are signed with the same key across versions, so system updates work.
- Release naming follows what the repositories use today: plain version tags for Ghost BMS, Whisker Grid and Circuitopedia; an Android-specific tag prefix for Hentai Reader (whose repository also holds Windows releases); a fixed installable file name per app. Only Hentai Reader currently publishes a separate checksum file; where none is published, verification relies on package identity, version checks and the system's signature check.
- Where the release source reports a checksum for an installable file, that checksum is treated as a published checksum.
- The updater's own release is published in its own repository with an installable file named `updater.apk`, using plain version tags.
- Checking happens at manual launch (cold start or coming to the foreground from a manual launch) and on the button; it does not repeat when the user merely returns to an already-open updater.
- Downloads and checks run only while the updater is visible; leaving the app cancels an in-progress check, and an in-progress download is cancelled or fails cleanly.
- Android 8.0 (API 26) or newer, matching the managed apps.
- Out of scope for this version: scheduled or background checks, notifications, silent/unattended installation, uninstalling apps, release notes display, pre-release opt-in, user-editable catalog, authentication with the release source, and non-Android apps.
