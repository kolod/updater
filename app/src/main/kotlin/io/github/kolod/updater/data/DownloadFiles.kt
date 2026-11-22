package io.github.kolod.updater.data

import io.github.kolod.updater.core.catalog.ManagedApp
import io.github.kolod.updater.core.verify.Sha256
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Owns the private download directory (constitution, Principle II): files are written as `.part`,
 * renamed only after a complete download, and never outlive an installation attempt.
 */
class DownloadFiles(private val dir: File) {

    fun partFor(app: ManagedApp, version: String): File {
        dir.mkdirs()
        return File(dir, "${app.id}-$version.apk.part")
    }

    /** Turns a finished `.part` file into the final `.apk` file. */
    fun commit(part: File): File {
        val target = File(part.parentFile, part.name.removeSuffix(".part"))
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    fun discard(file: File) {
        file.delete()
    }

    /** Removes everything left over from earlier runs; called at process start. */
    fun wipeAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Streams [input] into [part] and returns the lower-case SHA-256 and byte count. On any failure
     * (including cancellation through [checkActive]) the partial file is deleted before rethrowing.
     */
    fun write(input: InputStream, part: File, onProgress: (Long) -> Unit, checkActive: () -> Unit): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        try {
            part.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    written += read
                    onProgress(written)
                }
            }
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
        return Sha256.toHex(digest.digest()) to written
    }
}
