package io.github.kolod.updater

import io.github.kolod.updater.core.catalog.Catalog
import io.github.kolod.updater.core.status.FailureReason
import io.github.kolod.updater.core.status.UpdateStatus
import io.github.kolod.updater.core.verify.ApkFacts
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UpdaterViewModelInstallTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val source = FakeSource()
    private val reader = FakeReader()
    private lateinit var downloader: FakeDownloader
    private val inspector = FakeInspector()
    private lateinit var installer: FakeInstaller
    private var canInstall = true

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
        canInstall = { canInstall },
        clock = { 1_000L },
        io = dispatcher,
    )

    private fun UpdaterViewModel.row(id: String) = state.value.apps.first { it.app.id == id }

    /** Installed 1.0.0 everywhere, 2.0.0 published everywhere; then a completed check. */
    private suspend fun kotlinx.coroutines.test.TestScope.checkedViewModel(installed: Set<String> = Catalog.apps.map { it.id }.toSet()): UpdaterViewModel {
        Catalog.apps.forEach {
            source.results[it.id] = FetchResult.Success(releaseOf(it, "2.0.0"))
            if (it.id in installed) reader.installed[it.id] = installedOf(it, "1.0.0")
        }
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()
        return vm
    }

    private fun assertNoLeftoverFiles() = assertTrue(downloader.created.none { it.exists() })

    @Test
    fun happyPathInstallsAndRefreshesTheRow() = runTest(dispatcher) {
        val vm = checkedViewModel()
        vm.install("whiskergrid")
        advanceUntilIdle()
        assertEquals(listOf("whiskergrid"), installer.calls)
        assertEquals(InstallAttempt.Installed, vm.row("whiskergrid").attempt)
        assertEquals("2.0.0", vm.row("whiskergrid").installed?.versionName)
        assertTrue(vm.row("whiskergrid").status is UpdateStatus.UpToDate)
        assertNoLeftoverFiles()
    }

    @Test
    fun anInstalledResultIsOnlyTrustedWhenTheDeviceShowsTheNewVersion() = runTest(dispatcher) {
        val vm = checkedViewModel()
        installer.updatesDevice = false
        vm.install("whiskergrid")
        advanceUntilIdle()
        assertTrue(vm.row("whiskergrid").attempt is InstallAttempt.Failed)
        assertTrue(vm.row("whiskergrid").status is UpdateStatus.UpdateAvailable)
        assertNoLeftoverFiles()
    }

    @Test
    fun anAppThatIsNotInstalledCanBeInstalled() = runTest(dispatcher) {
        val vm = checkedViewModel(installed = emptySet())
        assertTrue(vm.row("circuitopedia").status is UpdateStatus.NotInstalled)
        vm.install("circuitopedia")
        advanceUntilIdle()
        assertEquals(InstallAttempt.Installed, vm.row("circuitopedia").attempt)
        assertEquals("2.0.0", vm.row("circuitopedia").installed?.versionName)
    }

    @Test
    fun failedVerificationNeverReachesTheInstaller() = runTest(dispatcher) {
        val vm = checkedViewModel()
        inspector.overrides["whiskergrid"] = ApkFacts("evil.app", "2.0.0", 100, setOf("sig"))
        vm.install("whiskergrid")
        advanceUntilIdle()
        assertTrue(installer.calls.isEmpty())
        assertEquals(InstallAttempt.Failed(FailureReason.WrongPackage), vm.row("whiskergrid").attempt)
        assertNoLeftoverFiles()
    }

    @Test
    fun checksumMismatchNeverReachesTheInstaller() = runTest(dispatcher) {
        val vm = checkedViewModel()
        downloader.publishedSha = listOf("b".repeat(64))
        vm.install("whiskergrid")
        advanceUntilIdle()
        assertTrue(installer.calls.isEmpty())
        assertEquals(InstallAttempt.Failed(FailureReason.ChecksumMismatch), vm.row("whiskergrid").attempt)
        assertNoLeftoverFiles()
    }

    @Test
    fun unreadableApkIsRefused() = runTest(dispatcher) {
        val vm = checkedViewModel()
        inspector.overrides["whiskergrid"] = null
        vm.install("whiskergrid")
        advanceUntilIdle()
        assertTrue(installer.calls.isEmpty())
        assertTrue(vm.row("whiskergrid").attempt is InstallAttempt.Failed)
        assertNoLeftoverFiles()
    }

    @Test
    fun decliningInTheSystemDialogIsNotAnError() = runTest(dispatcher) {
        val vm = checkedViewModel()
        installer.results["whiskergrid"] = InstallResult.Declined
        vm.install("whiskergrid")
        advanceUntilIdle()
        assertEquals(InstallAttempt.Idle, vm.row("whiskergrid").attempt)
        assertTrue(vm.row("whiskergrid").status is UpdateStatus.UpdateAvailable)
        assertNoLeftoverFiles()
    }

    @Test
    fun installerFailureIsReportedAndCleanedUp() = runTest(dispatcher) {
        val vm = checkedViewModel()
        installer.results["whiskergrid"] = InstallResult.Failed(FailureReason.SignatureMismatch)
        vm.install("whiskergrid")
        advanceUntilIdle()
        assertEquals(InstallAttempt.Failed(FailureReason.SignatureMismatch), vm.row("whiskergrid").attempt)
        assertTrue(vm.row("whiskergrid").status is UpdateStatus.UpdateAvailable)
        assertNoLeftoverFiles()
    }

    @Test
    fun downloadFailureIsShownOnTheRow() = runTest(dispatcher) {
        val vm = checkedViewModel()
        downloader.failures["whiskergrid"] = FailureReason.Offline
        vm.install("whiskergrid")
        advanceUntilIdle()
        assertEquals(InstallAttempt.Failed(FailureReason.Offline), vm.row("whiskergrid").attempt)
        assertTrue(installer.calls.isEmpty())
    }

    @Test
    fun cancellingADownloadReturnsTheRowToIdle() = runTest(dispatcher) {
        val vm = checkedViewModel()
        downloader.gates["whiskergrid"] = CompletableDeferred()
        vm.install("whiskergrid")
        runCurrent()
        assertTrue(vm.row("whiskergrid").attempt is InstallAttempt.Downloading)
        vm.cancelInstall("whiskergrid")
        advanceUntilIdle()
        assertEquals(InstallAttempt.Idle, vm.row("whiskergrid").attempt)
        assertTrue(installer.calls.isEmpty())
        assertNoLeftoverFiles()
    }

    @Test
    fun cancellingWhileWaitingForConfirmationCleansUp() = runTest(dispatcher) {
        val vm = checkedViewModel()
        installer.gates["whiskergrid"] = CompletableDeferred()
        vm.install("whiskergrid")
        runCurrent()
        assertEquals(InstallAttempt.AwaitingConfirmation, vm.row("whiskergrid").attempt)
        vm.cancelInstall("whiskergrid")
        advanceUntilIdle()
        assertEquals(InstallAttempt.Idle, vm.row("whiskergrid").attempt)
        assertNoLeftoverFiles()
    }

    @Test
    fun aSecondInstallOfTheSameAppIsIgnored() = runTest(dispatcher) {
        val vm = checkedViewModel()
        downloader.gates["whiskergrid"] = CompletableDeferred()
        vm.install("whiskergrid")
        runCurrent()
        vm.install("whiskergrid")
        runCurrent()
        assertEquals(listOf("whiskergrid"), downloader.calls)
    }

    @Test
    fun missingInstallPermissionAsksOnceAndContinuesAfterItIsGranted() = runTest(dispatcher) {
        val vm = checkedViewModel()
        canInstall = false
        vm.install("whiskergrid")
        advanceUntilIdle()
        assertTrue(vm.state.value.permissionRequested)
        assertEquals(InstallAttempt.Queued, vm.row("whiskergrid").attempt)
        assertTrue(downloader.calls.isEmpty())

        vm.permissionPromptShown()
        assertFalse(vm.state.value.permissionRequested)
        canInstall = true
        vm.onInstallPermissionResult()
        advanceUntilIdle()
        assertEquals(InstallAttempt.Installed, vm.row("whiskergrid").attempt)
    }

    @Test
    fun refusedPermissionFailsTheQueuedApp() = runTest(dispatcher) {
        val vm = checkedViewModel()
        canInstall = false
        vm.install("whiskergrid")
        advanceUntilIdle()
        vm.permissionPromptShown()
        vm.onInstallPermissionResult()
        advanceUntilIdle()
        assertEquals(InstallAttempt.Failed(FailureReason.NotAllowedToInstall), vm.row("whiskergrid").attempt)
        assertTrue(downloader.calls.isEmpty())
    }

    @Test
    fun updateAllRunsSequentiallyAndOneFailureDoesNotStopTheRest() = runTest(dispatcher) {
        val vm = checkedViewModel()
        downloader.failures["circuitopedia"] = FailureReason.Offline
        val firstInstall = CompletableDeferred<Unit>()
        installer.gates["ghostbms"] = firstInstall

        vm.updateAll()
        runCurrent()
        // Only the first app is in progress; the others wait in the queue.
        assertEquals(listOf("ghostbms"), downloader.calls)
        assertEquals(InstallAttempt.Queued, vm.row("whiskergrid").attempt)

        firstInstall.complete(Unit)
        advanceUntilIdle()
        assertEquals(InstallAttempt.Installed, vm.row("ghostbms").attempt)
        assertEquals(InstallAttempt.Installed, vm.row("whiskergrid").attempt)
        assertEquals(InstallAttempt.Installed, vm.row("hentaireader").attempt)
        assertEquals(InstallAttempt.Failed(FailureReason.Offline), vm.row("circuitopedia").attempt)
        assertEquals(InstallAttempt.Installed, vm.row("updater").attempt)
        assertEquals(listOf("ghostbms", "whiskergrid", "hentaireader", "circuitopedia", "updater"), downloader.calls)
    }

    @Test
    fun updateAllSkipsAppsThatAreNotInstalled() = runTest(dispatcher) {
        val vm = checkedViewModel(installed = setOf("whiskergrid"))
        vm.updateAll()
        advanceUntilIdle()
        assertEquals(listOf("whiskergrid"), downloader.calls)
    }

    @Test
    fun theNextDownloadStartsOnlyWhileTheAppIsVisible() = runTest(dispatcher) {
        val vm = checkedViewModel()
        val firstInstall = CompletableDeferred<Unit>()
        installer.gates["ghostbms"] = firstInstall
        vm.install("ghostbms")
        vm.install("whiskergrid")
        runCurrent()
        assertEquals(InstallAttempt.AwaitingConfirmation, vm.row("ghostbms").attempt)

        // The system dialog covers the app; the queue must survive and wait.
        vm.onLeftApp()
        firstInstall.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("ghostbms"), downloader.calls)
        assertEquals(InstallAttempt.Queued, vm.row("whiskergrid").attempt)

        vm.onEnteredApp()
        advanceUntilIdle()
        assertEquals(listOf("ghostbms", "whiskergrid"), downloader.calls)
        assertEquals(InstallAttempt.Installed, vm.row("whiskergrid").attempt)
    }

    @Test
    fun leavingTheAppDuringADownloadCancelsItAndTheQueue() = runTest(dispatcher) {
        val vm = checkedViewModel()
        downloader.gates["ghostbms"] = CompletableDeferred()
        vm.install("ghostbms")
        vm.install("whiskergrid")
        runCurrent()
        vm.onLeftApp()
        advanceUntilIdle()
        assertEquals(InstallAttempt.Idle, vm.row("ghostbms").attempt)
        assertEquals(InstallAttempt.Idle, vm.row("whiskergrid").attempt)
        assertTrue(installer.calls.isEmpty())
        assertNoLeftoverFiles()
    }
}
