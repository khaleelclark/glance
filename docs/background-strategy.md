# Background monitoring strategy

## Current source — checked 22 October 2026

Includes unreleased [audit fixes](ponytail-audit-20261022.md); the previously
delivered APKs retain their own source/evidence boundaries.

The service is integrated through `GlanceApplication`'s
`MonitoringDependencyProvider`, controller and notification adapters. The
user-started foreground session uses `specialUse` on API34+, a monotonic
`PollCoordinator`, 30-second poll timeout, ±3-second cadence jitter and
30-second freshness ticks while execution is available. Single-flight work,
skipped missed slots and persisted cooldowns prevent catch-up bursts.
Manual Refresh on Now can bypass transient delay with a three-second debounce;
provider cooldowns remain enforced. Running-session Refresh goes through the
service coordinator; after a completed manual attempt the next nominal automatic
slot is one full interval later. Old notification actions are legacy-only.

There is no application wake-lock acquisition or alarm/push/WorkManager loop
in the shipped scheduler. A coroutine cadence is not a guarantee of waking a
suspended CPU. The short API36 synthetic forced-idle smoke recorded three gaps
of 61.398, 57.908 and 62.854 seconds; synthetic reboot recovery was also observed.
These are historical smoke checks, not an eight-hour/24-hour natural screen-off
qualification or battery measurement. See [device validation](device-validation.md).

Stop/sign-out invalidate work and remove the card. `honorExplicitSystemStop`
uses the latest `ApplicationExitInfo.REASON_USER_REQUESTED` record to clear
monitoring intent; a stop is marked handled only after the disabled intent saves
successfully, or a later explicit Start succeeds. Automatic recovery does not
acknowledge a pending system Stop. A failed save still stops current-process work and hides output,
while reporting that restart persistence is uncertain. Start/retry storage errors
do not escape the control/scheduler handlers. Force-stop still requires an
explicit app launch. Boot recovery
waits for `BOOT_COMPLETED` after unlock, stored intent, resume preference,
notification permission and a selected authenticated connection. A previously
suppressed card with retention off also prevents automatic boot recovery. Glance
itself gates both visible Start and boot recovery on notification permission. Start rejection
leaves an unable-to-start state; it does not post an automatic tap-to-resume
fallback notification. Reopen and explicitly start from the app.

The standard card uses custom compact/expanded views; enhanced mode uses a
standard Android template. The owner-requested default-on retention choice
supersedes the original manual-only design below: delivered running-session
dismissal restores a regular card without requesting promotion again. With
retention off, suppression persists while retention remains off until explicit
restore/start; enabling retention republishes the regular card. No OS/OEM
undismissability or Now bar admission is guaranteed.

Diagnostics stores bounded source-free wall-time poll events and a largest-gap
summary, not the proposed full monotonic lateness/percentile/wake-duration report.
Collect separate qualification evidence to assess the baseline's 95% target.
Physical Samsung background, natural overnight/24-hour cadence and matched
battery results remain outstanding. Current setup is in [setup](setup.md).

## Original feasibility decision and test plan — 27 September 2026

The rest of this record preserves the original research, isolated-worktree status
and proposed measurement plan. Its not-integrated statements, notification
controls and host observations describe that checkpoint; they do not describe
current wiring or current host state. Platform references were inspected on that
date. Later evidence retains its own exact source/APK boundary.

## Decision

Use one user-started foreground service (FGS) for an active monitoring session.
On Android 14 / API 34 and later, declare it as `specialUse`; on earlier
supported Android releases it runs as an ordinary foreground service without
passing the API-34 service-type constant.

The manifest declaration for API 34+ must include:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<service
    android:name=".monitoring.MonitoringService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="User-started continuous remote glucose monitoring that updates an ongoing notification; no other foreground-service type covers this cloud-only display." />
</service>
```

Normal `startForegroundService()` entry is an explicit Start/Resume action while
the app is visible. Enabled boot recovery is the separate `BOOT_COMPLETED`
exemption, conditional on the checks in the current summary above. Promote
immediately with the stable glucose
notification and `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 34+. Catch and
persist a truthful unable-to-start state if Android rejects the start. Android
12+ generally rejects background FGS starts, although documented exemptions
exist; a visible user action is the reliable normal entry point. See the
[FGS launch guide](https://developer.android.com/develop/background-work/services/fgs/launch)
and [background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

`specialUse` is the closest honest type because Android defines it for valid
FGS cases not covered by another type, requires
`FOREGROUND_SERVICE_SPECIAL_USE`, and requires a free-form subtype explanation.
It has no type-specific runtime prerequisite. A cloud follower/display does not
use an Android body sensor or Health Connect, so it should not request unrelated
permissions merely to qualify for `health`. `remoteMessaging` is for moving text
messages between devices, not reading glucose data. A continuous `dataSync` FGS
is rejected: for apps targeting Android 15+, Android gives each of the
`dataSync` and `mediaProcessing` types an aggregate six-hour budget across the
app's services in a 24-hour period while the app is in the background. The two
type budgets are tracked separately. Sources:
[FGS types and `specialUse`](https://developer.android.com/develop/background-work/services/fgs/service-types),
[FGS time limits](https://developer.android.com/develop/background-work/services/fgs/timeout).

This is a platform classification, not permission to defeat power management.
`specialUse` has no documented guarantee of one-minute CPU wakeups or network
access, and a future Play distribution would require declaration/review of the
free-form use case. The current product is personally sideloaded.

## What Android can and cannot guarantee

An FGS improves process priority and makes monitoring visible to the user. It
does not turn a coroutine timer into an alarm. During Doze Android suspends
network access, ignores wake locks, defers ordinary alarms, jobs, and
WorkManager, and opens increasingly infrequent maintenance windows. Even the
alarms allowed while idle cannot fire more than once per nine minutes per app.
A battery-optimization exemption is only partial: it allows network access and
partial wake locks during Doze, but regular alarms, jobs, and syncs remain
restricted. See [Optimize for Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby).

Consequences for Glance:

- A coroutine `delay(60.seconds)` inside the FGS is only an in-process cadence
  mechanism. It does not prove that the CPU wakes every minute.
- A short partial wake lock acquired when a poll callback starts can keep the
  CPU running through that request. It cannot cause a callback to run while the
  CPU is already suspended, and Doze ignores it unless the app is exempt.
- `setExactAndAllowWhileIdle()` is not a one-minute solution because of the
  documented nine-minute floor in idle. Glance will not request exact-alarm
  access or create an aggressive alarm loop.
- Periodic WorkManager has a 15-minute minimum and is explicitly inexact under
  battery optimization/Doze. It may be used later for coarse housekeeping or a
  recovery check, never as the one-minute poller. See
  [`PeriodicWorkRequest`](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest).
- No continuous wake lock is acceptable. No server relay or push channel is in
  the current direct-phone architecture.

Therefore the one-minute screen-off target is a measurement gate, not a claim.
There is no documented Android primitive in the approved design that guarantees
a one-minute wake cadence in deep idle. The implementation may meet the target
on a particular unrestricted device because the running FGS remains scheduled;
that must be demonstrated on that device. If it does not, the product choice is
to accept slower/gapped refresh, reconsider a push/server design, or explicitly
approve a materially different power strategy. It is not acceptable to hide the
gap by refreshing the reading timestamp.

## Original proposed poll lifecycle (historical)

Monotonic scheduling, single-flight work, missed-slot skipping, cooldowns, the
30-second timeout and freshness projection were integrated. The wake-lock A/B
path was not implemented. Notification Refresh actions are now legacy-only;
current manual Refresh is on Now.

### Poll lifecycle

1. The visible Start action persists monitoring intent and a new generation,
   starts the service, and posts the notification before any network request.
2. One service-owned scheduler uses monotonic elapsed time. One mutex enforces a
   single flight across timer, notification Refresh, and connectivity recovery.
3. Each nominal due time is derived from the prior nominal due time, with small
   jitter. After suspension, make at most one immediate eligible request and
   skip missed slots; never burst requests to catch up.
4. Provider cooldowns and exponential backoff override the nominal cadence.
   Manual Refresh coalesces and cannot bypass provider cooldowns. Following the
   October 1 DNS-recovery report, manual retries can bypass transient network or
   server backoff, with a three-second monotonic tap debounce.
5. A poll has a 30-second overall deadline. Initially run without a wake lock.
   If measurement shows requests beginning but being interrupted during screen
   off, A/B test a non-reference-counted partial wake lock acquired only after
   the poll begins, with a hard 35-second timeout and release in `finally`.
6. Stop, sign-out, person/account change, and system teardown invalidate the
   generation before cancelling jobs. Every response checks its captured
   generation before storing or rendering.
7. Notification freshness is computed from the source measurement timestamp,
   never last HTTP success. The absolute measurement time always remains
   visible because Android may suspend the process before a relative-age label
   can update.

Do not infer monitoring success from a persisted preference. The UI should
display monitoring as running only after foreground promotion succeeds and the
service publishes running state.

### Battery setting

Setup should show the current value of
`PowerManager.isIgnoringBatteryOptimizations(packageName)` and offer a best-
effort link to the system battery-optimization settings. Requesting a direct
exemption must remain a separately reviewed product decision; the Android docs
say it is acceptable only for narrow cases where Doze breaks the core function
and a push alternative is technically unavailable, and Play policy constrains
direct exemption requests.

Test both optimized and unrestricted modes. If only unrestricted passes, say so
plainly during setup and in release notes. Samsung may also expose an OEM
"Unrestricted"/sleeping-app control; its exact screens and effect must be
recorded on the actual phone rather than guessed.

### Lifecycle boundaries

- Android 13+ lets the user stop an FGS app through Task Manager. Android removes
  the entire app and FGS notification and sends no callback. On the next launch,
  inspect `ApplicationExitInfo.REASON_USER_REQUESTED`, present the stopped state,
  and require an explicit Resume rather than fighting the stop. See
  [Handle user-stopped foreground services](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping).
- A force-stop cannot be recovered autonomously; the user must launch the app.
- Treat recents swipe, notification dismissal, Task Manager Stop, force-stop,
  process death, and reboot as distinct tests.
- `BOOT_COMPLETED` is a documented background-start exemption, but newer Android
  releases restrict some service types and credential-protected storage is not
  available before first unlock. The prototype must test the selected target
  SDK. Safe fallback is an actionable tap-to-resume notification after unlock,
  not a false running state. Do not put credentials in device-protected storage.

## Notification and Live Update constraints

The current regular glucose card uses `DecoratedCustomViewStyle` with compact/
expanded `RemoteViews`; enhanced mode alone uses standard `BigTextStyle`. Both
use one stable ID, a silent `IMPORTANCE_LOW` channel, `setOngoing(true)`, and
`setOnlyAlertOnce(true)`. Routine updates remain silent.

On Android 13+, `POST_NOTIFICATIONS` is not required merely to launch an FGS,
and the FGS must still supply a notification. If permission is denied, however,
the FGS notice appears in Task Manager rather than the notification drawer.
This is a generic platform rule. Glance's visible Start and boot recovery are
both gated on permission, so the normal app path does not start monitoring
without it. See
[notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission).

On Android 14+, users can dismiss most ongoing FGS notifications while the
device is unlocked, despite `setOngoing(true)`; they remain non-dismissible on
the lock screen and via Clear all. Current Glance offers **Show notification** and
default-on **Keep notification visible**: a valid delivered dismissal restores
the regular card without requesting promotion again. OS/OEM delivery varies. See
[Android 14 notification behavior](https://developer.android.com/about/versions/14/behavior-changes-all#non-dismissable-notifications).

While retention remains off, Glance suppresses ordinary updates after a
recorded dismissal while the service remains alive. A sticky restart then stops
the persisted session before `startForeground`; explicit Start is required.
Turning retention back on republishes the regular card. Historical synthetic
swipe/retention checks are in [device validation](device-validation.md); physical
Samsung delivery and recreation remain unverified.

Live Update is progressive enhancement only. Android 16 / API 36 introduced
the promoted-ongoing capability, and OEMs may add eligibility criteria. A user
explicitly starting and stopping an ongoing glucose-monitoring session plausibly
matches the platform's ongoing, user-initiated, time-sensitive monitoring
criteria, but this is not a guarantee that Android or Samsung will promote it.
The implementation must:

- compile-gate and runtime-gate the feature (API 36+ for the platform promotion
  checks), request the non-runtime `POST_PROMOTED_NOTIFICATIONS` permission, and
  check `NotificationManager.canPostPromotedNotifications()`;
- request promotion, keep the notification ongoing, set a content title, and
  confirm `hasPromotableCharacteristics()` where available;
- use an eligible standard style such as `BigTextStyle`; do not use custom
  `RemoteViews`, a group summary, `IMPORTANCE_MIN`, or a colorized notification;
- fall back to the exact same ordinary notification ID when promotion is denied,
  demoted, dismissed, unsupported, or disabled in Glance settings;
- create no extra service, poller, request cadence, or notification channel for
  Live Update; and
- keep the short status-chip text honest. A fresh value may be shown only when
  privacy settings allow it; delayed/stale state must replace a bare value with
  a status indication.

The eligibility rules, checks, styles, and OEM caveat are in
[Create Live Update notifications](https://developer.android.com/develop/ui/views/notifications/live-update);
the API-36 platform check is documented by
[`canPostPromotedNotifications()`](https://developer.android.com/reference/android/app/NotificationManager#canPostPromotedNotifications()).

## Proposed qualification telemetry — not implemented

The collector/export below is a qualification target, not current app diagnostics.
`DiagnosticsStore` records bounded fixed-category wall-clock events, last values
and max wall-time attempt gap. The UI exports a summary and event count, not raw
events, due/start lateness, percentiles, wake duration, screen/connectivity state
or exclusion intervals. A separate qualification collector/worksheet is required.

The proposed collector should record bounded, non-PHI events using both clocks:

- service start attempt/result and reason, generation, and process instance;
- nominal poll due, actual poll start, completion, outcome category, and next
  eligible time;
- source-free scheduling gap (`actualStartElapsed - previousStartElapsed`) and
  start lateness (`actualStartElapsed - nominalDueElapsed`);
- connectivity state, screen-interactive state, and battery-optimization status;
- wake-lock acquisition/release and held milliseconds, if the bounded A/B path
  is enabled;
- notification post/update/dismiss-observed state and Live Update eligibility;
- lifecycle markers for Doze commands, connectivity changes, process restart,
  reboot, and user/system stop.

Do not log name, account/connection ID, glucose value, credentials, token, or
HTTP body. Export aggregate count, median, p95, maximum poll-start gap, count and
duration of gaps over 90 seconds and five minutes, attempted/completed requests,
request duration, and total wake-lock time. Preserve provider cooldown/outage
intervals as labeled exclusions rather than deleting them.

For the specified one-minute acceptance run, the target is at least 95% of
eligible poll-start gaps at or below 90 seconds, with no unexplained gap over
five minutes. This metric must be reported separately for screen-on, natural
screen-off, forced Doze, optimized, and unrestricted modes. Emulator results do
not count as battery evidence.

## Historical emulator readiness and original matrix (27 September 2026)

### Pine readiness (observed 2026-09-27)

| Check | Observed result |
|---|---|
| Host | x86-64, AMD Ryzen 5 5500, AMD-V advertised, 12 logical CPUs |
| JDK | OpenJDK 17.0.20.1 |
| Android SDK root | `/home/phoenix/Android/Sdk` exists |
| Command-line tools | `sdkmanager` 22.0 and `avdmanager` present |
| Required runtime packages | build-tools 36.0.0, emulator 37.1.11, platform-tools 37.0.1, platform 37.0, and API 36 Google APIs x86_64 system image revision 7 installed |
| AVDs | `Glance_API36`, Pixel 9 Pro XL profile, Android 16 / API 36 Google APIs x86_64 |
| Acceleration | `/dev/kvm` exists as `root:kvm` mode `0660`; `sg kvm -c 'emulator -accel-check'` reports KVM version 12 installed and usable |
| Emulator state at checkpoint | `Glance_API36` was booted with KVM and visible to ADB as `emulator-5554`; direct launcher invocation from that worktree shell failed because host `libpulse.so.0` was missing; restart reproducibility was pending |
| Disk | approximately 1.1 TiB available on the workspace filesystem |

At that checkpoint pine had the required API36 SDK image, ADB, AVD and working
KVM access through `sg kvm`. The lead booted the AVD and the isolated worktree
observed it through ADB. Direct launcher invocation failed on missing
`libpulse.so.0`; cold-start reproducibility was pending in that shell. The
worktree changed no SDK, AVD, KVM or host package configuration. These are dated
host observations; see [device validation](device-validation.md) for later checks.

### Initial API36 evidence at the 27 September checkpoint

On the booted `Glance_API36` AVD, ADB reported Android 16 / API 36 and the
`sdk_gphone64_x86_64` model. The debug APK installed successfully and
`MainActivity` completed a cold launch. The packaged manifest was inspected
with `aapt2`: it contains `MonitoringService`, `foregroundServiceType` value
`0x40000000` (`specialUse`), both foreground-service permissions, and the
declared special-use subtype. Android reported both foreground-service
permissions granted to the installed debug package.

At this isolated checkpoint foreground start, notification actions, screen-off
scheduling and forced-Doze recovery were **not run**. The application had not yet
installed `MonitoringDependencyProvider`/notification adapters. Later integration
and synthetic service/forced-idle/reboot evidence are recorded in
[device validation](device-validation.md); the current summary is above.

### Remaining matrix from that checkpoint

This was the original matrix, not a current claim that every item is pending.
Consult [device validation](device-validation.md) for completed/open boundaries.
Use clean AVD snapshots for API 30, 33, 34, 35, and 36 or newer stable images
supported by the frozen toolchain. API 36+ is required for Live Update checks.
Also use the system image closest to the Galaxy S25 Ultra's actual Android
version once the owner reports it.

For each relevant API, verify:

1. User Start posts exactly one notification before networking; denied start is
   truthful. API 33+ notification allow/deny paths behave as described.
2. One- and five-minute synthetic-data cadence with screen on, screen off, and
   network loss/recovery; no overlap or catch-up burst.
3. Force Doze using the Android-documented sequence:

   ```sh
   adb shell dumpsys deviceidle force-idle
   # Observe scheduling, notification, and recovery logs.
   adb shell dumpsys deviceidle unforce
   adb shell dumpsys battery reset
   ```

4. Run forced Doze once optimized and once with the app placed on the emulator's
   idle allowlist to model the platform exemption. Clearly label this as an
   emulator configuration, not proof of Samsung's battery setting.
5. Force App Standby, process kill/recreation, Task Manager Stop
   (`adb shell cmd activity stop-app PACKAGE`), recents swipe, notification
   dismissal, force-stop/relaunch, reboot, and in-place upgrade. Verify persisted
   intent versus truthful runtime state and absence of duplicate pollers.
6. On API 34+, confirm manifest/type promotion succeeds and captures type/start
   failures. On API 35+, confirm no `dataSync` timeout is involved because that
   type is not declared.
7. On API 36+, test Live Update eligible/ineligible settings, promotion,
   demotion, dismissal, ordinary fallback, and the app switch. Capture shade,
   lock-screen, and status-chip screenshots with synthetic values only.
8. Run at least a two-hour natural screen-off sample for quick iteration and an
   eight-hour emulator soak before phone testing. Export raw redacted event data
   and aggregates; do not call emulator battery percentages a power result.

Android's official forced-idle and App Standby commands are documented in the
[Doze testing section](https://developer.android.com/training/monitoring-device-state/doze-standby#testing_doze_and_app_standby).

## Required physical-phone verification

These controlled physical-phone scenarios remain **unverified**. The owner has
since reported phone use; those reports do not qualify these scenarios. An AOSP
emulator cannot qualify Samsung One UI behavior.

- actual phone model, Android version, One UI version, security patch, and
  Glance target SDK;
- ordinary notification appearance and actions in shade and configured lock
  screen, notification denial, Android 14+ dismissal, and restore flow;
- whether API/setting/OEM criteria permit Live Update, plus chip readability,
  privacy, demotion, and fallback;
- background-start, first-unlock/reboot, process recreation, Task Manager Stop,
  force-stop, recents swipe, and upgrade behavior;
- natural screen-off cadence for at least eight hours and the specified 24-hour
  one-minute run, first with default optimization and then unrestricted if
  needed;
- Wi-Fi/cellular transition, offline recovery, and actual provider cooldowns;
- matched 24-hour idle baseline versus monitoring run, including signal, screen
  time, calls, poll counts, gaps, and total wake-lock duration, against the
  provisional five-percentage-point additional battery budget; and
- any Samsung sleeping-app/background-usage setting required for the cadence.

Until those results exist, the honest release status is: **platform design
selected; screen-off cadence and battery impact unverified on the target phone**.
