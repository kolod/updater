package io.github.kolod.updater.core.verify

import io.github.kolod.updater.core.catalog.Catalog
import io.github.kolod.updater.core.release.Release
import io.github.kolod.updater.core.release.ReleaseAsset
import io.github.kolod.updater.core.status.FailureReason
import io.github.kolod.updater.core.status.InstalledApp
import io.github.kolod.updater.core.version.SemVer
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallPolicyTest {
    private val app = Catalog.byId("circuitopedia")
    private val goodHash = "a".repeat(64)
    private val otherHash = "b".repeat(64)

    private val release = Release(
        tag = "v0.2.0",
        version = SemVer.parse("0.2.0")!!,
        apk = ReleaseAsset("circuitopedia.apk", 1, "https://github.com/x", goodHash),
        checksumAsset = null,
    )

    private fun apk(
        pkg: String = app.packageName,
        versionName: String? = "0.2.0",
        versionCode: Long = 5,
        signers: Set<String> = setOf("sig1"),
    ) = ApkFacts(pkg, versionName, versionCode, signers)

    private fun installed(
        versionName: String? = "0.1.0",
        versionCode: Long = 3,
        signers: Set<String> = setOf("sig1"),
    ) = InstalledApp(app.packageName, versionName, versionCode, signers)

    private fun check(
        apk: ApkFacts = apk(),
        installed: InstalledApp? = installed(),
        computed: String = goodHash,
        published: List<String> = listOf(goodHash),
    ) = checkDownloadedApk(app, release, apk, installed, computed, published)

    private fun fail(reason: FailureReason) = VerifyResult.Fail(reason)

    @Test
    fun happyPathForAnUpdate() = assertEquals(VerifyResult.Ok, check())

    @Test
    fun happyPathForAFreshInstallSkipsSignerComparison() {
        assertEquals(VerifyResult.Ok, check(installed = null, apk = apk(signers = setOf("anything"))))
    }

    @Test
    fun noPublishedChecksumIsAllowed() = assertEquals(VerifyResult.Ok, check(published = emptyList()))

    @Test
    fun wrongChecksumIsRefused() {
        assertEquals(fail(FailureReason.ChecksumMismatch), check(computed = otherHash))
    }

    @Test
    fun oneMismatchingSourceAmongSeveralIsRefused() {
        assertEquals(fail(FailureReason.ChecksumMismatch), check(published = listOf(goodHash, otherHash)))
    }

    @Test
    fun wrongPackageIsRefused() {
        assertEquals(fail(FailureReason.WrongPackage), check(apk = apk(pkg = "evil.app")))
    }

    @Test
    fun versionInsideTheApkMustMatchTheRelease() {
        assertEquals(fail(FailureReason.VersionMismatch), check(apk = apk(versionName = "0.3.0")))
        assertEquals(fail(FailureReason.VersionMismatch), check(apk = apk(versionName = null)))
        assertEquals(fail(FailureReason.VersionMismatch), check(apk = apk(versionName = "garbage")))
    }

    @Test
    fun sameOrOlderVersionIsRefused() {
        assertEquals(fail(FailureReason.VersionNotNewer), check(installed = installed(versionName = "0.2.0")))
        assertEquals(fail(FailureReason.VersionNotNewer), check(installed = installed(versionName = "0.5.0")))
    }

    @Test
    fun versionCodeMustIncrease() {
        assertEquals(fail(FailureReason.VersionNotNewer), check(installed = installed(versionCode = 5)))
        assertEquals(fail(FailureReason.VersionNotNewer), check(installed = installed(versionCode = 9)))
    }

    @Test
    fun unreadableInstalledVersionNameStillRequiresANewerVersionCode() {
        assertEquals(VerifyResult.Ok, check(installed = installed(versionName = null, versionCode = 3)))
        assertEquals(fail(FailureReason.VersionNotNewer), check(installed = installed(versionName = null, versionCode = 5)))
    }

    @Test
    fun differentSignerIsRefused() {
        assertEquals(fail(FailureReason.SignatureMismatch), check(apk = apk(signers = setOf("other"))))
        assertEquals(fail(FailureReason.SignatureMismatch), check(apk = apk(signers = emptySet())))
    }

    @Test
    fun overlappingSignerSetsAreAccepted() {
        assertEquals(
            VerifyResult.Ok,
            check(apk = apk(signers = setOf("SIG1", "new")), installed = installed(signers = setOf("sig1", "old"))),
        )
    }

    @Test
    fun checksRunInTheDocumentedOrder() {
        // Everything is wrong: the checksum is reported first, then package, version, signer.
        val bad = apk(pkg = "evil", versionName = "9.9.9", signers = setOf("x"))
        assertEquals(fail(FailureReason.ChecksumMismatch), check(apk = bad, computed = otherHash))
        assertEquals(fail(FailureReason.WrongPackage), check(apk = bad))
        assertEquals(fail(FailureReason.VersionMismatch), check(apk = apk(versionName = "9.9.9", signers = setOf("x"))))
        assertEquals(fail(FailureReason.SignatureMismatch), check(apk = apk(signers = setOf("x"))))
    }
}
