# Contract: Catalog

Bundled in the APK as Kotlin data (`core/.../catalog/Catalog.kt`); never read from a remote or user
source (Principle IV, FR-020). Schema: see `ManagedApp` in [../data-model.md](../data-model.md).

| id | displayName | packageName | repo | tagPrefix | apkAssetName | checksumAssetName | isSelf |
|---|---|---|---|---|---|---|---|
| `ghostbms` | Ghost BMS (unofficial) | `io.github.kolod.ghostbms` | `kolod/ghost-bms-unofficial` | `v` | `ghost-bms-unofficial.apk` | – | no |
| `whiskergrid` | Whisker Grid | `io.github.kolod.whiskergrid` | `kolod/whiskergrid` | `v` | `whisker-grid.apk` | – | no |
| `hentaireader` | Hentai Reader | `io.github.kolod.hentaireader` | `kolod/hentai-reader` | `android-v` | `hentai-reader.apk` | `hentai-reader.apk.sha256` | no |
| `circuitopedia` | Circuitopedia | `io.github.kolod.circuitopedia` | `kolod/circuitopedia` | `v` | `circuitopedia.apk` | – | no |
| `updater` | Updater | `io.github.kolod.updater` | `kolod/updater` | `v` | `updater.apk` | `updater.apk.sha256` | yes |

Display order in the UI: as listed, updater last. Adding an app = one row here + a fixture; no logic
change. A catalog unit test asserts uniqueness, valid package names, exactly one `isSelf`, and that
every repository is reachable under the Principle I allow-list.
