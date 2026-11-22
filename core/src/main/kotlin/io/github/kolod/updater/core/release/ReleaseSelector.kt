package io.github.kolod.updater.core.release

/** The release with the highest semantic version, independent of the order GitHub returned them in. */
fun selectRelease(releases: List<Release>): Release? = releases.maxByOrNull { it.version }
