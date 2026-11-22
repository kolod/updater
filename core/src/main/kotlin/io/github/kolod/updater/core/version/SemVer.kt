package io.github.kolod.updater.core.version

import java.math.BigInteger

/**
 * Semantic Version 2.0.0 (https://semver.org/spec/v2.0.0.html), including pre-release precedence.
 * Build metadata is accepted but ignored in comparisons and equality.
 */
class SemVer private constructor(
    private val major: BigInteger,
    private val minor: BigInteger,
    private val patch: BigInteger,
    private val preRelease: List<String>,
    private val text: String,
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        major.compareTo(other.major).let { if (it != 0) return it }
        minor.compareTo(other.minor).let { if (it != 0) return it }
        patch.compareTo(other.patch).let { if (it != 0) return it }
        // A version without pre-release identifiers is greater than one with them.
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1
        val common = minOf(preRelease.size, other.preRelease.size)
        for (i in 0 until common) {
            compareIdentifier(preRelease[i], other.preRelease[i]).let { if (it != 0) return it }
        }
        return preRelease.size.compareTo(other.preRelease.size)
    }

    override fun equals(other: Any?): Boolean = other is SemVer && compareTo(other) == 0

    override fun hashCode(): Int = listOf(major, minor, patch, preRelease).hashCode()

    override fun toString(): String = text

    companion object {
        private const val NUMERIC = "0|[1-9]\\d*"
        private const val PRE_ID = "(?:$NUMERIC|\\d*[a-zA-Z-][0-9a-zA-Z-]*)"
        private val PATTERN = Regex(
            "^($NUMERIC)\\.($NUMERIC)\\.($NUMERIC)" +
                "(?:-($PRE_ID(?:\\.$PRE_ID)*))?" +
                "(?:\\+[0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*)?$",
        )

        /** Returns null for anything that is not a valid semantic version; never throws. */
        fun parse(input: String): SemVer? {
            val match = PATTERN.matchEntire(input) ?: return null
            val (major, minor, patch, pre) = match.destructured
            return SemVer(
                BigInteger(major),
                BigInteger(minor),
                BigInteger(patch),
                if (pre.isEmpty()) emptyList() else pre.split('.'),
                input,
            )
        }

        private fun isNumeric(id: String) = id.all { it in '0'..'9' }

        private fun compareIdentifier(a: String, b: String): Int {
            val aNumeric = isNumeric(a)
            val bNumeric = isNumeric(b)
            return when {
                aNumeric && bNumeric -> BigInteger(a).compareTo(BigInteger(b))
                aNumeric -> -1
                bNumeric -> 1
                else -> a.compareTo(b)
            }
        }
    }
}
