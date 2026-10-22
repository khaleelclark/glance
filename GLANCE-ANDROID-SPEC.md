# Glance: personal Android LibreLinkUp follower

Build specification, version 1.0 — 27 September 2026

Working name: **Glance**. User: Khaleel. Followed person: Patrick. Distribution: personally sideloaded, signed Android APK. Project folder on pine: **`/home/phoenix/khaleel/glance`**. Implementation environment: this separate Android project, built by Ponderosa managed agents on pine.

This document specifies the app to build. It is not a claim that the app, Abbott integration, or device behavior has already been tested. Numerical defaults and performance targets below are product decisions, not promises made by Abbott or Android.

## Current implementation addendum — 22 October 2026

The sections below preserve the 27 September baseline and its acceptance targets.
They are historical requirements, not the current installation guide. Use
[README](README.md), [setup](docs/setup.md), [release](docs/release.md) and the
[documentation index](docs/README.md) for the current 0.32.0 behavior and evidence.
Subsequent owner-directed changes supersede these baseline UI/scope defaults:

| Baseline | Current behavior |
|---|---|
| Phone-only; alerts/watch deferred | Optional four-category phone alerts, encrypted Logbook, passive widget and Wear OS companion added; all alert/device grants initially off |
| System light/dark presentation | Phone stays black/graphite in both modes; seven accent presets and matching native droplets; fresh reading card green/red/yellow |
| Main/notification Pause and Refresh | Refresh on Now; Stop monitoring in Settings; no new notification action buttons |
| Manual-only dismissal restore | Keep notification visible defaults on at owner request; a delivered callback restores the regular card, without automatic re-promotion |
| Suggested graph interaction | 3/6/12/24-hour presets, 6 default; hold/drag real samples, dismiss on release; no pinch/pan |
| Manual refresh/backoff sketch | Three-second monotonic debounce; transient retry delay may be bypassed manually; provider cooldowns remain mandatory |
| Proposed storage libraries | Android Keystore AES-GCM/AtomicFile for account/health stores; preferences for ordinary settings and checked privacy grants |
| History | Up to 24 hours/1,500 validated numeric timestamps; minimal historical rows accepted; guarded reported-value precision preserved; no fabricated missing samples |
| Launch from default icon | Studio Glance phone configuration explicitly launches always-enabled MainActivity, independent of selected accent alias |
| Numeric icons / Live Update | Regular Android 17+ numeric masks support automatic-group comparison; Live Update uses the theme accent, droplet and one value, with an explicit restore action after dismissal; Samsung appearance needs device verification |
| Watch installation | Glance watch Studio configuration or same-signer APK sideload via paired Wi-Fi ADB |

Current app identity: `com.khaleel.glance`, 0.32.0 / code 37 on phone and watch;
minSdk 30, compile/target SDK 37. Toolchain and signer instructions are in release.md.
Current verification remains narrower than the definition of done below:
controlled live-account parity, physical Samsung/watch/car, real TalkBack,
overnight/24-hour cadence and matched battery measurements are outstanding.
Historical test receipts retain their own pinned source/APK boundaries.

## 1. Outcome and scope

Khaleel signs in with the LibreLinkUp account that already follows Patrick, selects Patrick, and starts monitoring. Glance fetches the readings already available through LibreLinkUp and keeps the latest value, trend, and reading time visible in an ongoing Android notification. The app should remain useful with the screen locked and without regularly opening it.

The product does not need to improve the underlying glucose measurement. Faithful display of the source readings, roughly the convenience of Gluroo, and understandable connection status are the goals. Do not smooth, predict, recalibrate, or manufacture readings.

### Required for version 1

| ID | Capability | Required result |
|---|---|---|
| R01 | LibreLinkUp sign-in | Authenticate a follower account on the phone; retain credentials securely for unattended reconnects. |
| R02 | Person selection | List connections and explicitly confirm Patrick; remember the stable connection identifier. |
| R03 | Current reading | Display source value, unit, source trend, and source reading time. |
| R04 | Ongoing notification | Update one notification in place; visible in the notification shade and, when permitted, on the lock screen. |
| R05 | Background monitoring | Continue while another app is open and during screen-off use on the verified target phone. |
| R06 | Honest freshness | Distinguish a new reading from a successful request returning old data. |
| R07 | Recovery | Handle temporary network loss, expired sessions, service recreation, restart, and app upgrades. |
| R08 | User control | Start, pause, resume, sign out, and show/hide lock-screen values. |
| R09 | Diagnostics | Explain failures without exposing credentials or patient details in logs. |
| R10 | Delivery | Produce a signed APK, repeatable build instructions, source, and real test evidence. |

### Desirable after the core works

Numeric status-bar icon; an optional promoted Live Update presentation on eligible devices; a simple home-screen widget; optional expanded mini graph. Khaleel would like to try the Live Update if the phone supports it. Prototype it after the core notification works, enable it by default only when the user explicitly starts monitoring and the phone reports it is allowed, and provide a setting to turn the enhanced presentation off. Numeric status-bar work belongs in the first release if it renders legibly on Khaleel's phone, but must not delay the core notification.

### Outside version 1

Insulin or food logging, dose advice, prediction, treatment recommendations, direct Bluetooth/NFC sensor access, remote messages, smartwatch apps, Nightscout hosting, Health Connect, account registration, social features, advertisements, analytics, subscriptions, and Google Play publication. High/low alarms are a later feature; the initial app stays silent on routine updates.

## 2. Defaults and unresolved inputs

Proceed with these defaults without repeatedly asking for product decisions:

| Decision | Default |
|---|---|
| Language and UI | Native Kotlin, Jetpack Compose, Material 3, light/dark mode |
| App name | Glance, changeable before delivery |
| Minimum Android | Android 11 / API 30, unless the actual phone requires a change |
| Compile and target SDK | Latest stable supported by the chosen toolchain at implementation time; record exact versions |
| Distribution | Universal personal APK; no Play Store work |
| Network path | Phone directly to LibreLinkUp; no pine runtime server |
| Followed person | One selected connection; initially Patrick |
| Units | mg/dL initially; support explicit mmol/L selection and show units everywhere |
| Request cadence | 60 seconds, subject to protocol/rate-limit and device feasibility checks |
| Lower-traffic option | Five minutes, explicitly selected by the user |
| Sound/vibration | Off for routine readings and connection transitions |
| History | Up to 24 hours, local only, bounded retention |
| Lock-screen privacy | Ask during notification setup with a visual preview |
| Restart preference | Resume after reboot when monitoring was enabled; respect Android limits and user stops |

Target phone: **Samsung Galaxy S25 Ultra**, expected Wednesday after this spec was drafted. Ask for its actual Android and One UI versions after arrival; these are needed for final device qualification, not for starting the project. Before it arrives, build and test on an Android Studio virtual device using a supported system image as close as practical to the phone's Android version. Use synthetic data for repeatable tests; a real LibreLinkUp follower account can also be tested in the emulator if the owner signs in there. Determine whether pine has an Android SDK, JDK, suitable emulator acceleration, and an accessible virtual device. If not, build and unit-test on pine and run the emulator in Android Studio on a suitable local computer. Never assume a remote Android phone is connected to pine.

No LibreLinkUp password is needed in Ponderosa chat. The owner enters it into the installed app for the first live test. Confirm account region only if automatic region discovery fails.

## 3. User experience

### 3.1 First launch

1. Show a short welcome: “Keep Patrick's glucose reading in your notifications.” Use “a shared glucose reading” until a person is selected.
2. Present email and password fields, password visibility toggle, and **Sign in**. Explain in one sentence that this is the LibreLinkUp follower account.
3. On successful authentication, list available people. With one connection, show a confirmation card; with several, require selection. Never silently choose the first connection.
4. Retrieve the selected person's latest reading and show a notification preview.
5. Request Android notification permission where applicable, following a brief explanation of why it is the main feature.
6. Offer **Show readings on lock screen**, with a clear preview of what someone holding the phone can see. Store the explicit preference.
7. Offer the device's relevant battery/background settings, explain their practical effect, and read back settings that Android exposes.
8. **Start monitoring** starts the user-visible foreground service. Confirm monitoring only after service startup and notification posting succeed.

If a connection is absent, say: “No shared people found. Accept Patrick's invitation in LibreLinkUp, then try again.” Do not automatically send invitations, register accounts, or accept provider terms.

### 3.2 Main screen

One clean screen should contain:

- Selected person's name, large value and unit, and a readable trend arrow with an accessibility label.
- “Reading at 10:42 AM · 1 min ago,” driven by the measurement timestamp.
- Monitoring status, separate from reading freshness.
- **Pause/Resume**, **Refresh**, and **Settings**.
- A restrained connection message when necessary, such as “No internet. Showing last reading.”

Optional history goes below the essentials. Do not turn this into a diabetes management dashboard. Long names, large fonts, and dark mode must fit without hiding the glucose value.

### 3.3 Settings

Group a small set of controls under Display, Monitoring, and Account:

- Units: mg/dL or mmol/L.
- Lock-screen values: visible or hidden.
- Numeric status-bar icon: enabled where supported, otherwise standard app icon.
- Enhanced Live Update: try when supported and allowed; offer a switch to use only the standard notification.
- Refresh cadence: 1 minute or 5 minutes, with a brief battery/network tradeoff.
- Resume after restart: on by default once monitoring is enabled.
- Notification/background settings shortcuts and permission status.
- Change selected person, reconnect, diagnostics, sign out, and delete local data.

Changing person invalidates in-flight requests, clears the prior person's current display immediately, and then retrieves the new person's data. A response arriving late for Patrick must never populate another person's card.

## 4. Notification contract

### 4.1 Standard presentation

Use a single stable notification ID and a silent `IMPORTANCE_LOW` monitoring channel. The service's foreground notification is the glucose notification; do not add a second permanent “service running” notification.

Example, fresh reading:

```text
Patrick · 112 mg/dL →
Reading 10:42 AM · 1 min ago
[Refresh] [Pause]
```

Example, old source data:

```text
Patrick · Reading delayed
Last: 112 mg/dL → at 10:42 AM · 7 min ago
[Refresh] [Pause]
```

Example, stale data:

```text
Patrick · No recent reading
Last: 112 mg/dL at 10:42 AM · 18 min ago
[Refresh] [Pause]
```

The primary fresh layout must never be used for stale data. The timestamp is always available even if Android suspends the process before it can change a relative-age string. Avoid static “Live” badges or a green “connected” label that would imply continuing freshness after suspension.

Requirements:

- Use `setOngoing(true)`, `setOnlyAlertOnce(true)`, a small icon, and a content intent opening the main screen.
- Prefer standard notification layouts and `BigTextStyle` before custom `RemoteViews`.
- Tap actions use explicit, immutable intents where appropriate; validate the monitoring/session generation.
- A Refresh action goes through the same single-flight poller and rate limiter as automatic polling.
- Pause persists intent, cancels pending polling, and stops the foreground service. Remove the ongoing reading notification; a paused state remains in the app.
- Resume is a user action in the app; a Quick Settings tile can be added later.
- Updating a value must not beep, vibrate, repeatedly wake the screen, create multiple cards, or change notification importance.
- Use a redacted public version when lock-screen values are hidden. Respect system settings that hide all notification content.
- Include the unit and clear trend semantics in TalkBack output; do not communicate status using color alone.

Android 14 permits users to dismiss many ongoing notifications when unlocked. “Persistent” means maintained while monitoring and allowed by the OS, not impossible to remove. Test dismissal separately from stopping the app; provide **Show notification again** in the main screen and do not fight an explicit dismissal with a repost loop. [Android notification behavior](https://developer.android.com/about/versions/14/behavior-changes-all)

### 4.2 Numeric status-bar icon

Prototype a single monochrome icon containing the current number. Do not rely on arbitrary status-bar text, color, or multiple adjacent icons. Test three-digit values, mmol/L decimals, font scaling, display scaling, and OEM icon treatment. If it cannot be rendered unambiguously, use a standard app icon and retain the full value in the notification.

When a reading becomes delayed or stale, replace the plain numeric icon with a clear status symbol; the notification contains the last known value. This prevents an old number from looking current when viewed without the card.

### 4.3 Optional Live Update

Treat Android's promoted Live Update as progressive enhancement. Its rules cover user-initiated, ongoing, time-sensitive activity; custom notification layouts are not eligible, and promotion remains subject to OS/user/OEM controls. The platform supplies promotion capability checks and settings. [Live Update documentation](https://developer.android.com/develop/ui/views/notifications/live-update)

An explicit monitoring session with Start and Stop is the proposed fit, not a claim of universal eligibility. The platform owner must check the shipping SDK's exact API levels and actual device behavior. If supported, use the value as short critical text where readable and a supported notification style. Do not invent a progress bar, impersonate navigation/calls, or make a new MetricStyle API a minimum dependency. During setup, show a preview of the enhanced presentation and explain that Android may display the normal notification instead. The user can turn the enhancement off without pausing glucose monitoring. Demotion, denial, or device ineligibility falls back to the ordinary notification. Respect an explicit dismissal; do not repost the promoted version in a loop. No extra polling loop is created for this presentation.

## 5. Data flow and architecture

```text
Patrick's existing Libre setup
          ↓ uploads as it already does
LibreLinkUp cloud
          ↓ authenticated HTTPS from Khaleel's phone
LibreLinkUp adapter → validation/normalization → local repository
                                               ↓
                              shared display state + freshness clock
                                    ↙                     ↘
                              main screen             notification
```

Pine builds the APK; it is not part of the runtime data path. Turning pine or Ponderosa off must not interrupt the installed app.

Use one application with clear packages before introducing many Gradle modules. Suggested layout:

```text
app/
  auth/          credential/session storage and account lifecycle
  librelinkup/   HTTP client, regional routing, DTOs, adapter
  data/          normalized models, repository, bounded persistence
  monitoring/    foreground service, polling, lifecycle/recovery
  notification/  formatting, rendering, actions, privacy
  ui/            onboarding, main screen, settings, diagnostics
  diagnostics/   redaction, local events, export
  testfixtures/  synthetic fixtures, excluded from production runtime
docs/
  protocol.md
  background-strategy.md
  device-validation.md
  release.md
```

Use Kotlin coroutines and a shared observable state source. Suggested libraries are OkHttp plus a typed JSON serializer, Room for bounded reading history, and DataStore for ordinary preferences. Pin actual compatible versions in the Gradle version catalog. Introduce dependency injection only to the extent needed to replace clock, HTTP transport, credentials, and notification output in tests.

Core boundaries:

```kotlin
interface GlucoseSource {
    suspend fun signIn(credentials: Credentials): AuthResult
    suspend fun connections(session: Session): ConnectionsResult
    suspend fun latest(session: Session, connectionId: String): ReadingResult
}

interface CredentialStore { /* save, load, delete; encrypted at rest */ }
interface MonitoringController { /* start, pause, refresh, observe state */ }
interface NotificationRenderer { /* render DisplayState; hide */ }
interface Clock { /* wall time and monotonic elapsed time */ }
```

These are interface sketches, not a prescribed ABI. Define typed outcomes instead of relying on thrown strings or treating all failures as “offline.” Freeze shared data models before splitting implementation work.

## 6. LibreLinkUp protocol work

The integration is a maintained adapter around an unofficial interface. Existing GlucoDataHandler code and its setup documentation are useful primary implementation references: it supports LibreLinkUp follower accounts and is currently MIT-licensed. Inspect and pin the exact upstream commit and license before reusing code; retain applicable notices. [Project](https://github.com/pachi81/GlucoDataHandler), [follower setup](https://github.com/pachi81/GlucoDataHandler/blob/master/SOURCES.md)

### 6.1 Required discovery record

Before implementing a live client, write `docs/protocol.md` with:

- Upstream commit inspected, source files, license, and inspection date.
- Current regional base-host rules, login path/body, required headers, and client-version handling.
- Login result, token lifetime/refresh behavior, account identifier handling, and region redirect format.
- Connections list schema and stable identifier used to select a person.
- Latest reading endpoint and its timestamp, unit, numeric value, special-value, and trend fields.
- Differences between current reading and historical graph data.
- Mappings for authentication failure, terms acceptance, rate limiting, missing connections, and upstream failures.
- Synthetic fixtures covering each result; unknown fields tolerated and mandatory fields validated.

Do not hard-code an API version, region, endpoint, or trend mapping based solely on memory or an old tutorial. Endpoint names seen in other projects are discovery leads until confirmed against maintained source and, later, a live account test.

### 6.2 Authentication and routing

- User supplies their follower account on-device. Do not require Patrick's sensor-account password.
- Reuse valid sessions; do not log in on every poll.
- Serialize token replacement so concurrent requests cannot start a login storm.
- Permit one reauthentication sequence after an expired-session response; repeated rejection enters **Sign-in required**.
- Distinguish invalid credentials from network failure, unsupported protocol, and terms acceptance.
- If new terms must be accepted, direct the user to the official LibreLinkUp app, then provide **Try again**. Do not automatically accept contractual terms.
- Implement bounded region discovery using a maintained allowlist of legitimate provider hosts. Reject arbitrary server-supplied URLs, non-HTTPS redirects, and redirects carrying credentials to unrelated hosts.
- Never disable TLS validation. Avoid certificate pinning that cannot be maintained through provider certificate changes.
- Persist the selected stable connection ID. A removed connection becomes an explicit unavailable state; never substitute another person.

### 6.3 Polling and retry policy

Default to one poll every 60 seconds while monitoring is enabled, with small jitter and no overlapping fetches. This is a proposed product cadence, not an assertion about an Abbott-approved request limit. The protocol spike must verify feasibility; if it cannot, document a slower supported cadence and its user-visible tradeoff.

| Condition | Policy |
|---|---|
| Successful, new reading | Store atomically and update display immediately. |
| Successful, duplicate reading | Update last successful contact only; preserve measurement age. |
| No network | Suspend network attempts; react to connectivity restoration, subject to existing backoff. |
| Timeout / transient server failure | Back off approximately 1, 2, 4, 8, then 15 minutes, with jitter. |
| HTTP 429 | Honor Retry-After; if absent use a conservative 15-minute cooldown. Persist next allowed attempt. |
| Expired token | One serialized reauthentication, then retry the original operation once. |
| Invalid credentials / terms | Stop automatic auth attempts; require user action. |
| Missing connection | Stop reading requests for it; retain explicit unavailable state. |
| Unknown schema / malformed response | Reject it; retain last valid reading and a protocol error. |

Manual Refresh coalesces with any active request and cannot override provider cooldowns. App restarts must not reset a 429 cooldown. Normal Refresh has a short local debounce, e.g. 15 seconds. Keep connect/read/overall timeouts bounded; proposed overall request budget is 30 seconds.

Account for other apps polling the same account during testing. Do not silently modify Gluroo or the official LibreLinkUp app.

## 7. Reading model and state rules

Store normalized readings with source identity and measurement time, not just a number:

```text
accountScopeId, connectionId
sourceRecordId? or deterministic deduplication key
sourceTimestampUtc, receivedAtUtc
valueMgDl? (canonical numeric value), sourceUnit, sourceReportedValue
specialValue? (LOW / HIGH / UNAVAILABLE as explicitly provided)
trend (validated enum plus UNKNOWN)
sourceQuality/status fields needed to interpret the record
```

Store canonical units once and round only for display. Use a documented conversion and test rounding boundaries. Prefer provider-supplied display values when their unit and meaning are established; do not create discrepancies by repeatedly converting back and forth. Never turn a missing number into zero, or a source “LOW/HIGH” sentinel into an invented precise value.

Persist ordinary settings separately from encrypted auth. Use a bounded, atomic reading store; expire data older than 24 hours. Do not persist complete raw provider payloads in production.

Represent the display state with independent dimensions:

1. **User intent:** monitoring enabled or paused.
2. **Runtime:** starting, running, stopped, or unable to start.
3. **Connection:** ready, fetching, offline, backing off, sign-in required, terms required, connection removed, or protocol error.
4. **Reading:** absent, fresh, delayed, stale, or invalid timestamp.

This avoids treating “HTTP succeeded” as “reading fresh.” A connection error can coexist with a still-fresh reading. User pause takes precedence over automatic restarts.

### Freshness defaults

| Reading age | Presentation |
|---|---|
| No validated reading | “Waiting for first reading”; no numeric value. |
| Under 5 minutes | Value and source arrow prominent; measurement time visible. |
| 5 to under 15 minutes | “Reading delayed”; last value and age visible; status icon replaces bare number. |
| 15 minutes or more | “No recent reading”; last value labeled as historical; omit a prominent current trend. |
| Timestamp invalid or over 2 minutes in the future | “Reading time unavailable”; do not mark as fresh. |

These thresholds are display defaults, not medical alarm thresholds. A five-minute polling setting will naturally spend some time in the delayed state; explain this tradeoff rather than resetting age on fetch.

Use source UTC/offset fields where available. Never interpret an ambiguous source-local timestamp using the viewer's timezone without verifying its schema. Exercise timezone and daylight-saving fixtures. Use monotonic elapsed time for in-process intervals, reconcile wall-clock changes, and recompute after process recreation/reboot.

Reject out-of-order readings from replacing newer current data. If a same-timestamp record is corrected by the source, define and test a deterministic replacement policy. Do not compute a replacement trend when the provider's arrow is unknown. Any optional delta must show its actual elapsed interval and disappear when comparison data is insufficient.

Reevaluate freshness without needing a new network response whenever execution is available. Always retain an absolute measurement time in the notification, since an OS-suspended or stopped process cannot guarantee a timely stale-label transition.

## 8. Background operation: first feasibility gate

Build a small notification/polling prototype before substantial UI work. It must establish what actually works on the target Android version with screen off. A coroutine delay in a foreground service alone is not evidence of reliable waking from suspend.

### Platform constraints

Android requires appropriate foreground-service declarations on newer releases. `specialUse` is available for legitimate cases not covered by other types, with a declared explanation. Proposed choice for this personal app: a user-started continuous remote glucose display using `specialUse`, subject to implementation review and device validation. Do not request irrelevant sensor/Bluetooth permissions simply to satisfy another service category. [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)

Do not build all-day monitoring on a continuous `dataSync` service: apps targeting Android 15+ face a six-hour background budget in a 24-hour period for that service type. [Timeout documentation](https://developer.android.com/develop/background-work/services/fgs/timeout)

Periodic WorkManager jobs have a minimum 15-minute interval and can be delayed. They are suitable only for housekeeping or a best-effort recovery check, not the one-minute glucose loop. [Periodic work](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)

Doze can suspend network access and defer work. A user-granted battery-optimization exemption permits some additional execution but does not remove all restrictions. Test both default and unrestricted settings, document what is required, and measure any wake-lock strategy. [Doze documentation](https://developer.android.com/training/monitoring-device-state/doze-standby)

### Required implementation behavior

- Start monitoring from a visible, explicit user action; enter foreground promptly and post the notification before network work.
- Persist monitoring intent separately from transient process state.
- Keep one poller per account/selected person; one mutex or equivalent enforces single-flight work.
- On system process recreation, reload the last reading, mark its actual age, and resume only if persisted intent is enabled and startup is permitted.
- On boot, wait for credential storage to be accessible after first unlock. Attempt restart only through a supported path; if blocked, show a tap-to-resume notification when permitted.
- Do not store passwords in device-protected storage just to fetch before the first unlock.
- Treat app swipe-away, notification dismissal, Android's active-app Stop action, and force-stop as different lifecycle cases.
- Respect explicit system/user stops. A force-stopped app cannot be promised autonomous restart; the next app launch explains and offers Resume.
- Pause/sign-out cancel polling, queued retries, recovery work, and stale callbacks. Late responses are discarded by generation ID.
- Catch service-start restrictions; leave truthful stopped state instead of claiming monitoring is running.
- Recreate notification channels idempotently; do not reset user choices by continually inventing new channel IDs.
- Battery setting links must be best effort with generic Android fallback. Do not request accessibility/device-admin access or keep the display on.

### Scheduling decision that the spike must resolve

Evaluate a foreground-service scheduler under unrestricted battery settings. Measure whether proposed wake scheduling produces the desired screen-off cadence. If wake locks are needed, document exactly when they are acquired/released, their maximum duration, measured energy cost, and behavior in Doze. Bounded locks around requests do not themselves wake a suspended CPU; account for that separately.

Do not silently add continuous wake locks, exact-alarm privileges, aggressive alarm loops, or a server relay to paper over failures. A cadence/power strategy change must be recorded as an engineering decision with evidence and an explicit product tradeoff. If a direct phone-only solution cannot meet the target phone's requirements, report the measured limitation and proposed alternatives before expanding architecture.

The gate passes only when the team has an observed background strategy, or a clearly labeled prototype awaiting access to the real phone. Do not label unrun physical-device checks as passed.

## 9. Privacy, local storage, and diagnostics

Use the Android Keystore to protect an encryption key and encrypt persisted credentials/tokens with authenticated encryption. Use established platform/library primitives with correct nonce handling; do not design a cryptographic scheme. Keep secrets out of ordinary preferences, Room rows, build scripts, source control, crash output, and notification extras.

The chosen key policy must allow unattended monitoring after the user has unlocked the device following boot. Requiring biometric authentication for every token read would defeat background use. Handle invalidated keys or restored ciphertext by asking the user to sign in again.

Exclude credentials, tokens, reading history, and diagnostics from cloud/device-transfer backups unless an explicit secure migration is designed later. Sign-out deletes auth and patient caches, stops all monitoring, and removes notifications. Protect screen transitions so data from a previous account cannot flash on reconnect.

Use only permissions required for implemented features: internet/network state, notifications, relevant foreground-service permissions, boot receipt, and any measured wake/power capability. No contacts, location, storage-wide access, notification listener, Bluetooth, or Health Connect permission is needed for this cloud-only version.

Provide local diagnostics containing app/build version, Android version, service state, permission state, last attempted fetch, last successful fetch, last measurement age, redacted error category, next allowed retry, and observed scheduling gaps. Cap storage, e.g. a 48-hour rolling event buffer.

Exclude names, email, connection IDs, glucose values, tokens, passwords, and HTTP bodies from default exports. Offer an explicit user-triggered text export after preview. Logs must not be automatically sent anywhere. Use synthetic identifiers and values in fixtures and screenshots shared for review.

## 10. Verification and acceptance

### 10.1 Automated tests worth writing

- Login, valid session, token expiry, regional routing, terms-required response, removed connection, 429 and retry persistence.
- Fresh/duplicate/out-of-order/corrected readings, missing values, source HIGH/LOW, unknown arrow, malformed JSON, extra fields.
- Unit formatting, rounding boundaries, UTC and timezone handling, future timestamps, daylight-saving changes.
- Freshness transitions driven by a fake clock without network updates.
- Single-flight polling, cancellation, person/account switches, stale in-flight responses, pause persistence.
- Redaction and sign-out cleanup; no secrets in exported diagnostics.
- Notification content for all states, privacy variants, and permission denial.

Use mocked HTTP responses for frequent tests. Never run CI against a personal live account. Debug/demo sources must not be selectable accidentally in a release monitoring session; if a demo exists, show an unmistakable DEMO label everywhere.

### 10.2 Device matrix

Run emulator checks on API 30, 33, 34, 35, and the closest available image to the S25 Ultra's actual Android version; include newer stable API behavior when the selected toolchain supports it. The emulator can verify sign-in, person selection, reading formatting, notifications, optional Live Update behavior on a supported image, permission denial, network failure, restart, and forced Doze/recovery scenarios. Test the Galaxy S25 Ultra as the primary acceptance device after it arrives. An ordinary Android Emulator image does not reproduce Samsung One UI's exact lock-screen presentation, battery management, or real power draw. Record untested platforms rather than claiming broad compatibility from one emulator.

### 10.3 Scenario acceptance table

| Test | Pass condition |
|---|---|
| First sign-in | Correct connection selected; first available valid reading displayed within 30 seconds of a successful fetch initiation under normal connectivity. |
| Source fidelity | Every compared same-timestamp numeric value matches the source after documented unit formatting; arrow matches validated mapping. |
| Core notification | One silent card, no duplicates; value, unit, and measurement time legible in shade and configured lock-screen view. |
| Optional Live Update | On an eligible device, test promotion, status-chip readability, user demotion/dismissal, and the setting to disable it. On an ineligible device, the standard notification still works and setup explains the limitation. |
| Permission denied | UI clearly states notification is unavailable; never reports full setup success. |
| One-minute mode | During an eligible, unrestricted, connected 24-hour run, target at least 95% of poll-start gaps ≤90 seconds, excluding recorded provider cooldowns/outages. Report all excluded intervals and total coverage. |
| Screen-off overnight | At least eight hours unplugged and screen off, with logged gaps and source ages; no unexplained gap over five minutes under the verified configuration. |
| Display latency | Target under two seconds from validated response receipt to notification update. This excludes sensor/cloud delay. |
| Connectivity loss | Last value remains explicitly timestamped; freshness ages correctly; reconnect without duplicate pollers. |
| Wi-Fi/cellular switch | No permanent stuck state; next eligible fetch resumes. |
| Repeated stale response | Successful HTTP responses do not refresh measurement age. |
| Expired session | One serialized recovery sequence, or a clear sign-in-required state. |
| Rate limit | No request before persisted cooldown expires, including after app restart or Refresh taps. |
| Process recreation | Restores persisted user intent and truthful last reading; resumes where OS permits. |
| Reboot | After first unlock, resumes where supported or presents an actionable resume path; records actual behavior. |
| User Pause / system Stop | Polling stops and stays stopped as required; no silent restart loop. |
| Notification dismissed | Dismissal is respected; app offers an explicit restore action. |
| Change person/account | No cross-person display or credential reuse. |
| In-place APK upgrade | Same signer/package updates successfully; settings preserved; monitoring recovery tested. |
| Privacy | Hidden lock-screen mode redacts readings; default logs/exports contain no secrets or patient data. |

Targets are release criteria for the verified configuration, not universal timing guarantees. If the phone needs unrestricted battery settings to pass, state that directly in setup and the release notes. A failure calls for investigation or a documented tradeoff, not changing measurements to appear successful.

For source comparison, compare measurement timestamps rather than screenshots taken at different moments. Gluroo may be a useful convenience benchmark, but its displayed value at a different timestamp is not a ground-truth discrepancy.

### 10.4 Battery measurement

Measure a matched idle baseline and an equivalent monitoring run, documenting screen use, signal quality, OS settings, battery health, duration, network calls, and wake-lock duration. Initial target: no more than five additional battery percentage points per 24 hours on the target phone under comparable conditions. This is a provisional engineering budget; investigate and report measurements if it is exceeded. Never claim “low battery use” based only on emulator results.

## 11. Build and release on pine

Use **`/home/phoenix/khaleel/glance`** as the Glance working directory for the Ponderosa chat. Keep `GLANCE-ANDROID-SPEC.md` and `PONDEROSA-START-HERE.md` at its root; put the Android source, build files, and `docs/` beneath it. Do not create an additional nested `glance/` directory. Ponderosa's TypeScript codebase is the workbench, not the app implementation. Do not modify Ponderosa or its services to build this app.

The lead inventories JDK, Android command-line tools, SDK platforms, build tools, Gradle compatibility, emulator availability, and disk capacity. Record exact versions and use the Gradle wrapper with checksum verification. If emulator acceleration is absent on pine, build and unit-test there and arrange physical-device/emulator execution elsewhere; label the gap.

Release requirements:

1. Stable application ID chosen once and recorded before the first install.
2. Separate debug and release handling, with debug-only facilities absent or disabled in release.
3. Durable private release signing key outside the repository and published artifacts; owner has a recoverable backup. Never use a disposable CI/debug key for the personal production install.
4. Incrementing versionCode and understandable versionName.
5. Clean-checkout build instructions and recorded output from tests, lint, assembly, and signature verification.
6. APK filename, SHA-256 checksum, certificate fingerprint, source commit, and tested Android/device versions in the release manifest.
7. Simple sideload instructions: transfer APK, grant that installer permission if requested, install, sign in on-device, enable notifications, complete battery setup, and verify a reading.
8. Upgrade instructions using the same signer/package; explain that changing signing keys prevents normal in-place updates.
9. Recovery instructions: Pause/Resume, inspect status, reopen after force-stop, accept terms in LibreLinkUp, reconnect, and reinstall only when needed. Do not make uninstalling the first troubleshooting step.

Save release artifacts and their manifest under `/home/phoenix/khaleel/glance`, and link or identify their exact paths in the Ponderosa chat. Public hosting, a public repository, or Play publication is not required. Do not put real login credentials in build-time configuration.

## 12. Ponderosa execution plan

This is a proposed future team structure. No agents have been launched by writing this spec.

### 12.1 Team and ownership

| Role | Responsibilities | Deliverables |
|---|---|---|
| Lead / integrator | Own scope, architecture, shared models, sequencing, integration, and final release. | Decision records, integrated source, release manifest, honest completion report. |
| Android runtime specialist | Prove background scheduling, lifecycle recovery, notification platform behavior, and power profile. | Prototype, `background-strategy.md`, service/poller implementation and tests. |
| LibreLinkUp specialist | Inspect current protocol, implement adapter/session handling, normalize data. | `protocol.md`, synthetic fixtures, client and focused tests. |
| UI / notification specialist | Implement onboarding, main/settings screens, display formatting, privacy, accessibility. | Compose UI, notification renderer, visual evidence. |
| Independent reviewer / QA | Read-only review, failure-path verification, device matrix and release audit. | Findings by severity and exact reviewed commit; verified/unverified checklist. |

Use only as many simultaneous workers as the Ponderosa installation supports. A practical arrangement is lead plus two specialists first, then UI and independent review. Keep interfaces stable so the protocol and runtime spikes can proceed independently. The lead can own UI if a smaller team is more efficient.

### 12.2 Ponderosa chat coordination

Work from a Ponderosa chat with `/home/phoenix/khaleel/glance` as its working directory. If the current installation supports visible agent creation and delegation, use those capabilities for specialist work. Do not substitute invisible provider-native helpers and then claim a Ponderosa team was created. Resolve capabilities from the running installation; do not invent tool names or hand-write scheduler/database state.

Give each specialist a precise deliverable and acceptance condition. Use the current chat's supported routing and coordination features to gather results, with exact artifact paths. Share durable decisions explicitly with the agents who need them; do not assume private messages or work logs are automatically shared. If the current Ponderosa version lacks a particular delegation feature, keep the lead responsible for the work rather than relying on an obsolete workflow.

Each writer gets an independent branch/worktree. Keep one writer per worktree; reviewers are read-only. Shared models and root build files have an explicit owner. Handoffs include branch, base/head commit, worktree, dirty state, artifacts, tests run, unresolved issues, and next action. Verify actual state before integration; do not overwrite another writer's changes.

Older local Ponderosa documentation was consulted when this plan was drafted, but its Project-chat terminology is obsolete for the current chat-only interface. The lead must use the capabilities actually present in the pine installation. The plan does not require any particular provider/model override.

### 12.3 Phases and gates

**Phase A — bootstrap and feasibility.** Lead initializes `/home/phoenix/khaleel/glance` as the separate Android project and establishes shared interfaces. Runtime specialist proves a real persistent notification and screen-off scheduling in an emulator, including forced Doze and recovery checks. Protocol specialist records the current contract and prepares fixtures. Outcome: installable prototype plus an explicit list of what the Galaxy S25 Ultra must verify after delivery.

**Gate A:** Do not spend most of the project on UI before the polling/notification mechanism works. A build that merely displays a fake reading is a prototype, not a completed feasibility proof.

**Phase B — vertical slice.** Connect on-device login → person selection → real fetch → normalized reading → one notification. Implement encrypted auth, silent updates, timestamp display, and Pause. Owner enters live credentials in the app.

**Gate B:** Same-timestamp source/display parity, correct selected person, and single poller established. A lack of live-account access is recorded as unverified, not bypassed with demo data.

**Phase C — resilience and product finish.** Complete stale/offline/backoff states, reboot/process recovery, privacy settings, diagnostics, accessibility, and update behavior. Prototype numeric icon and optional Live Update only after baseline stability.

**Phase D — review and device qualification.** Reviewer examines source and evidence. Run automated and emulator checks first. Once the Galaxy S25 Ultra is available, verify One UI notification/lock-screen behavior, background scheduling, overnight run, 24-hour soak, and battery comparison. Fix blocking findings and recheck the changed behavior.

**Phase E — personal release.** Produce signed APK, checksum, source revision, install guide, signing/upgrade notes, and test report. Distinguish “APK built,” “live integration verified,” and “target phone qualified.” If only the first is possible, state that precisely and provide the smallest remaining owner test.

## 13. Definition of done

The project is complete when the signed APK installs on Khaleel's phone; the follower account can select Patrick; readings match the source for the same timestamps; the ongoing notification works during verified background/lock-screen use; missing data is never presented as current; Pause and sign-out work; the release can be updated using the same signer; and test/review evidence covers the required scenarios.

Deliver:

- Source repository at an identified commit.
- Signed APK and release manifest with checksum/certificate fingerprint.
- `README.md` with build and installation instructions.
- `docs/protocol.md` and upstream/license attribution.
- `docs/background-strategy.md` with measured platform choices.
- `docs/device-validation.md` with pass/fail/not-run results, device/OS, overnight gaps, and battery measurements.
- `docs/release.md` with signing, upgrade, recovery, and known limitations.
- Independent review tied to the delivered source commit.

Completion must describe the observable product, not just how many agents finished or whether Gradle succeeded.

## 14. Implementation reference index

These references were checked while preparing the specification. Recheck evolving APIs when implementation begins.

- [Android foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Foreground-service time limits](https://developer.android.com/develop/background-work/services/fgs/timeout)
- [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [PeriodicWorkRequest](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)
- [Android 14 ongoing-notification dismissal behavior](https://developer.android.com/about/versions/14/behavior-changes-all)
- [Live Update notifications](https://developer.android.com/develop/ui/views/notifications/live-update)
- [GlucoDataHandler source and license](https://github.com/pachi81/GlucoDataHandler)
- [GlucoDataHandler LibreLinkUp setup](https://github.com/pachi81/GlucoDataHandler/blob/master/SOURCES.md)
- Historical Ponderosa reference: `docs/wiki/agents-and-organization.md`, `docs/wiki/communication-scopes.md`, and `docs/wiki/runs-and-results.md`, inspected in `/Users/khaleel/Projects/Ponderosa`. Their Project-chat workflow is obsolete; use the active pine chat interface as the authority. These are reference paths, not a request to edit Ponderosa.
