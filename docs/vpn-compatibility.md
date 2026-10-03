# VPN compatibility investigation

Status: version 0.22.0 compatibility candidate implemented; independent diagnostic and cookie
reviews complete. Exact signed-build identity and checks are recorded in the
release manifest. No live Proton VPN fix has been verified.

On 2026-10-03 the owner reported that Glance failed on the shared-person setup
screen with “LibreLinkUp returned an unexpected response” when using Proton
Free across several countries. The official LibreLinkUp app still loaded the
reading with Proton enabled. This is owner-reported behavior, not a captured
HTTP trace or an independently reproduced same-timestamp comparison.

Glance uses the normal Android network and a standard OkHttp client. Its
regional host comes from the provider's login response. It does not detect or
block VPNs. A generic protocol error previously hid the HTTP status and response
type. A VPN-related rejection, an HTML security page, and an incompatible JSON
response could all produce the same user-facing error.

The investigation patch retains only numeric HTTP status and one of three
fixed categories derived from the declared Content-Type: JSON, WEB_PAGE, OTHER.
It attaches this metadata to source errors, displays it under setup errors, and
records it in the existing redacted diagnostics. Diagnostic metadata includes no HTTP bodies, raw header values, cookie values,
URLs, credentials, names, IDs, or readings. Successful
polls and subsequent non-HTTP errors clear stale response metadata. Existing
source error kinds, provider cooldowns, bounded version retries, regional
allowlists, and platform TLS validation are unchanged.

## Cookie compatibility candidate

Source-backed comparison found that Glance's default OkHttp client used
`CookieJar.NO_COOKIES`. The public LibreView country-config response observed
by the protocol researcher on 2026-10-03 included a Secure, HttpOnly `__cf_bm`
cookie for `libreview.io`. Cloudflare documents that this cookie supports its
bot-management decisions and that cookies can distinguish clients sharing an
IP address. Losing that state between login and the next request is a plausible
compatibility defect. It is not a captured explanation of the owner's failure.

The source now retains Secure cookies in memory for exact reviewed HTTPS origins
(host and port), including provider-issued parent-domain cookies needed across
bootstrap and regional calls. It respects cookie domain, host-only scope, path,
expiry, replacement and deletion. It bounds storage to 64 cookies and rejects
cookies whose combined fields exceed 4,096 characters. It never persists or
logs cookie values. New sign-in and sign-out replace the active cookie jar;
responses on an old captured client cannot refill the active jar after clearing.
A single login/region sequence or bounded version retry uses the same captured
client. Credentials and bearer-token storage remain owned by the controller.

This candidate changes no User-Agent, product/version header, TLS validation,
HTTP protocol preference, request cadence, or VPN route. User-Agent/TLS
fingerprinting remains a second-line hypothesis from other maintained clients;
we deliberately vary cookie handling first. An HTML/security-page response stays
a typed failure; it is never parsed as a reading or treated as a successful
connection. The retained status/type helps diagnose a remaining failure.

The patch does not route around the VPN. Android gives the VPN control over
whether applications can bypass it; Proton's split tunneling is a paid feature.
Neither constraint proves that the owner's live failure is a VPN IP rejection.

## Verification

- Phone JVM suite after cookie/deletion correction: 189 tests passed, zero failures/errors/
  skips (2026-10-03); diagnostic-only phase had 179 passing tests.
- Debug assembly and debug lint: passed with final production code and the
  debug-only native verification fixture. Clean release signing separately
  runs phone/watch unit suites and both debug/release lint variants.
- Added synthetic transport cases: HTTP 403 HTML rejection without retry or
  private-data retention; HTTP 200 HTML challenge rejected as a failure; JSON
  schema failure retaining safe metadata.
- Added six cookie-jar cases for origin/domain/TLS/path scope, ordering, expiry,
  replacement/deletion, and storage bounds. Added three real HTTPS loopback
  transport cases: a login-issued parent cookie is required by the synthetic
  regional login and shared-person endpoint; new sign-in/explicit clearing drop
  prior cookie state; an in-flight late response cannot refill the active jar.
  The test dependency `okhttp-tls` supplies synthetic certificates trusted only
  by the test client; production TLS validation remains unchanged.
- Added controller case: response metadata reaches diagnostics while the last
  validated reading remains unchanged and no reauthentication is attempted.
  A separate controller case verifies transport clearing on account start and sign-out.
- Native API 36 emulator: seven checks passed. HTTP 403/web-page details are
  visible on both setup error screens; Try again dispatches once. Actual Android
  preferences retain safe metadata across recorder recreation and clear it on a
  network error, successful poll, and deletion. See
  [native evidence](evidence/api36-vpn-diagnostics.json) and the two DEMO screenshots
  it lists. This native run used 0.22.0 with the final cookie-deletion correction.
  Repeat it with `python3 scripts/validate_vpn_diagnostics.py emulator-5556`
  after installing the debug APK on the API 36 emulator.
  These are synthetic checks, not live VPN compatibility evidence.
- Live Proton/account requests, real phone behavior, and actual compatibility
  correction: unverified. The owner must reproduce using an installable build
  with Proton enabled. If setup still fails, share the new HTTP status/type shown
  below the error; that contains no credentials or response contents.

## References

- Android VPN bypass control:
  https://developer.android.com/reference/android/net/VpnService.Builder#allowBypass()
- Proton split tunneling:
  https://protonvpn.com/support/protonvpn-split-tunneling
- Cloudflare cookies:
  https://developers.cloudflare.com/fundamentals/reference/policies-compliances/cloudflare-cookies/
- Cloudflare challenge response detection:
  https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/detect-response/
- OkHttp 4.12.0 defaults (revision `4984568367caaf359b82c452bd28b5e192824d1c`):
  https://github.com/square/okhttp/blob/4984568367caaf359b82c452bd28b5e192824d1c/okhttp/src/main/kotlin/okhttp3/OkHttpClient.kt
- GlucoDataHandler reviewed revision `215b2f071f86942a5b9714cbf88808a71708a33f`:
  https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/common/src/main/java/de/michelinside/glucodatahandler/common/tasks/LibreLinkSourceTask.kt

## Independent diagnostic review

The bounded read-only review found no HIGH or MEDIUM diagnostic-patch blocker.
Its missing-metadata LOW for unsupported/repeated JSON region redirects was
resolved afterward. A body-read IOException still reports a metadata-free
NETWORK error, preserving prior behavior; that is not evidence that no headers
ever arrived. Setup failures retain the preexisting `POLL_` diagnostic event
naming. The bounded cookie/session review found no HIGH or MEDIUM source blocker.
Its LOW finding identified expired cookie deletion without a repeated Secure
attribute; this was corrected after reproducing the failure in a regression test.
Only reviewed HTTPS origins may delete stored state; live insecure cookies remain
rejected. The review covered the candidate on base `21a3f08` with uncommitted
changes; the final correction adds one focused cookie test.

The public config cookie observation does not prove that the owner's login
response issues the needed cookie. Production does not call public country-config.
Cookie state is deliberately volatile; after process death a retained valid bearer
session starts without cookies. If subsequent endpoints do not seed them, a fresh
sign-in may be needed. Both are unverified live compatibility boundaries.
