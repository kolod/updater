package io.github.kolod.updater.core.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    private fun v(s: String): SemVer = SemVer.parse(s) ?: error("not parsed: $s")

    @Test
    fun parsesValidVersions() {
        assertNotNull(SemVer.parse("0.0.0"))
        assertNotNull(SemVer.parse("1.2.3"))
        assertNotNull(SemVer.parse("1.0.0-alpha.1"))
        assertNotNull(SemVer.parse("1.0.0-0.3.7"))
        assertNotNull(SemVer.parse("1.0.0+build.5"))
        assertNotNull(SemVer.parse("1.0.0-rc.1+build.5"))
        assertEquals("1.2.3-dev", v("1.2.3-dev").toString())
    }

    @Test
    fun rejectsInvalidVersions() {
        listOf(
            "", " ", "1", "1.2", "1.2.3.4", "v1.2.3", "01.2.3", "1.02.3", "1.2.03",
            "1.2.3-", "1.2.3-01", "1.2.3+", "1.2.3-a..b", "a.b.c", "1.2.3 ", "-1.2.3",
        ).forEach { assertNull("should be invalid: '$it'", SemVer.parse(it)) }
    }

    @Test
    fun followsPrecedenceOfTheSpecification() {
        val ascending = listOf(
            "0.1.0-dev", "0.1.0", "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta",
            "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0",
            "1.0.1", "1.1.0", "2.0.0", "10.0.0",
        )
        for (i in 0 until ascending.size - 1) {
            assertTrue("${ascending[i]} < ${ascending[i + 1]}", v(ascending[i]) < v(ascending[i + 1]))
            assertTrue("${ascending[i + 1]} > ${ascending[i]}", v(ascending[i + 1]) > v(ascending[i]))
        }
    }

    @Test
    fun numericIdentifiersSortBeforeAlphanumeric() {
        assertTrue(v("1.0.0-1") < v("1.0.0-a"))
        assertTrue(v("1.0.0-9") < v("1.0.0-10"))
    }

    @Test
    fun buildMetadataIsIgnoredInPrecedence() {
        assertEquals(0, v("1.0.0+a").compareTo(v("1.0.0+b")))
        assertEquals(v("1.0.0"), v("1.0.0+x"))
        assertEquals(v("1.0.0").hashCode(), v("1.0.0+x").hashCode())
    }

    @Test
    fun hugeNumbersDoNotCrash() {
        assertTrue(v("99999999999999999999.0.0") > v("1.0.0"))
    }
}
