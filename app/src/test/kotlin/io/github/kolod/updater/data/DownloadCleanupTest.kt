package io.github.kolod.updater.data

import io.github.kolod.updater.core.catalog.Catalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class DownloadCleanupTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val app = Catalog.byId("circuitopedia")
    private fun files() = DownloadFiles(File(folder.root, "downloads"))

    @Test
    fun partFileIsRenamedOnlyAfterCommit() {
        val files = files()
        val part = files.partFor(app, "0.2.0")
        val (sha, size) = files.write(ByteArrayInputStream("abc".toByteArray()), part, {}, {})
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha)
        assertEquals(3L, size)
        assertTrue(part.exists())
        val done = files.commit(part)
        assertEquals("circuitopedia-0.2.0.apk", done.name)
        assertFalse(part.exists())
        assertTrue(done.exists())
    }

    @Test
    fun partialFileIsRemovedWhenTheStreamFails() {
        val files = files()
        val part = files.partFor(app, "0.2.0")
        val failing = object : InputStream() {
            private var served = false
            override fun read(): Int = throw IOException("unused")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!served) {
                    served = true
                    b[off] = 1
                    return 1
                }
                throw IOException("connection reset")
            }
        }
        try {
            files.write(failing, part, {}, {})
            error("expected failure")
        } catch (_: IOException) {
        }
        assertFalse(part.exists())
    }

    @Test
    fun partialFileIsRemovedOnCancellation() {
        val files = files()
        val part = files.partFor(app, "0.2.0")
        var calls = 0
        try {
            files.write(ByteArrayInputStream(ByteArray(300_000)), part, {}, { if (++calls == 2) throw IllegalStateException("cancelled") })
            error("expected cancellation")
        } catch (_: IllegalStateException) {
        }
        assertFalse(part.exists())
    }

    @Test
    fun wipeAllEmptiesTheDirectory() {
        val files = files()
        files.write(ByteArrayInputStream(byteArrayOf(1)), files.partFor(app, "1.0.0"), {}, {})
        File(folder.root, "downloads/old.apk").writeText("x")
        files.wipeAll()
        assertTrue(File(folder.root, "downloads").listFiles().isNullOrEmpty())
    }

    @Test
    fun progressIsReportedCumulatively() {
        val files = files()
        val seen = mutableListOf<Long>()
        files.write(ByteArrayInputStream(ByteArray(200_000)), files.partFor(app, "1.0.0"), { seen += it }, {})
        assertEquals(200_000L, seen.last())
        assertEquals(seen.sorted(), seen)
    }
}
