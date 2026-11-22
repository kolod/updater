package io.github.kolod.updater.core.status

/** Why a check or an installation did not succeed. Each maps to one localized message in the app. */
sealed interface FailureReason {
    data object Offline : FailureReason
    data class RateLimited(val resetAtEpochSeconds: Long?) : FailureReason
    data class ServerError(val httpCode: Int) : FailureReason
    data object BadResponse : FailureReason
    data object StorageFull : FailureReason
    data object ChecksumMismatch : FailureReason
    data object WrongPackage : FailureReason
    /** The version inside the downloaded APK differs from the release it was published under. */
    data object VersionMismatch : FailureReason
    data object VersionNotNewer : FailureReason
    data object SignatureMismatch : FailureReason
    data class InstallerRejected(val code: Int) : FailureReason
    /** The user cancelled; not shown as an error. */
    data object Cancelled : FailureReason
    data object NotAllowedToInstall : FailureReason
}
