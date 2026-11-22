package io.github.kolod.updater

import android.app.Application
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.kolod.updater.core.catalog.Catalog
import io.github.kolod.updater.core.catalog.ManagedApp
import io.github.kolod.updater.core.release.Release
import io.github.kolod.updater.core.status.FailureReason
import io.github.kolod.updater.core.status.InstalledApp
import io.github.kolod.updater.core.status.UpdateStatus
import io.github.kolod.updater.core.status.resolveStatus
import io.github.kolod.updater.core.verify.VerifyResult
import io.github.kolod.updater.core.verify.checkDownloadedApk
import io.github.kolod.updater.core.version.SemVer
import io.github.kolod.updater.data.DownloadFiles
import io.github.kolod.updater.data.GitHubReleaseSource
import io.github.kolod.updater.data.HttpApkDownloader
import io.github.kolod.updater.data.LastCheckStore
import io.github.kolod.updater.device.PackageArchiveInspector
import io.github.kolod.updater.device.PackageManagerAppReader
import io.github.kolod.updater.device.SessionInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Whether a row's data comes from a check made in this session. */
enum class Freshness { Fresh, Stale }

/** Progress of one download-verify-install run for one app (data-model.md, InstallAttempt). */
sealed interface InstallAttempt {
    data object Idle : InstallAttempt
    data object Queued : InstallAttempt
    data class Downloading(val done: Long, val total: Long) : InstallAttempt
    data object Verifying : InstallAttempt
    data object AwaitingConfirmation : InstallAttempt
    data object Installed : InstallAttempt
    data class Failed(val reason: FailureReason) : InstallAttempt

    /** True while the app is part of a running or waiting installation. */
    val isBusy: Boolean
        get() = this is Queued || this is Downloading || this is Verifying || this is AwaitingConfirmation
}

data class AppState(
    val app: ManagedApp,
    val installed: InstalledApp?,
    /** Null until a check has run in this session. */
    val status: UpdateStatus?,
    /** Latest version seen by any earlier check, kept visible when the current check fails. */
    val latestKnown: String?,
    val freshness: Freshness,
    val checkedAt: Long?,
    val attempt: InstallAttempt = InstallAttempt.Idle,
)

data class UpdaterUiState(
    val apps: List<AppState>,
    val isChecking: Boolean = false,
    val lastCheckedAt: Long? = null,
    /** The screen should send the user to the "install unknown apps" setting. */
    val permissionRequested: Boolean = false,
) {
    val updatesAvailable: Int get() = apps.count { it.status is UpdateStatus.UpdateAvailable }
    val anyBusy: Boolean get() = apps.any { it.attempt.isBusy }
}

/**
 * Holds the screen state. Checks happen only when [check] is called: from the activity on a fresh
 * launch and from the button (constitution, Principle III). Installations run one at a time and
 * only start while the app is visible. [io] runs the blocking package-manager and file work.
 */
class UpdaterViewModel(
    private val catalog: List<ManagedApp>,
    private val source: ReleaseSource,
    private val reader: InstalledAppReader,
    private val store: LastCheckStore,
    private val downloader: ApkDownloader,
    private val inspector: ApkInspector,
    private val installer: ApkInstaller,
    private val canInstall: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val startupCleanup: () -> Unit = {},
    /** Free bytes in the download directory; checked before a download starts. */
    private val freeSpace: () -> Long = { Long.MAX_VALUE },
) : ViewModel() {

    private val _state = MutableStateFlow(UpdaterUiState(apps = catalog.map { unknownRow(it, null) }))
    val state: StateFlow<UpdaterUiState> = _state.asStateFlow()

    private var checkJob: Job? = null

    private val queue = ArrayDeque<String>()
    private var worker: Job? = null
    private var activeJob: Job? = null
    private var activeId: String? = null
    private var waitingForPermission = false
    private val visible = MutableStateFlow(true)
    private val rechecking = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            withContext(io) { runCatching(startupCleanup) }
            restore()
        }
    }

    private fun unknownRow(app: ManagedApp, installed: InstalledApp?) = AppState(
        app = app,
        installed = installed,
        status = null,
        latestKnown = null,
        freshness = Freshness.Stale,
        checkedAt = null,
    )

    private suspend fun restore() {
        val snapshot = withContext(io) { store.load() }
        val installed = withContext(io) { catalog.associate { it.id to reader.read(it) } }
        _state.update { current ->
            current.copy(
                apps = current.apps.map { row ->
                    val entry = snapshot[row.app.id]
                    // A check that already finished in this session wins over the snapshot.
                    if (row.status != null) row
                    else row.copy(installed = installed[row.app.id], latestKnown = entry?.latest, checkedAt = entry?.checkedAt)
                },
            )
        }
    }

    // ---- Checking ---------------------------------------------------------------------------

    /** Checks all apps in parallel. Ignored while a check is already running. */
    fun check() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            _state.update { state ->
                state.copy(
                    isChecking = true,
                    apps = state.apps.map {
                        if (it.attempt is InstallAttempt.Failed || it.attempt is InstallAttempt.Installed) {
                            it.copy(attempt = InstallAttempt.Idle)
                        } else {
                            it
                        }
                    },
                )
            }
            var completed = false
            try {
                val rateLimit = AtomicReference<FailureReason.RateLimited?>(null)
                coroutineScope {
                    catalog.map { app -> async { checkOne(app, rateLimit) } }.awaitAll()
                }
                completed = true
            } finally {
                _state.update { it.copy(isChecking = false) }
            }
            if (completed) {
                val now = clock()
                _state.update { it.copy(lastCheckedAt = now) }
                persist()
            }
        }
    }

    /** Cancels a running check; rows keep their previous state. */
    fun cancel() {
        checkJob?.cancel()
    }

    private suspend fun checkOne(app: ManagedApp, rateLimit: AtomicReference<FailureReason.RateLimited?>) {
        val installed = withContext(io) { reader.read(app) }
        val limited = rateLimit.get()
        val result = if (limited != null) FetchResult.Failure(limited) else source.fetch(app)
        if (result is FetchResult.Failure && result.reason is FailureReason.RateLimited) {
            rateLimit.compareAndSet(null, result.reason)
        }
        val now = clock()
        updateRow(app.id) { old ->
            when (result) {
                is FetchResult.Success -> {
                    val status = resolveStatus(app, installed, result.release)
                    old.copy(
                        installed = installed,
                        status = status,
                        latestKnown = latestOf(status) ?: old.latestKnown,
                        freshness = Freshness.Fresh,
                        checkedAt = now,
                    )
                }
                is FetchResult.Failure -> old.copy(
                    installed = installed,
                    status = UpdateStatus.Failed(result.reason),
                    freshness = Freshness.Stale,
                )
            }
        }
    }

    private fun latestOf(status: UpdateStatus): String? = when (status) {
        is UpdateStatus.UpToDate -> status.latest
        is UpdateStatus.UpdateAvailable -> status.release.version.toString()
        is UpdateStatus.NotInstalled -> status.release.version.toString()
        is UpdateStatus.NoInstallableFile -> status.latest
        UpdateStatus.NoRelease, is UpdateStatus.Failed -> null
    }

    /** Repeats only what failed for one app: its check, or its installation. */
    fun retry(appId: String) {
        val row = _state.value.apps.firstOrNull { it.app.id == appId } ?: return
        when {
            row.attempt is InstallAttempt.Failed -> install(appId)
            row.status is UpdateStatus.Failed -> recheck(row.app)
        }
    }

    private fun recheck(app: ManagedApp) {
        if (checkJob?.isActive == true || !rechecking.add(app.id)) return
        viewModelScope.launch {
            try {
                checkOne(app, AtomicReference(null))
                persist()
            } finally {
                rechecking.remove(app.id)
            }
        }
    }

    // ---- Installing -------------------------------------------------------------------------

    fun install(appId: String) = enqueue(listOf(appId))

    /** Updates every outdated installed app, one after another, the updater itself last. */
    fun updateAll() {
        val ids = _state.value.apps
            .filter { it.status is UpdateStatus.UpdateAvailable }
            .sortedBy { it.app.isSelf }
            .map { it.app.id }
        enqueue(ids)
    }

    /** Cancels the download/installation of one app, or removes it from the queue. */
    fun cancelInstall(appId: String) {
        if (activeId == appId) {
            activeJob?.cancel()
        } else if (queue.remove(appId)) {
            setAttempt(appId, InstallAttempt.Idle)
        }
    }

    /** The screen has started the "install unknown apps" settings page. */
    fun permissionPromptShown() {
        _state.update { it.copy(permissionRequested = false) }
    }

    /** The user came back from the "install unknown apps" settings page. */
    fun onInstallPermissionResult() {
        if (!waitingForPermission) return
        waitingForPermission = false
        if (canInstall()) {
            ensureWorker()
        } else {
            queue.forEach { setAttempt(it, InstallAttempt.Failed(FailureReason.NotAllowedToInstall)) }
            queue.clear()
        }
    }

    /** The app came to the foreground; queued installations may start. */
    fun onEnteredApp() {
        visible.value = true
    }

    /**
     * The app left the foreground. Checks and downloads stop (they only run while visible); an
     * installation already waiting for the system confirmation, and the queue behind it, survive.
     */
    fun onLeftApp() {
        visible.value = false
        checkJob?.cancel()
        val attempt = activeId?.let { id -> _state.value.apps.first { it.app.id == id }.attempt }
        if (attempt is InstallAttempt.Downloading || attempt is InstallAttempt.Verifying) {
            activeJob?.cancel()
            queue.toList().forEach { setAttempt(it, InstallAttempt.Idle) }
            queue.clear()
        }
    }

    private fun enqueue(ids: List<String>) {
        var added = false
        for (id in ids) {
            val row = _state.value.apps.firstOrNull { it.app.id == id } ?: continue
            if (row.attempt.isBusy) continue
            if (row.status !is UpdateStatus.UpdateAvailable && row.status !is UpdateStatus.NotInstalled) continue
            queue.addLast(id)
            setAttempt(id, InstallAttempt.Queued)
            added = true
        }
        if (added) ensureWorker()
    }

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        if (!canInstall()) {
            waitingForPermission = true
            _state.update { it.copy(permissionRequested = true) }
            return
        }
        worker = viewModelScope.launch {
            while (true) {
                visible.first { it }
                val id = queue.removeFirstOrNull() ?: break
                coroutineScope {
                    val job = launch { process(id) }
                    activeId = id
                    activeJob = job
                    job.join()
                }
                activeId = null
                activeJob = null
            }
        }
    }

    private suspend fun process(id: String) {
        val row = _state.value.apps.first { it.app.id == id }
        val app = row.app
        val release = when (val status = row.status) {
            is UpdateStatus.UpdateAvailable -> status.release
            is UpdateStatus.NotInstalled -> status.release
            else -> {
                setAttempt(id, InstallAttempt.Idle)
                return
            }
        }
        var file: File? = null
        try {
            val size = release.apk?.size ?: 0
            // Fail early and clearly instead of filling the disk with a download that cannot finish.
            if (size > 0 && freeSpace() < size + size / 10) {
                fail(id, FailureReason.StorageFull)
                return
            }
            setAttempt(id, InstallAttempt.Downloading(0, size))
            val download = downloader.download(app, release) { done, total ->
                setAttempt(id, InstallAttempt.Downloading(done, total))
            }
            val done = download as? DownloadResult.Done
            if (done == null) {
                fail(id, (download as DownloadResult.Failed).reason)
                return
            }
            file = done.file

            setAttempt(id, InstallAttempt.Verifying)
            val facts = withContext(io) { inspector.inspect(done.file) }
            if (facts == null) {
                fail(id, FailureReason.BadResponse)
                return
            }
            val installedNow = withContext(io) { reader.read(app) }
            val verdict = checkDownloadedApk(app, release, facts, installedNow, done.computedSha256, done.publishedSha256)
            if (verdict is VerifyResult.Fail) {
                fail(id, verdict.reason)
                return
            }

            setAttempt(id, InstallAttempt.AwaitingConfirmation)
            when (val result = installer.install(done.file, app)) {
                InstallResult.Installed -> {
                    // Trust the device, not the callback: the installed version must really be the new one.
                    if (refreshAfterInstall(app, release)) {
                        setAttempt(id, InstallAttempt.Installed)
                    } else {
                        fail(id, FailureReason.InstallerRejected(1))
                    }
                }
                InstallResult.Declined -> setAttempt(id, InstallAttempt.Idle)
                is InstallResult.Failed -> fail(id, result.reason)
            }
        } catch (e: CancellationException) {
            setAttempt(id, InstallAttempt.Idle)
            throw e
        } catch (e: Exception) {
            fail(id, FailureReason.BadResponse)
        } finally {
            // Every outcome ends here: the downloaded file never outlives the attempt (FR-014).
            file?.let { withContext(NonCancellable + io) { it.delete() } }
        }
    }

    /** Re-reads the device and returns whether the installed version now matches [release]. */
    private suspend fun refreshAfterInstall(app: ManagedApp, release: Release): Boolean {
        val installed = withContext(NonCancellable + io) { reader.read(app) }
        updateRow(app.id) { it.copy(installed = installed, status = resolveStatus(app, installed, release)) }
        val version = installed?.versionName?.let { SemVer.parse(it) }
        return version != null && version >= release.version
    }

    private fun fail(id: String, reason: FailureReason) {
        setAttempt(id, if (reason == FailureReason.Cancelled) InstallAttempt.Idle else InstallAttempt.Failed(reason))
    }

    private fun setAttempt(id: String, attempt: InstallAttempt) = updateRow(id) { it.copy(attempt = attempt) }

    // ---- Shared helpers ---------------------------------------------------------------------

    private fun updateRow(id: String, transform: (AppState) -> AppState) {
        _state.update { current ->
            current.copy(apps = current.apps.map { if (it.app.id == id) transform(it) else it })
        }
    }

    private suspend fun persist() {
        val entries = _state.value.apps
            .filter { it.checkedAt != null }
            .associate { it.app.id to LastCheckStore.Entry(it.latestKnown, it.checkedAt!!) }
        withContext(io) { runCatching { store.save(entries) } }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val files = DownloadFiles(File(application.cacheDir, "downloads"))
                UpdaterViewModel(
                    catalog = Catalog.apps,
                    source = GitHubReleaseSource(),
                    reader = PackageManagerAppReader(application),
                    store = LastCheckStore(File(application.filesDir, "last-check.json")),
                    downloader = HttpApkDownloader(files),
                    inspector = PackageArchiveInspector(application),
                    installer = SessionInstaller(application),
                    canInstall = { application.packageManager.canRequestPackageInstalls() },
                    startupCleanup = files::wipeAll,
                    freeSpace = { StatFs(application.cacheDir.path).availableBytes },
                )
            }
        }
    }
}
