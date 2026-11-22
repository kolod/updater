package io.github.kolod.updater

import io.github.kolod.updater.core.catalog.Catalog
import io.github.kolod.updater.core.status.FailureReason
import io.github.kolod.updater.core.status.UpdateStatus
import io.github.kolod.updater.data.LastCheckStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UpdaterViewModelRecoveryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val source = FakeSource()
    private val reader = FakeReader()
    private lateinit var downloader: FakeDownloader
    private val inspector = FakeInspector()
    private lateinit var installer: FakeInstaller
    private var freeSpace = Long.MAX_VALUE

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        downloader = FakeDownloader(folder.newFolder("downloads"))
        installer = FakeInstaller(reader)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = UpdaterViewModel(
        catalog = Catalog.apps,
        source = source,
        reader = reader,
        store = LastCheckStore(File(folder.root, "last.json")),
        downloader = downloader,
        inspector = inspector,
        installer = installer,
        canInstall = { true },
        clock = { 1_000L },
        io = dispatcher,
        freeSpace = { freeSpace },
    )

    private fun UpdaterViewModel.row(id: String) = state.value.apps.first { it.app.id == id }

    private fun publishEverywhere(version: String = "2.0.0") {
        Catalog.apps.forEach {
            source.results[it.id] = FetchResult.Success(releaseOf(it, version))
            reader.installed[it.id] = installedOf(it, "1.0.0")
        }
    }

    @Test
    fun retryRechecksOnlyTheFailedApp() = runTest(dispatcher) {
        publishEverywhere()
        source.results["whiskergrid"] = FetchResult.Failure(FailureReason.Offline)
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()
        assertTrue(vm.row("whiskergrid").status is UpdateStatus.Failed)

        source.calls.clear()
        source.results["whiskergrid"] = FetchResult.Success(releaseOf(Catalog.byId("whiskergrid"), "2.0.0"))
        vm.retry("whiskergrid")
        advanceUntilIdle()
        assertEquals(listOf("whiskergrid"), source.calls)
        assertTrue(vm.row("whiskergrid").status is UpdateStatus.UpdateAvailable)
        assertEquals(Freshness.Fresh, vm.row("whiskergrid").freshness)
    }

    @Test
    fun retryOfAFailedInstallationRunsItAgain() = runTest(dispatcher) {
        publishEverywhere()
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()
        downloader.failures["whiskergrid"] = FailureReason.Offline
        vm.install("whiskergrid")
        advanceUntilIdle()
        assertEquals(InstallAttempt.Failed(FailureReason.Offline), vm.row("whiskergrid").attempt)

        downloader.failures.clear()
        vm.retry("whiskergrid")
        advanceUntilIdle()
        assertEquals(InstallAttempt.Installed, vm.row("whiskergrid").attempt)
    }

    @Test
    fun retryAfterCancelWorks() = runTest(dispatcher) {
        publishEverywhere()
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()
        downloader.gates["whiskergrid"] = CompletableDeferred()
        vm.install("whiskergrid")
        runCurrent()
        vm.cancelInstall("whiskergrid")
        advanceUntilIdle()
        assertEquals(InstallAttempt.Idle, vm.row("whiskergrid").attempt)

        downloader.gates.clear()
        vm.install("whiskergrid")
        advanceUntilIdle()
        assertEquals(InstallAttempt.Installed, vm.row("whiskergrid").attempt)
    }

    @Test
    fun rateLimitedRowsKeepTheResetTimeAndRetryAsksAgain() = runTest(dispatcher) {
        publishEverywhere()
        val limited = FailureReason.RateLimited(1795352754L)
        source.results["whiskergrid"] = FetchResult.Failure(limited)
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()
        assertEquals(UpdateStatus.Failed(limited), vm.row("circuitopedia").status)

        source.calls.clear()
        vm.retry("circuitopedia")
        advanceUntilIdle()
        assertEquals(listOf("circuitopedia"), source.calls)
        assertTrue(vm.row("circuitopedia").status is UpdateStatus.UpdateAvailable)
    }

    @Test
    fun aMissingInstallableFileAffectsOnlyThatApp() = runTest(dispatcher) {
        publishEverywhere()
        source.results["circuitopedia"] = FetchResult.Success(releaseOf(Catalog.byId("circuitopedia"), "3.0.0", withApk = false))
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()
        assertEquals(UpdateStatus.NoInstallableFile("3.0.0"), vm.row("circuitopedia").status)
        assertTrue(vm.row("whiskergrid").status is UpdateStatus.UpdateAvailable)
        // Nothing to install for the broken one.
        vm.install("circuitopedia")
        advanceUntilIdle()
        assertTrue(downloader.calls.isEmpty())
    }

    @Test
    fun notEnoughFreeSpaceStopsBeforeAnyDownload() = runTest(dispatcher) {
        publishEverywhere()
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()
        // The fake asset is 1000 bytes; 10 % margin means 1100 bytes are needed.
        freeSpace = 1_099L
        vm.install("whiskergrid")
        advanceUntilIdle()
        assertEquals(InstallAttempt.Failed(FailureReason.StorageFull), vm.row("whiskergrid").attempt)
        assertTrue(downloader.calls.isEmpty())

        freeSpace = 1_100L
        vm.retry("whiskergrid")
        advanceUntilIdle()
        assertEquals(InstallAttempt.Installed, vm.row("whiskergrid").attempt)
    }
}
