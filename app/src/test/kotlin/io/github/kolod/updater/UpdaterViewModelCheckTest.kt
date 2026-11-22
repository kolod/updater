package io.github.kolod.updater

import io.github.kolod.updater.core.catalog.Catalog
import io.github.kolod.updater.core.status.FailureReason
import io.github.kolod.updater.core.status.UpdateStatus
import io.github.kolod.updater.data.LastCheckStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UpdaterViewModelCheckTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val source = FakeSource()
    private val reader = FakeReader()
    private var now = 1_000L

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = UpdaterViewModel(
        catalog = Catalog.apps,
        source = source,
        reader = reader,
        store = LastCheckStore(File(folder.root, "last.json")),
        downloader = FakeDownloader(folder.newFolder()),
        inspector = FakeInspector(),
        installer = FakeInstaller(reader),
        canInstall = { true },
        clock = { now },
        io = dispatcher,
    )

    private fun UpdaterViewModel.row(id: String) = state.value.apps.first { it.app.id == id }

    private fun allSucceedWith(version: String) {
        Catalog.apps.forEach { source.results[it.id] = FetchResult.Success(releaseOf(it, version)) }
    }

    @Test
    fun startsWithOneUnknownRowPerCatalogApp() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(Catalog.apps.map { it.id }, vm.state.value.apps.map { it.app.id })
        // Hentai Reader is optional and not installed, so it has no visible row.
        assertEquals(
            Catalog.apps.map { it.id }.filter { it != "hentaireader" },
            vm.state.value.visibleApps.map { it.app.id },
        )
        assertTrue(vm.state.value.apps.all { it.status == null })
        assertFalse(vm.state.value.isChecking)
        assertNull(vm.state.value.lastCheckedAt)
    }

    @Test
    fun checkResolvesAllFiveRows() = runTest(dispatcher) {
        allSucceedWith("2.0.0")
        reader.installed["whiskergrid"] = installedOf(Catalog.byId("whiskergrid"), "1.0.0")
        reader.installed["circuitopedia"] = installedOf(Catalog.byId("circuitopedia"), "2.0.0")
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()

        assertTrue(vm.row("whiskergrid").status is UpdateStatus.UpdateAvailable)
        assertTrue(vm.row("circuitopedia").status is UpdateStatus.UpToDate)
        assertTrue(vm.row("ghostbms").status is UpdateStatus.NotInstalled)
        assertEquals(4, vm.state.value.visibleApps.count { it.status != null })
        assertTrue(vm.state.value.visibleApps.all { it.freshness == Freshness.Fresh })
        assertEquals("2.0.0", vm.row("whiskergrid").latestKnown)
        assertEquals(1, vm.state.value.updatesAvailable)
        assertEquals(now, vm.state.value.lastCheckedAt)
        assertFalse(vm.state.value.isChecking)
    }

    @Test
    fun checksRunInParallel() = runTest(dispatcher) {
        allSucceedWith("1.0.0")
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val vm = viewModel()
        vm.check()
        runCurrent()
        assertEquals("all four requests are in flight before any finishes", 4, source.calls.size)
        assertTrue(vm.state.value.isChecking)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.isChecking)
    }

    @Test
    fun oneFailureDoesNotAffectTheOthers() = runTest(dispatcher) {
        allSucceedWith("1.0.0")
        source.results["whiskergrid"] = FetchResult.Failure(FailureReason.ServerError(500))
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()
        assertEquals(UpdateStatus.Failed(FailureReason.ServerError(500)), vm.row("whiskergrid").status)
        listOf("ghostbms", "circuitopedia", "updater").forEach {
            assertTrue(it, vm.row(it).status is UpdateStatus.NotInstalled)
            assertEquals(Freshness.Fresh, vm.row(it).freshness)
        }
    }

    @Test
    fun rateLimitStopsFurtherRequestsAndMarksTheRest() = runTest(dispatcher) {
        allSucceedWith("1.0.0")
        val limited = FailureReason.RateLimited(1795352754L)
        source.results["whiskergrid"] = FetchResult.Failure(limited)
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()
        // Catalog order: ghostbms, whiskergrid (limited), circuitopedia, updater (hentaireader is not asked).
        assertEquals(listOf("ghostbms", "whiskergrid"), source.calls)
        listOf("whiskergrid", "circuitopedia", "updater").forEach {
            assertEquals(it, UpdateStatus.Failed(limited), vm.row(it).status)
        }
        assertTrue(vm.row("ghostbms").status is UpdateStatus.NotInstalled)
    }

    @Test
    fun secondCheckWhileCheckingIsIgnored() = runTest(dispatcher) {
        allSucceedWith("1.0.0")
        source.gate = CompletableDeferred()
        val vm = viewModel()
        vm.check()
        runCurrent()
        vm.check()
        runCurrent()
        assertEquals(4, source.calls.size)
    }

    @Test
    fun cancelStopsTheRunAndKeepsPreviousRows() = runTest(dispatcher) {
        allSucceedWith("1.0.0")
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()
        val before = vm.state.value.apps
        val checkedAt = vm.state.value.lastCheckedAt

        now = 5_000L
        source.gate = CompletableDeferred()
        vm.check()
        runCurrent()
        assertTrue(vm.state.value.isChecking)
        vm.cancel()
        advanceUntilIdle()
        assertFalse(vm.state.value.isChecking)
        assertEquals(before, vm.state.value.apps)
        assertEquals(checkedAt, vm.state.value.lastCheckedAt)
    }

    @Test
    fun failedCheckKeepsPreviousVersionsMarkedNotFresh() = runTest(dispatcher) {
        allSucceedWith("2.0.0")
        reader.installed["circuitopedia"] = installedOf(Catalog.byId("circuitopedia"), "1.0.0")
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()

        Catalog.apps.forEach { source.results[it.id] = FetchResult.Failure(FailureReason.Offline) }
        vm.check()
        advanceUntilIdle()
        val row = vm.row("circuitopedia")
        assertEquals(UpdateStatus.Failed(FailureReason.Offline), row.status)
        assertEquals("2.0.0", row.latestKnown)
        assertEquals("1.0.0", row.installed?.versionName)
        assertEquals(Freshness.Stale, row.freshness)
    }

    @Test
    fun lastCheckedAtChangesOnlyWhenARunCompletes() = runTest(dispatcher) {
        allSucceedWith("1.0.0")
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val vm = viewModel()
        vm.check()
        runCurrent()
        assertTrue(vm.state.value.isChecking)
        assertNull(vm.state.value.lastCheckedAt)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(now, vm.state.value.lastCheckedAt)
    }

    @Test
    fun earlierResultsAreRestoredAsNotFreshOnTheNextLaunch() = runTest(dispatcher) {
        allSucceedWith("2.0.0")
        reader.installed["whiskergrid"] = installedOf(Catalog.byId("whiskergrid"), "1.0.0")
        viewModel().also {
            it.check()
            advanceUntilIdle()
        }

        val second = viewModel()
        advanceUntilIdle()
        val row = second.row("whiskergrid")
        assertEquals("2.0.0", row.latestKnown)
        assertEquals(Freshness.Stale, row.freshness)
        assertEquals("1.0.0", row.installed?.versionName)
        assertNull(row.status)
    }

    @Test
    fun anOptionalAppThatIsNotInstalledHasNoRowAndCausesNoRequest() = runTest(dispatcher) {
        allSucceedWith("2.0.0")
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()
        assertFalse("hentaireader" in source.calls)
        assertFalse(vm.state.value.visibleApps.any { it.app.id == "hentaireader" })
        assertNull(vm.row("hentaireader").status)
    }

    @Test
    fun anInstalledOptionalAppIsListedCheckedAndCounted() = runTest(dispatcher) {
        allSucceedWith("2.0.0")
        reader.installed["hentaireader"] = installedOf(Catalog.byId("hentaireader"), "1.0.0")
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()
        assertTrue("hentaireader" in source.calls)
        assertTrue(vm.state.value.visibleApps.any { it.app.id == "hentaireader" })
        assertTrue(vm.row("hentaireader").status is UpdateStatus.UpdateAvailable)
        assertEquals(1, vm.state.value.updatesAvailable)
    }

    @Test
    fun anOptionalAppDisappearsAtTheNextCheckAfterItIsUninstalled() = runTest(dispatcher) {
        allSucceedWith("2.0.0")
        reader.installed["hentaireader"] = installedOf(Catalog.byId("hentaireader"), "1.0.0")
        val vm = viewModel()
        vm.check()
        advanceUntilIdle()
        assertTrue(vm.state.value.visibleApps.any { it.app.id == "hentaireader" })

        reader.installed["hentaireader"] = null
        source.calls.clear()
        vm.check()
        advanceUntilIdle()
        assertFalse(vm.state.value.visibleApps.any { it.app.id == "hentaireader" })
        assertFalse("hentaireader" in source.calls)
        assertEquals(0, vm.state.value.updatesAvailable)
    }
}
