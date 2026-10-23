# LibreLinkUp protocol and discovery record

Status: source-backed discovery baseline plus implemented `GlucoseSource`
adapter and synthetic transport tests. Authenticated owner verification remains
outstanding.

Initial upstream/public-config inspection: 2026-09-27 (UTC).
Implemented-contract audit: 2026-10-11 against Glance 0.30.0 source `57a0902`.
Pinned discovery observations retain their original dates; they are not new live requests.

## Scope and confidence

LibreLinkUp does not publish a supported public API for this use. The contract
below is therefore an unofficial, reverse-engineered integration and can change
without notice. Abbott's documentation confirms the product-level flow (a
follower accepts an invitation and receives glucose data over an Internet
connection), but it does not document the HTTP endpoints or schemas used here.

This record separates three kinds of evidence:

- **Pinned source** means behavior read from the exact GlucoDataHandler revision
  below.
- **Live public config** means a credential-free request made on the inspection
  date to LibreView's country configuration endpoint.
- **Needs account verification** means it must be exercised later by the owner
  signing in inside Glance. No credentials, patient data, or authenticated
  responses were used during this discovery.

This adapter must never be presented as a primary glucose monitor or as a basis
for treatment decisions. The official product describes LibreLinkUp as a tool
for family, friends, and caregivers, dependent on Internet connectivity, and
not intended for dosing decisions.

### Implemented boundary

The adapter is implemented in
`app/src/main/java/com/khaleel/glance/librelinkup/LibreLinkUpSource.kt` with a
private strict JSON reader. It implements `GlucoseSource` from `core/Contracts.kt`, including `snapshot`
for separate current and historical results. Focused JVM tests use
MockWebServer plus synthetic resources under `app/src/test/resources/librelinkup`.

The API leaves several orchestration responsibilities
outside the transport adapter:

- `AUTH_EXPIRED` tells the credential/session owner to perform the one
  serialized reauthentication and at most one replay; the adapter never has
  credentials during `connections` or `latest`.
- `RATE_LIMITED` includes an absolute `retryAfter`; the monitoring layer must
  persist and enforce that deadline across process restarts.
- A successful empty connection list is the representation of “no
  shared people.” Comparing a persisted selected ID against that list and
  rejecting late or wrong-person responses belongs to the repository/runtime.
- Current duplicate/out-of-order/correction decisions use stored state in the
  controller. History duplicates are also folded within each adapter response
  using the guarded helper shared with store merge, then sorted/capped.

Two useful distinctions do not fit the current shared enum. A malformed status-920 minimum
version or a repeated 920 on the single replay maps to `PROTOCOL` rather than a
distinct client-upgrade result. Other replay failures retain their typed outcome. Also, the shared `Reading` cannot
retain unverified `isHigh`/`isLow` flags separately; the adapter leaves
`specialValue` null and never invents `HIGH` or `LOW`. Add shared outcomes only
if the UI needs to distinguish these cases before live verification.

## Pinned upstream and attribution

Primary implementation reference:

- Project: [pachi81/GlucoDataHandler](https://github.com/pachi81/GlucoDataHandler)
- Revision: [`114460bc29973c78580095e2e9b0f212eed9df20`](https://github.com/pachi81/GlucoDataHandler/commit/114460bc29973c78580095e2e9b0f212eed9df20)
- Tag at that revision: `2.4.2`
- Commit date: 2026-07-20
- License: [MIT](https://github.com/pachi81/GlucoDataHandler/blob/114460bc29973c78580095e2e9b0f212eed9df20/LICENSE), copyright 2023 Michael Pach
- Protocol source: [`LibreLinkSourceTask.kt`](https://github.com/pachi81/GlucoDataHandler/blob/114460bc29973c78580095e2e9b0f212eed9df20/common/src/main/java/de/michelinside/glucodatahandler/common/tasks/LibreLinkSourceTask.kt)
- Shared HTTP/retry behavior: [`DataSourceTask.kt`](https://github.com/pachi81/GlucoDataHandler/blob/114460bc29973c78580095e2e9b0f212eed9df20/common/src/main/java/de/michelinside/glucodatahandler/common/tasks/DataSourceTask.kt) and [`HttpRequest.kt`](https://github.com/pachi81/GlucoDataHandler/blob/114460bc29973c78580095e2e9b0f212eed9df20/common/src/main/java/de/michelinside/glucodatahandler/common/utils/HttpRequest.kt)
- Multi-person selection: [`MultiPatientSourceTask.kt`](https://github.com/pachi81/GlucoDataHandler/blob/114460bc29973c78580095e2e9b0f212eed9df20/common/src/main/java/de/michelinside/glucodatahandler/common/tasks/MultiPatientSourceTask.kt)
- Follower setup: [`SOURCES.md`](https://github.com/pachi81/GlucoDataHandler/blob/114460bc29973c78580095e2e9b0f212eed9df20/SOURCES.md)

These pinned sources document protocol discovery and attribution. If future work
copies or closely adapts a substantial portion of upstream code, retain its MIT
notice in the project attribution and redistributed source.

Additional primary product references:

- [Abbott: What is LibreLinkUp?](https://www.support.freestyle.abbott/hc/en-us/articles/36332326812433-What-is-the-LibreLinkUp-app)
- [Abbott: How to connect using LibreLinkUp](https://www.support.freestyle.abbott/hc/en-us/articles/45386328360721-How-do-I-connect-using-LibreLinkUp)
- [Official App Store version history](https://apps.apple.com/us/app/librelinkup/id1234323923)

## Protocol summary

All observed calls use HTTPS and JSON. A successful envelope has top-level
`status: 0` and `data`. HTTP 200 alone is not success: a nonzero JSON `status`,
missing mandatory operation fields, or an invalid required current measurement
returns a typed failure. Invalid individual historical graph rows are dropped;
they do not fail an otherwise valid current snapshot.

### Hosts and regional routing

The pinned client starts at:

```text
https://api.libreview.io
```

It also permits the Russian top-level domain `https://api.libreview.ru` as a
manual setting. For an `.io` regional redirect, the source constructs:

```text
https://api-{region}.libreview.io
```

On 2026-09-27, a credential-free request to:

```text
GET https://api.libreview.io/llu/config/country?country=US&version=4.17.0
```

returned HTTP 200, JSON `status: 0`, `MinVersion: "4.16.0"`, and this regional
map:

| Redirect key | Exact allowed base host |
|---|---|
| `ae` | `https://api-ae.libreview.io` |
| `ap` | `https://api-ap.libreview.io` |
| `au` | `https://api-au.libreview.io` |
| `ca` | `https://api-ca.libreview.io` |
| `de` | `https://api-de.libreview.io` |
| `eu` | `https://api-eu.libreview.io` |
| `eu2` | `https://api-eu2.libreview.io` |
| `fr` | `https://api-fr.libreview.io` |
| `jp` | `https://api-jp.libreview.io` |
| `la` | `https://api-la.libreview.io` |
| `us` | `https://api-us.libreview.io` |

Glance routing rules:

1. Production starts at `https://api.libreview.io/`. Russian routing was observed
   upstream but is not selectable or allowed in Glance.
2. Accept a login redirect only when `data.redirect` is exactly `true` and
   `data.region` maps to an exact host in the compiled allowlist above.
3. Permit at most one regional redirect per sign-in operation. Repeat login at
   the mapped host; do not forward an Authorization token because the first
   request has not established a session.
4. Reject unknown regions, full URLs, non-HTTPS schemes, user-info, ports,
   query fragments, lookalike domains, and redirect chains as
   `SourceErrorKind.PROTOCOL`. Do not concatenate an unchecked server value into a URL.
5. Persist the validated canonical `regionHost` with the encrypted session.
   Controller reauthentication starts at the bootstrap host. The transport
   does not independently discard/retry sessions on arbitrary region failures.
6. Use normal platform TLS validation. Do not install a trust-all client or
   disable hostname verification.

The live public request validates the `.io` map only. Russian service behavior,
regions not present in that map, and whether every map entry accepts this
follower account remain unverified. The allowlist should be updated from a
reviewed release, not expanded from arbitrary redirect data at runtime.

### Common headers and client version

The pinned Android client sends:

```http
product: llu.android
version: 4.17.0
Accept: application/json
Content-Type: application/json
cache-control: no-cache
Account-Id: <lowercase SHA-256 hex of trimmed data.user.id>
Authorization: Bearer <authTicket.token>   # authenticated calls only
```

Before login Glance omits `Account-Id` and `Authorization`. The pinned upstream
source sends an empty `Account-Id` on login; an owner-account comparison of this
difference remains outstanding.

`4.17.0` is an observed compatible baseline, not a permanent API version. The
official app version has advanced since that value. The pinned source handles
HTTP 403 with envelope `status: 920` and `data.minimumVersion` by adopting the
minimum version and retrying. Glance implements:

- validate `minimumVersion` as a bounded dotted numeric version;
- retain the accepted version only in a process-local `AtomicReference`;
  recreation starts again at baseline `4.17.0`, not a persisted negotiated version;
- replay the failed operation once for any syntactically valid minimum, even
  when equal to or below the current version; only an advancing minimum changes
  the stored version;
- fail closed with `PROTOCOL` on a malformed minimum without replay or a repeated
  920 on replay; other typed failures from the single replay remain typed;
- never loop on changing minimum-version responses.

The public country-config endpoint reported minimum `4.16.0` on the inspection
date, but this did not prove that authenticated calls accept `4.17.0` for the
owner's region.

### Login and session

```http
POST /llu/auth/login
```

Request body:

```json
{
  "email": "follower@example.invalid",
  "password": "synthetic-password"
}
```

Do not use the sensor wearer's account. This is the follower's LibreLinkUp
account that has accepted the invitation.

Successful login data includes:

```json
{
  "status": 0,
  "data": {
    "user": { "id": "synthetic-user-id" },
    "authTicket": {
      "token": "synthetic-token",
      "expires": 1893456000,
      "duration": 15552000000
    }
  }
}
```

`authTicket.expires` is interpreted by the pinned source as Unix seconds and
converted to milliseconds. `duration` is present in an upstream example but is
not used there; it must not override `expires` without live verification.
`data.user.id` is the account identifier. Authenticated requests send the
lowercase SHA-256 hex digest of its trimmed UTF-8 bytes as `Account-Id`, never
the raw ID.

Session rules for Glance:

- Encrypt the token, hashed account ID, canonical region host, expiry and
  reconnect credentials at rest. `Session.accountId` contains SHA-256 of the
  trimmed provider user ID; the raw user ID is not persisted.
- Reuse a session until its explicit expiry; do not log in on each poll.
- Refresh a little before known expiry to avoid racing the boundary.
- Serialize replacement behind one mutex/single-flight operation.
- On a known expired/unauthorized response, invalidate the old session,
  authenticate once, and retry the original request once.
- A second rejection becomes `SignInRequired`; automatic polling must stop
  attempting login. Do not create a login storm.
- Never log email, password, raw user ID, account hash, bearer token, response
  body, connection name, or patient ID.

A redirect-only login response is:

```json
{
  "status": 0,
  "data": { "redirect": true, "region": "us" }
}
```

This is routing, not authentication; it contains no usable session.

### Required user action and terms

The pinned source treats envelope `status: 4` as a required step. Examples have
`data.step.type` equal to `tou` (terms of use) or `pp` (privacy-policy/token
continuation), and may include a short-lived `authTicket`. It also knows an
`/auth/continue/{type}` endpoint and can auto-accept terms.

Glance does **not** call that continuation endpoint or accept legal documents
on the user's behalf. Any status-4 step maps to `TERMS_REQUIRED`. Glance retains
no ticket or raw step payload, including no step category; it exposes only the
typed failure, stops automatic authentication, and directs the user to the
official LibreLinkUp app. **Try again** is an explicit user action after acceptance.

### Connections and selected person

```http
GET /llu/connections
```

The successful `data` is an array. Observed connection fields include:

```json
{
  "patientId": "synthetic-patient-1",
  "firstName": "Pat",
  "lastName": "Example",
  "sensor": {
    "deviceId": "synthetic-device",
    "sn": "SYNTHETIC-SERIAL"
  },
  "glucoseMeasurement": { "...": "same measurement shape described below" }
}
```

`patientId` is the stable selection key used by the pinned client and is the
only identity key Glance persists for the selected person. The encrypted
selected-person store also caches the display name for presentation; names are
never used for identity matching. Even if exactly one connection is
returned, Glance must show it for explicit confirmation rather than silently
selecting it.

An empty list before any successful selection means `NoConnections` (usually
an invitation has not been accepted); it is a successful empty list, not a
separate adapter error. If a persisted `patientId` is absent from a refreshed
list (including an empty list), the controller returns `CONNECTION_REMOVED`; never switch to
the first remaining person. Changing accounts or people increments the request
generation, clears the displayed reading immediately, and causes late results
for the old generation to be discarded.

### Latest reading and graph history

```http
GET /llu/connections/{percent-encoded-patientId}/graph
```

The pinned source reads the latest measurement from:

```text
data.connection.glucoseMeasurement
```

and historical samples from:

```text
data.graphData[]
```

The endpoint also includes `data.connection.sensor`; observed sensor fields
include `sn` and `a` (the latter is treated upstream as sensor-start Unix
seconds). Glance does not need to persist the serial number or sensor metadata
for version 1.

Observed measurement shape:

```json
{
  "FactoryTimestamp": "9/27/2026 4:42:00 AM",
  "Timestamp": "9/27/2026 5:42:00 AM",
  "type": 1,
  "ValueInMgPerDl": 112,
  "TrendArrow": 3,
  "TrendMessage": null,
  "MeasurementColor": 1,
  "GlucoseUnits": 1,
  "Value": 112,
  "isHigh": false,
  "isLow": false
}
```

Field handling:

| Field | Meaning and validation |
|---|---|
| `FactoryTimestamp` | Strict `M/d/uuuu h:mm:ss a`, Locale.US, interpreted as UTC by Glance following pinned source. Invalid or >2-minute-future time is rejected; live-account validation of UTC semantics remains outstanding. |
| `Timestamp` | Ambiguous source-local wall time without offset. Ignored, not a fallback instant and not retained/exported in diagnostics. |
| `ValueInMgPerDl` | Canonical numeric mg/dL used by the pinned client. Require a finite, plausible integer and preserve the exact source value. A missing value is not zero. |
| `GlucoseUnits` | Upstream observation: `0` = mmol/L, `1` = mg/dL. Current parsing rejects missing/unknown units. Historical rows with valid canonical fields fall back to `sourceUnit=MG_DL`, `sourceValue=null` when the full reported value/unit cannot be parsed. |
| `Value` | Provider display value in the reported source unit. Preserve for parity checks; do not repeatedly convert it back and forth. |
| `TrendArrow` | `1` falling quickly/down, `2` falling/down-right, `3` steady/right, `4` rising/up-right, `5` rising quickly/up. Any other or missing value maps to `UNKNOWN`; never calculate a replacement arrow. |
| `isHigh`, `isLow` | Provider booleans whose precise distinction between range flags and special HI/LO display values is not established by the pinned client. Do not invent a precise number or map them to `HIGH`/`LOW` until a live source comparison verifies semantics. |
| `type`, `TrendMessage`, `MeasurementColor` | Observed but not required for the version-1 reading model. Tolerate and ignore unless later evidence assigns a needed meaning. |

The current measurement and graph samples serve different purposes and do not
necessarily contain the same fields. `connection.glucoseMeasurement` is the latest
snapshot; `graphData` is bounded history and may omit the latest sample, contain
duplicates, or arrive in an unspecified order. Normalize each independently,
sort validated history by canonical UTC time, deduplicate deterministically,
and never let an older graph item replace a newer current reading.

### History field correction, 7 October 2026 (0.30)

Before 0.30, Glance used the strict current-measurement parser for graph rows,
requiring `GlucoseUnits` and `Value`; that rejects valid minimal historical
rows. The GlucoDataHandler revision inspected for that correction
[`215b2f071f86942a5b9714cbf88808a71708a33f`](https://github.com/pachi81/GlucoDataHandler/blob/215b2f071f86942a5b9714cbf88808a71708a33f/common/src/main/java/de/michelinside/glucodatahandler/common/tasks/LibreLinkSourceTask.kt#L444-L461)
uses the timestamp and canonical `ValueInMgPerDl` for graph data. A
[nightscout-connect regression fixture](https://github.com/nightscout/nightscout-connect/blob/4dde1ecdcad1fd923e69d458e5e2faa7bc78e4f1/test/librelinkup.test.js#L138-L157)
at `4dde1ecdcad1fd923e69d458e5e2faa7bc78e4f1` explicitly contains only those two
fields in a historical row. This is primary implementation evidence of a
supported minimal shape, not observation of the owner's live response.

Glance 0.30 keeps current parsing strict. Graph rows require a valid canonical
factory UTC timestamp and positive, finite integer mg/dL value <=1,000. A full
valid row retains its reported display value/unit. A minimal row has
`sourceValue = null`, canonical `sourceUnit = MG_DL`; display conversion uses
the genuine canonical value. Missing/unsupported trend remains UNKNOWN.
The source-local `Timestamp` fallback remains rejected because it lacks a
verified offset. History must precede current source time; sorting, correction
deduplication and future-time checks remain. Adapter/load/merge share the
1,500-point bound rather than truncating graph imports to 300. A same-timestamp,
same-canonical minimal row preserves a previously captured reported value/unit;
a changed canonical correction cannot inherit that old reported value. The same
rule applies during adapter deduplication within the first graph response, so
full/minimal duplicate order cannot discard genuine provider display precision.
A later full row wins; a later missing trend remains UNKNOWN.

The [nightscout-connect source notes](https://github.com/nightscout/nightscout-connect/blob/4dde1ecdcad1fd923e69d458e5e2faa7bc78e4f1/README.md#L365-L369)
describe delayed graph backfill with a bounded returned window and no requested
date range. Neither this nor the examined client code establishes a guaranteed
one-minute or fifteen-minute history cadence. The owner's recording shows
coarser older segments and denser recent samples, but source keys/counts/timestamp
deltas and actual device poll timings are needed to attribute particular gaps.

The adapter must propagate source measurement time separately from HTTP receipt
time. A successful response containing the same timestamp is a duplicate: it
updates last-successful-contact only, not measurement age. A response older
than stored current data is out of order. For a same-timestamp correction,
replace only when the validated source payload differs and the incoming receipt
is equal or newer. `ReadingPolicyTest` covers this policy; diagnostics does not record
duplicate/out-of-order/correction decisions. Live observation remains outstanding.

`FactoryTimestamp`'s UTC interpretation, special HI/LO semantics, exact numeric
ranges, and correction behavior all need live-account verification. Until then,
failure to parse the required source instant returns `PROTOCOL`, never fresh
data. `ReadingFreshness.INVALID_TIME` is a display-policy state, not an adapter
error named InvalidTimestamp.

## Errors, retry, and cooldown behavior

### Typed mapping

The following names are actual `SourceErrorKind` values, with controller states
shown where relevant; discovery shorthand is not an additional public enum.

| Evidence | Implemented outcome | Controller/scheduler behavior |
|---|---|---|
| DNS/transport/body-read IOException, including network timeout | `NETWORK` → `OFFLINE` | Persist transient backoff; retry when eligible. No connectivity-callback wake guarantee |
| Poll coordinator's 30-second overall timeout | `SERVER` → `BACKING_OFF` | Record one timeout/failure and transient delay |
| HTTP 429 | `RATE_LIMITED(retryAfter)` | Delta-seconds/date Retry-After bounded to 7 days; otherwise 15 minutes; persist provider deadline across restart |
| HTTP 401 / invalid or expired session | `AUTH_EXPIRED` | Controller serializes one reauthentication/replay; repeated failure requires explicit sign-in |
| HTTP 403 + status 920 + syntactically valid minimumVersion | One bounded version replay, including equal/lower minimum | Malformed minimum/repeated 920 → `PROTOCOL`; only an advancing minimum updates the process-local version |
| Login status 2 | `INVALID_CREDENTIALS` | Stop automatic authentication; user action required |
| Authenticated status 2 | `PROTOCOL` | Retain saved reading; explicit recovery, no automatic reauthentication |
| Status 4 | `TERMS_REQUIRED` | Stop automatic auth; complete action in official app; no legal continuation request |
| Empty connections | `Success(emptyList())` | Setup shows no shared people; selected ID missing on refresh → `CONNECTION_REMOVED` |
| Graph HTTP 404 / missing connection object | `CONNECTION_REMOVED` | Stop graph requests; never substitute a person |
| Non-graph 404 | `PROTOCOL` | Require explicit recovery; no retry/authentication loop |
| HTTP 5xx after bounded body read | `SERVER` | Transient exponential backoff; oversized body may instead fail `PROTOCOL` |
| Other HTTP/schema/status errors | `PROTOCOL` → `PROTOCOL_ERROR` | Retain saved reading. A retry deadline is stored, but scheduler blocks automatic polling in this state; use explicit recovery |
| Graph missing/null current measurement | `NO_READING` → `BACKING_OFF` | No fabricated zero/new timestamp; does not itself create an exponential retry window |

Transient backoff is approximately 1, 2, 4, 8 and 15 minutes plus 0–15 seconds
jitter. A provider deadline cannot be replaced by a transient failure. Transport
metadata is only numeric HTTP status and JSON/WEB_PAGE/OTHER format; exceptions
before/while reading a response may have no metadata. Unknown raw statuses or
provider text are not exported.

The pinned GlucoDataHandler implementation is useful evidence but not the retry
policy to copy: it resets the session for every HTTP error, retries once, and
uses a five-minute interval after 429 without reading `Retry-After`. Glance uses
the stricter policy above to avoid authentication storms and provider abuse.

All automatic and manual fetches share one single-flight gate, local refresh
debounce, backoff state, and persisted `RetryWindow` (`nextAllowedFetch` plus reason). Manual Refresh may
coalesce with an active request but cannot bypass a provider cooldown. Unreleased
audit fixes also gate explicit sign-in, connection discovery and session recovery.
Active provider deadlines survive clear/select-person and repeated sign-in;
explicit sign-out clears all settings. Transient failure counts reset across
identity boundaries, independently of provider cooldowns. A successful response resets transient backoff but not source measurement age.

The adapter bounds each HTTP call to at most 30 seconds; login with region/version
replay may involve more than one call. Automatic polling wraps the controller
operation in a 30-second overall `PollingPolicy` timeout. This is not an overall
30-second guarantee for every interactive sign-in/connections sequence.
Do not automatically retry non-idempotent login more than the single controlled
sequence described above. Never retry the legal continuation endpoint because
Glance does not call it.

## Parsing and schema policy

- Parse through the private strict JSON reader while tolerating unknown fields.
- Validate mandatory envelope and operation-specific fields before producing a
  domain result.
- Treat `null`, missing, wrong-type, overflow, NaN/infinity, impossible unit,
  and invalid timestamp distinctly in tests even if they share the public
  `PROTOCOL` category.
- Never persist complete provider payloads in production.
- Percent-encode the selected `patientId` as one URL path segment; never allow
  it to alter the host or path structure.
- Bound the response body before JSON parsing. Reject connections above the
  count limit. Graph rows are mapped/deduplicated/sorted after bounded-body
  parsing and capped before return/persistence, not before collection allocation.
- Never retain/export error bodies, raw headers, cookie values, envelope status,
  region or connection identifiers. Diagnostics retains fixed error categories,
  numeric HTTP status, coarse response format and source-free timing.
- Bounds: 1 MiB response body, 100 connections, display names limited to 200 UTF-16 code units (longer names rejected),
  512 UTF-8-byte connection IDs, 1,500 retained history points. Canonical mg/dL
  is finite/positive/integral <=1,000; full reported Value is finite/positive
  <=1,000 in valid unit code 0/1.

## Synthetic fixture catalog

Checked-in files under `app/src/test/resources/librelinkup`:

- [`connections_empty.json`](../app/src/test/resources/librelinkup/connections_empty.json)
- [`connections_multiple.json`](../app/src/test/resources/librelinkup/connections_multiple.json)
- [`graph_current_mgdl.json`](../app/src/test/resources/librelinkup/graph_current_mgdl.json)
- [`graph_current_mmol.json`](../app/src/test/resources/librelinkup/graph_current_mmol.json)
- [`graph_future_timestamp.json`](../app/src/test/resources/librelinkup/graph_future_timestamp.json)
- [`graph_high_low_flags.json`](../app/src/test/resources/librelinkup/graph_high_low_flags.json)
- [`graph_malformed.json`](../app/src/test/resources/librelinkup/graph_malformed.json)
- [`graph_minimal_history.json`](../app/src/test/resources/librelinkup/graph_minimal_history.json)
- [`graph_missing_factory_timestamp.json`](../app/src/test/resources/librelinkup/graph_missing_factory_timestamp.json)
- [`graph_unknown_trend.json`](../app/src/test/resources/librelinkup/graph_unknown_trend.json)
- [`graph_with_history.json`](../app/src/test/resources/librelinkup/graph_with_history.json)
- [`login_invalid_credentials.json`](../app/src/test/resources/librelinkup/login_invalid_credentials.json)
- [`login_minimum_version.json`](../app/src/test/resources/librelinkup/login_minimum_version.json)
- [`login_policy_required.json`](../app/src/test/resources/librelinkup/login_policy_required.json)
- [`login_redirect_unknown.json`](../app/src/test/resources/librelinkup/login_redirect_unknown.json)
- [`login_redirect_us.json`](../app/src/test/resources/librelinkup/login_redirect_us.json)
- [`login_success.json`](../app/src/test/resources/librelinkup/login_success.json)
- [`login_terms_required.json`](../app/src/test/resources/librelinkup/login_terms_required.json)

Additional inline synthetic MockWebServer cases in
[`LibreLinkUpSourceTest.kt`](../app/src/test/java/com/khaleel/glance/librelinkup/LibreLinkUpSourceTest.kt)
cover both mixed-shape duplicate orders, full-day backfill, strict current-only
fields, safe path segments, status/type diagnostics, malformed/oversized bodies,
401/404/status-2 mappings and Retry-After seconds/date/fallback. Cookie transport
and grant/merge/recovery/concurrency tests live in their respective test packages.
Duplicate/out-of-order/correction behavior using prior current state belongs to
`ReadingPolicyTest`; the adapter also tests duplicates within returned graph history.
The original proposed fixture names are not a claim that missing files exist.
All payloads use synthetic credentials/identities/values; no copied account data.

## Verification still required with the owner's account

The owner should enter real credentials only in an owner-controlled, personally
signed, non-debuggable release on the owner's device. Emulator/debug validation
uses synthetic accounts and fixtures. Record results without raw bodies or
identifiers:

1. Confirm bootstrap-to-region routing and that the returned key is in the
   allowlist.
2. Confirm the exact request headers accepted by login, especially whether an
   empty `Account-Id` is necessary.
3. Confirm `4.17.0` remains accepted for authenticated login or capture only the
   redacted minimum-version category.
4. Confirm token expiry units and whether successful non-login calls rotate an
   auth ticket.
5. Confirm connection identity and explicit selection of Patrick by comparing
   the visible name in LibreLinkUp; never export the ID.
6. Compare the latest value, trend, unit, and `FactoryTimestamp` with the
   official app at the same measurement timestamp.
7. Confirm UTC interpretation across the device timezone and a DST boundary.
8. Observe `isHigh`/`isLow` and actual LO/HI states before defining special
   value semantics.
9. Confirm graph ordering, whether current is duplicated in `graphData`, and
   any same-timestamp corrections.
10. Exercise an expired session, removed connection if safely reproducible, and
    provider cooldown behavior without intentionally creating a request storm.

Until those checks pass, this document is an implemented source-backed contract,
not a claim that the owner's account, region, or current service behavior has
been authenticated end to end.
