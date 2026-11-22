# Contract: Network Policy

Implements Principles I and II at the code level as one pure function set in `:core` (`NetworkPolicy`).

## Rules

1. Scheme MUST be `https`. Any other scheme (including on a redirect) ⇒ refused.
2. Host MUST be one of: `api.github.com`, `github.com`, `objects.githubusercontent.com`,
   `release-assets.githubusercontent.com`. Anything else ⇒ refused, including after redirects.
   This list is the single place to extend, and only by constitution amendment.
3. Port MUST be default (443); no user-info in the URL.
4. At most 5 redirect hops per request; each hop re-validated by rules 1–3.
5. `browser_download_url` MUST also have path prefix `/{owner}/{repo}/releases/download/` of the
   catalog app it belongs to; otherwise the asset is ignored.
6. Request headers: only `Accept`, `X-GitHub-Api-Version` (API) and `User-Agent: Updater`.
   No `Cookie`, `Authorization`, `Referer`, device or installed-app data.
7. Timeouts: connect 10 s, read 20 s (API) / 30 s (download, between chunks).
8. Retries: ≤2 for connection failures and 5xx, backoff 1 s then 2 s; never for 403/429/404; none after cancel.

## Tests (JVM)

Table-driven tests for: http scheme, foreign host, lookalike hosts (`github.com.evil.net`,
`evilgithub.com`), user-info, non-default port, redirect to foreign host, redirect loop (>5), asset URL
belonging to a different repository.
