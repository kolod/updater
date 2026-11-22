package io.github.kolod.updater.core.catalog

/** The fixed catalog; see specs/001-check-and-install-updates/contracts/catalog.md. */
object Catalog {
    val apps: List<ManagedApp> = listOf(
        ManagedApp(
            id = "ghostbms",
            displayName = "Ghost BMS (unofficial)",
            packageName = "io.github.kolod.ghostbms",
            repoOwner = "kolod",
            repoName = "ghost-bms-unofficial",
            tagPrefix = "v",
            apkAssetName = "ghost-bms-unofficial.apk",
        ),
        ManagedApp(
            id = "whiskergrid",
            displayName = "Whisker Grid",
            packageName = "io.github.kolod.whiskergrid",
            repoOwner = "kolod",
            repoName = "whiskergrid",
            tagPrefix = "v",
            apkAssetName = "whisker-grid.apk",
        ),
        ManagedApp(
            id = "hentaireader",
            displayName = "Hentai Reader",
            packageName = "io.github.kolod.hentaireader",
            repoOwner = "kolod",
            repoName = "hentai-reader",
            // Monorepo: Windows releases are tagged windows-v*, so only android-v* is considered.
            tagPrefix = "android-v",
            apkAssetName = "hentai-reader.apk",
            checksumAssetName = "hentai-reader.apk.sha256",
        ),
        ManagedApp(
            id = "circuitopedia",
            displayName = "Circuitopedia",
            packageName = "io.github.kolod.circuitopedia",
            repoOwner = "kolod",
            repoName = "circuitopedia",
            tagPrefix = "v",
            apkAssetName = "circuitopedia.apk",
        ),
        ManagedApp(
            id = "updater",
            displayName = "Updater",
            packageName = "io.github.kolod.updater",
            repoOwner = "kolod",
            repoName = "updater",
            tagPrefix = "v",
            apkAssetName = "updater.apk",
            checksumAssetName = "updater.apk.sha256",
            isSelf = true,
        ),
    )

    val self: ManagedApp = apps.single { it.isSelf }

    fun byId(id: String): ManagedApp = apps.first { it.id == id }
}
