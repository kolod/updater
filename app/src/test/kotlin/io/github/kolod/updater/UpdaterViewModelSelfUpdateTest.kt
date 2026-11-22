package io.github.kolod.updater

import io.github.kolod.updater.core.catalog.Catalog
import io.github.kolod.updater.core.status.FailureReason
import io.github.kolod.updater.core.status.UpdateStatus
import io.github.kolod.updater.data.LastCheckStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class UpdaterViewModelSelfUpdateTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val source = FakeSource()
    private val reader = FakeReader()
    private lateinit var downloader: FakeDownloader
    private val inspector = FakeInspector()
    private lateinit var installer: FakeInstaller

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        downloader = FakeDownloader(folder.newFolder("downloads"))
        installer = FakeInstaller(reader)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** [catalog] is deliberately not in the shipped order, to prove the queue sorts the updater last. */
    private fun viewModel(catalog: List<io.github.kolod.updater.core.catalog.ManagedApp>) = UpdaterViewModel(
        catalog = catalog,
        source = source,
        reader = reader,
        store = LastCheckStore(File(folder.root, "last.json")),
        downloader = downloader,
        inspector = inspector,
        installer = installer,
        canInstall = { true },
        clock = { 1_000L },
        io = dispatcher,
    )

    private fun outdatedEverywhere() {
        Catalog.apps.forEach {
            source.results[it.id] = FetchResult.Success(releaseOf(it, "2.0.0"))
            reader.installed[it.id] = installedOf(it, "1.0.0")
        }
    }

    @Test
    fun theUpdaterGoesThroughTheSameVerifiedPathAsEveryOtherApp() = runTest(dispatcher) {
        outdatedEverywhere()
        val vm = viewModel(Catalog.apps)
        vm.check()
        advanceUntilIdle()
        assertTrue(vm.state.value.apps.first { it.app.isSelf }.status is UpdateStatus.UpdateAvailable)

        // A wrong checksum is refused for the updater exactly as for any other app.
        downloader.publishedSha = listOf("b".repeat(64))
        vm.install("updater")
        advanceUntilIdle()
        assertTrue(installer.calls.isEmpty())
        assertEquals(
            InstallAttempt.Failed(FailureReason.ChecksumMismatch),
            vm.state.value.apps.first { it.app.isSelf }.attempt,
        )
    }

    @Test
    fun updateAllInstallsTheUpdaterLastWhateverTheCatalogOrder() = runTest(dispatcher) {
        outdatedEverywhere()
        val reordered = listOf(Catalog.self) + Catalog.apps.filterNot { it.isSelf }
        val vm = viewModel(reordered)
        vm.check()
        advanceUntilIdle()
        vm.updateAll()
        advanceUntilIdle()
        assertEquals("updater", downloader.calls.last())
        assertEquals(5, downloader.calls.size)
    }

    @Test
    fun aFailureOfAnotherAppDoesNotSkipTheSelfUpdate() = runTest(dispatcher) {
        outdatedEverywhere()
        downloader.failures["whiskergrid"] = FailureReason.Offline
        val vm = viewModel(Catalog.apps)
        vm.check()
        advanceUntilIdle()
        vm.updateAll()
        advanceUntilIdle()
        assertEquals(InstallAttempt.Failed(FailureReason.Offline), vm.state.value.apps.first { it.app.id == "whiskergrid" }.attempt)
        assertEquals(InstallAttempt.Installed, vm.state.value.apps.first { it.app.isSelf }.attempt)
    }

    @Test
    fun afterTheSelfUpdateTheNextCheckShowsTheNewVersion() = runTest(dispatcher) {
        outdatedEverywhere()
        val vm = viewModel(Catalog.apps)
        vm.check()
        advanceUntilIdle()
        vm.install("updater")
        advanceUntilIdle()
        // The fake installer updated the installed version, as the system does on a real restart.
        vm.check()
        advanceUntilIdle()
        val status = vm.state.value.apps.first { it.app.isSelf }.status
        assertEquals(UpdateStatus.UpToDate("2.0.0", "2.0.0"), status)
    }
}
