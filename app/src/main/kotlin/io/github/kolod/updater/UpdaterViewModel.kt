package io.github.kolod.updater

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.kolod.updater.core.catalog.Catalog
import io.github.kolod.updater.core.catalog.ManagedApp
import io.github.kolod.updater.core.status.FailureReason
import io.github.kolod.updater.core.status.InstalledApp
import io.github.kolod.updater.core.status.UpdateStatus
import io.github.kolod.updater.core.status.resolveStatus
import io.github.kolod.updater.data.GitHubReleaseSource
import io.github.kolod.updater.data.LastCheckStore
import io.github.kolod.updater.device.PackageManagerAppReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Whether a row's data comes from a check made in this session. */
enum class Freshness { Fresh, Stale }

data class AppState(
    val app: ManagedApp,
    val installed: InstalledApp?,
    /** Null until a check has run in this session. */
    val status: UpdateStatus?,
    /** Latest version seen by any earlier check, kept visible when the current check fails. */
    val latestKnown: String?,
    val freshness: Freshness,
    val checkedAt: Long?,
)

data class UpdaterUiState(
    val apps: List<AppState>,
    val isChecking: Boolean = false,
    val lastCheckedAt: Long? = null,
) {
    val updatesAvailable: Int get() = apps.count { it.status is UpdateStatus.UpdateAvailable }
}

/**
 * Holds the screen state. Checks happen only when [check] is called: from the activity on a fresh
 * launch and from the button (constitution, Principle III). [io] runs the blocking package-manager
 * and file work.
 */
class UpdaterViewModel(
    private val catalog: List<ManagedApp>,
    private val source: ReleaseSource,
    private val reader: InstalledAppReader,
    private val store: LastCheckStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdaterUiState(apps = catalog.map { unknownRow(it, null) }))
    val state: StateFlow<UpdaterUiState> = _state.asStateFlow()

    private var checkJob: Job? = null

    init {
        viewModelScope.launch { restore() }
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

    /** Checks all apps in parallel. Ignored while a check is already running. */
    fun check() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            _state.update { it.copy(isChecking = true) }
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
                UpdaterViewModel(
                    catalog = Catalog.apps,
                    source = GitHubReleaseSource(),
                    reader = PackageManagerAppReader(application),
                    store = LastCheckStore(File(application.filesDir, "last-check.json")),
                )
            }
        }
    }
}
