package io.github.kolod.updater.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LastCheckStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = LastCheckStore(File(folder.root, "last-check.json"))

    @Test
    fun roundTrip() {
        val entries = mapOf(
            "circuitopedia" to LastCheckStore.Entry("0.2.0", 1_700_000_000_000L),
            "whiskergrid" to LastCheckStore.Entry(null, 5L),
        )
        store().save(entries)
        assertEquals(entries, store().load())
    }

    @Test
    fun saveReplacesAndLeavesNoTempFile() {
        store().save(mapOf("a" to LastCheckStore.Entry("1.0.0", 1)))
        store().save(mapOf("b" to LastCheckStore.Entry("2.0.0", 2)))
        assertEquals(setOf("b"), store().load().keys)
        assertFalse(File(folder.root, "last-check.json.tmp").exists())
    }

    @Test
    fun missingFileMeansNoData() {
        assertTrue(store().load().isEmpty())
    }

    @Test
    fun corruptFileIsIgnored() {
        File(folder.root, "last-check.json").writeText("{ not json")
        assertTrue(store().load().isEmpty())
    }

    @Test
    fun unknownVersionIsIgnored() {
        File(folder.root, "last-check.json").writeText("""{"version":99,"apps":{"a":{"latest":"1.0.0","checkedAt":1}}}""")
        assertTrue(store().load().isEmpty())
    }

    @Test
    fun fileContainsOnlyTheGivenIdsVersionsAndTimes() {
        store().save(mapOf("circuitopedia" to LastCheckStore.Entry("0.2.0", 7)))
        val root = org.json.JSONObject(File(folder.root, "last-check.json").readText())
        assertEquals(setOf("version", "apps"), root.keys().asSequence().toSet())
        val apps = root.getJSONObject("apps")
        assertEquals(setOf("circuitopedia"), apps.keys().asSequence().toSet())
        assertEquals(setOf("latest", "checkedAt"), apps.getJSONObject("circuitopedia").keys().asSequence().toSet())
    }
}
