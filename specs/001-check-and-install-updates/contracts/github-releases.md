# Contract: GitHub Releases API (consumed)

The only remote interface the app uses. Unauthenticated, read-only.

## Request

```text
GET https://api.github.com/repos/{owner}/{repo}/releases?per_page=30
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
User-Agent: Updater
```

No `Authorization`, cookies or identifiers. One request per catalog app per check (FR-017).

## Fields read (everything else ignored)

```text
[ {
    "tag_name":   string,
    "draft":      boolean,
    "prerelease": boolean,
    "assets": [ {
        "name":                 string,
        "size":                 integer,
        "browser_download_url": string,   // must satisfy network-policy.md
        "digest":               string?   // "sha256:<64 hex>" when present
    } ]
} ]
```

Missing optional `digest` is normal (older releases). Unknown `digest` algorithm prefix ⇒ ignored.
Missing required field in a release ⇒ that release is skipped; non-array/invalid JSON ⇒ `BadResponse`.

## Error mapping

| Response | Result |
|---|---|
| 200 with matching release | status per data-model |
| 200, no eligible release | `NoRelease` |
| 404 | `NoRelease` |
| 403/429 with `X-RateLimit-Remaining: 0` | `RateLimited(X-RateLimit-Reset)`; stop sending further requests this run |
| other 4xx/5xx | `ServerError(code)` after bounded retry for 5xx |
| network exception | `Offline` |

## Recorded fixtures (`core/src/test/resources/fixtures/`)

Captured once from the real repositories, trimmed to the fields above, never refreshed from tests:

| File | Purpose |
|---|---|
| `circuitopedia-releases.json` | plain `v` tags, `digest` present |
| `hentai-reader-releases.json` | monorepo: `windows-v0.4.0` first, `android-v0.1.0` second; `.sha256` asset |
| `whiskergrid-releases.json`, `ghost-bms-releases.json` | plain tags |
| `prerelease-and-draft.json` | synthetic: draft + pre-release newer than the stable one |
| `no-apk-asset.json` | eligible tag without the APK asset |
| `empty.json`, `malformed.json`, `rate-limit-headers.txt` | edge cases |
