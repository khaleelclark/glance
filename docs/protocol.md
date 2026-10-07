# LibreLinkUp protocol discovery record

Status: source-backed discovery baseline plus implemented `GlucoseSource`
adapter and synthetic transport tests. Authenticated owner verification remains
outstanding.

Inspection date: 2026-09-27 (UTC).

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
private strict JSON reader. It implements the frozen `GlucoseSource` contract
without changing shared models or build files. Focused JVM tests use
MockWebServer plus synthetic resources under `app/src/test/resources/librelinkup`.

The frozen API deliberately leaves several orchestration responsibilities
outside the transport adapter:

- `AUTH_EXPIRED` tells the credential/session owner to perform the one
  serialized reauthentication and at most one replay; the adapter never has
  credentials during `connections` or `latest`.
- `RATE_LIMITED` includes an absolute `retryAfter`; the monitoring layer must
  persist and enforce that deadline across process restarts.
- A successful empty connection list is the frozen representation of “no
  shared people.” Comparing a persisted selected ID against that list and
  rejecting late or wrong-person responses belongs to the repository/runtime.
- Duplicate, out-of-order, and same-time-correction decisions require the
  stored prior reading, so they remain repository tests rather than stateless
  HTTP adapter tests.

Two useful distinctions do not fit the current shared enum. A status-920 retry
that still fails or supplies a malformed minimum version maps to `PROTOCOL`
rather than a distinct client-upgrade result. Also, the shared `Reading` cannot
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

No upstream code has been copied into Glance yet. If later implementation copies
or closely adapts a substantial portion, retain the MIT notice in the project
attribution and in redistributed source.

Additional primary product references:

- [Abbott: What is LibreLinkUp?](https://www.support.freestyle.abbott/hc/en-us/articles/36332326812433-What-is-the-LibreLinkUp-app)
- [Abbott: How to connect using LibreLinkUp](https://www.support.freestyle.abbott/hc/en-us/articles/45386328360721-How-do-I-connect-using-LibreLinkUp)
- [Official App Store version history](https://apps.apple.com/us/app/librelinkup/id1234323923)

## Protocol summary

All observed calls use HTTPS and JSON. A successful envelope has top-level
`status: 0` and `data`. HTTP 200 alone is not success: a nonzero JSON `status`,
missing mandatory fields, or an invalid measurement must be returned as a typed
failure.

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

1. Start only at the configured bootstrap host (`api.libreview.io`, or the
   explicitly supported Russian service when that region is selected).
2. Accept a login redirect only when `data.redirect` is exactly `true` and
   `data.region` maps to an exact host in the compiled allowlist above.
3. Permit at most one regional redirect per sign-in operation. Repeat login at
   the mapped host; do not forward an Authorization token because the first
   request has not established a session.
4. Reject unknown regions, full URLs, non-HTTPS schemes, user-info, ports,
   query fragments, lookalike domains, and redirect chains as
   `UnsupportedRegion`. Do not concatenate an unchecked server value into a URL.
5. Persist the validated region key with the encrypted session. Clear it when
   the account changes. If a previously valid region stops working, retry once
   from the bootstrap host during serialized reauthentication.
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

Before login there is no user ID or token. Glance should omit `Account-Id` and
`Authorization` rather than logging or persisting empty secret-like fields,
unless an account test proves the server requires an empty `Account-Id` header.
The pinned source sends an empty `Account-Id` on login.

`4.17.0` is an observed compatible baseline, not a permanent API version. The
official app version has advanced since that value. The pinned source handles
HTTP 403 with envelope `status: 920` and `data.minimumVersion` by adopting the
minimum version and retrying. Glance should:

- validate `minimumVersion` as a bounded dotted numeric version;
- persist the last accepted version as protocol metadata, not as a secret;
- retry the failed operation at most once after updating it;
- return `ClientUpgradeRequired` if the retry fails or the value is malformed;
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

- Encrypt the token, user ID, region, expiry, and reconnect credentials at rest.
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

Glance must **not** call that continuation endpoint or accept legal documents
on the user's behalf. Map any status-4 step to `TermsOrPolicyActionRequired`,
retain only the non-sensitive step category needed for diagnostics, discard the
ticket, stop automatic authentication, and direct the user to the official
LibreLinkUp app. **Try again** is an explicit user action after acceptance.

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
only identifier Glance should persist for the selected person. Names are for
the selection UI, not identity matching. Even if exactly one connection is
returned, Glance must show it for explicit confirmation rather than silently
selecting it.

An empty list before any successful selection means `NoConnections` (usually
an invitation has not been accepted). If a persisted `patientId` is absent
from a later nonempty list, return `SelectedConnectionRemoved`; never switch to
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
| `FactoryTimestamp` | Naive string in `M/d/yyyy h:mm:ss a` form, treated by the pinned client as UTC. Canonical source time only after account verification confirms this assumption. Parse strictly with `Locale.US`; never with the viewer timezone. |
| `Timestamp` | Source-local wall time with no offset. It is ambiguous and unsafe as a canonical instant. Keep only for redacted diagnostics during verification; do not parse it in the viewer timezone. |
| `ValueInMgPerDl` | Canonical numeric mg/dL used by the pinned client. Require a finite, plausible integer and preserve the exact source value. A missing value is not zero. |
| `GlucoseUnits` | Upstream observation: `0` = mmol/L, `1` = mg/dL. Reject/mark unknown for any other code. |
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

Released Glance used the strict current-measurement parser for graph rows,
requiring `GlucoseUnits` and `Value`; that rejects valid minimal historical
rows. Current GlucoDataHandler revision
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
a changed canonical correction cannot inherit that old reported value.

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
replace only when the validated source payload differs and record the decision;
this policy requires a focused fixture and later live observation.

`FactoryTimestamp`'s UTC interpretation, special HI/LO semantics, exact numeric
ranges, and correction behavior all need live-account verification. Until then,
failure to establish a trustworthy source instant is `InvalidTimestamp`, never
fresh data.

## Errors, retry, and cooldown behavior

### Typed mapping

| Evidence | Glance outcome | Automatic behavior |
|---|---|---|
| Transport unavailable / DNS failure | `Offline` | Wait for connectivity, then resume subject to existing backoff. |
| Connect/read timeout | `TransientFailure` | Exponential backoff about 1, 2, 4, 8, then 15 minutes, with jitter. |
| HTTP 429 | `RateLimited(retryAt)` | Honor a valid `Retry-After`; otherwise 15 minutes. Persist the deadline across restart. |
| HTTP 401 or verified expired-session response | internal `SessionExpired` | One serialized reauthentication and one replay, then `SignInRequired`. |
| HTTP 403 + status 920 + valid `minimumVersion` | internal `ClientVersionRejected` | Update once and retry once; otherwise `ClientUpgradeRequired`. |
| Login envelope status 2 | `InvalidCredentials` | Stop automatic login; require user action. The status-2 meaning is pinned-source evidence and still needs account verification. |
| Envelope status 4 / step | `TermsOrPolicyActionRequired` | Stop automatic login; user completes the step in official app. |
| Empty connections | `NoConnections` | Do not poll graph. User accepts invitation and retries. |
| Selected ID missing | `SelectedConnectionRemoved` | Stop graph requests; never substitute another person. |
| HTTP 5xx | `TransientFailure` | Bounded exponential backoff with jitter. |
| Other HTTP 4xx | `ProtocolOrAccountFailure` | Do not blindly reauthenticate; expose redacted category and require reviewed mapping. |
| HTTP 200 + nonzero unknown status | `UpstreamRejected(status)` | No immediate retry loop; retain last valid reading. |
| Malformed JSON / missing mandatory field / invalid value | `ProtocolError` | Reject response, retain last valid reading, back off. |

The pinned GlucoDataHandler implementation is useful evidence but not the retry
policy to copy: it resets the session for every HTTP error, retries once, and
uses a five-minute interval after 429 without reading `Retry-After`. Glance uses
the stricter policy above to avoid authentication storms and provider abuse.

All automatic and manual fetches share one single-flight gate, local refresh
debounce, backoff state, and persisted `nextAllowedAttempt`. Manual Refresh may
coalesce with an active request but cannot bypass a provider cooldown. A
successful response resets transient backoff but not source measurement age.

HTTP timeouts must be bounded; the product-level overall budget is 30 seconds.
Do not automatically retry non-idempotent login more than the single controlled
sequence described above. Never retry the legal continuation endpoint because
Glance does not call it.

## Parsing and schema policy

- Parse into DTOs that tolerate unknown fields.
- Validate mandatory envelope and operation-specific fields before producing a
  domain result.
- Treat `null`, missing, wrong-type, overflow, NaN/infinity, impossible unit,
  and invalid timestamp distinctly in tests even if they share the public
  `ProtocolError` category.
- Never persist complete provider payloads in production.
- Percent-encode the selected `patientId` as one URL path segment; never allow
  it to alter the host or path structure.
- Bound response sizes and collection lengths before allocation/persistence.
- Redact error bodies. Diagnostics may include HTTP class, envelope status,
  operation, region key, and retry time, but not provider messages when they
  might contain account or patient data.

## Synthetic fixture catalog

Adapter and later orchestration tests use only synthetic identifiers and
values. The fixture names below remain the minimum end-to-end contract; cases
that need stored prior state, persisted cooldowns, or concurrent credential
refresh belong to the repository/monitoring integration rather than the
stateless HTTP adapter:

| Fixture | Required assertion |
|---|---|
| `login_success.json` | Captures token, Unix-second expiry, user ID hash input; no secret reaches logs. |
| `login_redirect_us.json` | Routes once to exact allowlisted US host. |
| `login_redirect_unknown.json` | Unknown/full-URL region is rejected without a second request. |
| `login_invalid_credentials.json` | Status 2 becomes `InvalidCredentials`; no loop. |
| `login_terms_required.json` | Status 4 `tou` becomes required user action; no continuation call. |
| `login_policy_required.json` | Status 4 `pp` becomes required user action; no continuation call. |
| `login_minimum_version.json` | 403/status 920 updates a valid version once; malformed versions fail closed. |
| `connections_one.json` | One connection is returned for explicit confirmation, not auto-selection. |
| `connections_multiple.json` | Every synthetic person is listed and selection persists by `patientId`. |
| `connections_empty.json` | Becomes `NoConnections`. |
| `connections_selected_removed.json` | Missing persisted ID never selects a replacement. |
| `graph_current_mgdl.json` | Numeric value, units, trend, and factory timestamp normalize exactly. |
| `graph_current_mmol.json` | Source display value is preserved and canonical mg/dL stays stable. |
| `graph_duplicate.json` | Same measurement time does not become fresh on receipt. |
| `graph_out_of_order.json` | Older response cannot replace current data. |
| `graph_same_time_correction.json` | Deterministic correction policy is exercised. |
| `graph_high_low_flags.json` | Flags are preserved without inventing a precise special value. |
| `graph_unknown_trend.json` | Unknown trend maps to `UNKNOWN`. |
| `graph_future_timestamp.json` | More than two minutes in the future is not fresh. |
| `graph_dst_local_ambiguous.json` | Local `Timestamp` is never interpreted in viewer timezone. |
| `graph_missing_factory_timestamp.json` | Fails as `InvalidTimestamp` until fallback semantics are verified. |
| `graph_extra_fields.json` | Unknown fields are tolerated. |
| `graph_malformed.json` | Invalid/missing mandatory data is rejected and prior reading retained. |
| `http_429_seconds.txt` | Delta-seconds `Retry-After` creates a persisted cooldown. |
| `http_429_date.txt` | HTTP-date `Retry-After` is parsed against injected clock. |
| `http_429_missing.txt` | Missing/invalid header uses the 15-minute default. |
| `http_500.txt` | Transient backoff advances and is bounded. |
| `session_expired.json` | Exactly one serialized reauthentication/replay across concurrent callers. |

Test transport history must assert host, method, path, safe headers, request
count, and timing. Fixture payloads must never contain copied real IDs, names,
tokens, glucose readings, or timestamps.

## Verification still required with the owner's account

The owner should enter credentials only in a debug/release candidate installed
on the emulator or phone. Record results without raw bodies or identifiers:

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

Until those checks pass, this document is a source-backed implementation plan,
not a claim that the owner's account, region, or current service behavior has
been authenticated end to end.
