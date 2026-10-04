# Glance 0.23.0: focused redesign and alarm log

## Accepted scope

The owner approved ocean blue, charcoal dark surfaces and soft-white light
surfaces, a new native vector G logo, a smaller reading summary, a more prominent
graph and bottom navigation. The navigation destinations are Now, Logbook and
Settings. Existing source reading, chart interaction, notification, widget,
watch, privacy, monitoring and alert controls must remain available.

Multiple-person monitoring, its dashboard, per-person thresholds and multiple
notification summaries are explicitly deferred by the owner. No extra patient
poller, roster format or notification routing belongs in this release.

The Logbook contains locally posted Glance threshold alerts, distinguishing Low,
Urgent Low, High and Urgent High. Entries include the person, source reading and
measurement time, and the time Glance posted the alert. They do not represent
alarms generated independently by the official LibreLinkUp application, and
posting a notification does not prove that Android sounded it.

The log is encrypted in no-backup storage and bounded to 1,000 records and 30
days. Clearing the log must preserve the existing alert fingerprint/cooldown
state. Explicit new sign-in requires successful log cleanup. Sign-out and local
health-data deletion attempt cleanup and report partial failures. After sign-out,
an unerased ledger remains encrypted and hidden. Failed local-data deletion can
leave the log visible in the signed-in Logbook. Another explicit sign-in requires
successful cleanup. Default diagnostic exports continue to exclude patient names, values
and alarm records. Custom accent choices are preserved; the default accent is
now ocean blue.

## Automated build, 2026-10-04

The integrated source is based on `ce30b736362d0310e908eb3d1cdd9408f89f237c`.
The subsequent watch G glyph and ocean-blue notification trace were included in:

```sh
ANDROID_HOME=/home/phoenix/Android/Sdk ./gradlew \
  :app:testDebugUnitTest :app:assembleDebug :app:lintDebug \
  :wear:testDebugUnitTest :wear:assembleDebug :wear:lintDebug --no-daemon
```

Initial result: **BUILD SUCCESSFUL**. Phone: 201 JVM tests, zero failures/errors/skips;
debug lint zero errors and 74 warnings. Watch: 15 JVM tests, zero
failures/errors/skips; debug lint zero errors/warnings. No watch runtime change
was made. The review corrections below subsequently passed 205 phone JVM tests,
debug assembly and debug lint without errors. Release assembly/lint/signature
and final source identity are verified separately in the release manifests.

Alert tests cover exact four-category logging with urgent priority, actual
post-return time, blocked channels, duplicate samples, cooldowns, failed history
writes, serialized clear versus an in-flight append, and preservation of the
sample fingerprint after log-only clear. Retention tests cover newest-first
ordering, duplicate IDs, age/count bounds and invalid identities/readings.

## API 36 synthetic emulator

Device: Google APIs x86_64, `Glance_API36`, 1344×2992 at 480 dpi, KVM through
`sg kvm`. Fixtures are debuggable, labeled DEMO and excluded from release. The
local fixture APK was signed with the personal certificate only to update the
existing synthetic emulator installation; it is **not a release deliverable**.

The real Android Keystore/SecureFileStore probe passed eight checks: all four
types persisted, recreation preserved entries, source/posting timestamps were
distinct, the synthetic patient name was absent from plaintext disk bytes,
clear survived recreation, the probe used an isolated no-backup directory,
damaged ciphertext failed closed, and explicit clear repaired that unreadable
history. [Storage receipt](evidence/api36-alarm-storage-0.23.0.json).

The production UI passed **24 native UI checks** with synthetic callbacks:
navigation/Back, Refresh dispatch, 6/12/24-hour choices, four log types and time
labels, cancelled/confirmed log clear, units/cadence, checked privacy semantics,
both notification presentations, notification restore/help, custom color,
widget/watch/account/diagnostics reachability, cancelled sign-out, unit retention,
rejected and successful alert saves, stale/offline/setup states, light/dark,
1.5×/2.0× font scale, landscape and scrolling appearance/alert sheets.
[UI receipt](evidence/api36-ui-0.23.0.json).

The initial UI fixture's production UI source is `ce30b73`, preceding the small
review corrections below. Its APK SHA-256 is recorded in the receipt.
Earlier harness attempts failed because scroll gestures targeted the wrong
landscape column or skipped the Low control. The corrected harness inspects
scrollable bounds, uses shorter slower swipes outside chart/wheel gesture areas,
and starts each fixture in a fresh task. A focused final landscape follow-up
scrolled the time choices fully into view, selected 12 hours and checked its
selected state; its screenshot replaces the partly visible earlier capture.

The updated final debug fixture passed **16 native chart checks**: one-finger
inspection, two-finger pinch, selection preservation, no two-finger timeline
panning, 6/12-hour resets, pointer-lift behavior, 1–24-hour bounds, accessibility
zoom, fixed scale/default and enabled limits, red/orange threshold traces, actual
out-of-range values, identical mmol/L geometry and dark-theme colors.
[Chart receipt](evidence/api36-chart-0.23.0.json).

Commands:

```sh
python3 scripts/validate_redesign_023.py emulator-5556
python3 scripts/validate_chart_gestures.py emulator-5556 --version 0.23.0
python3 scripts/validate_alarm_storage.py emulator-5556
```

Visual inspection covered light/dark Now, Logbook/empty state, notification
settings, appearance, rejected alert edits, 1.5×/2.0× font screens, landscape and
scrollable edit sheets. Labels wrap rather than overlap at larger fonts; fixed
navigation remains readable. Landscape uses independent scrolling columns; the
whole graph and all controls need not fit simultaneously in that short viewport.
UI counters check callback behavior, rather than real account/Settings persistence.

## Independent review and release identity

The alarm integration at `18632b0d2ea3a241e94ce81741b2a55cd1a7ec67` and combined
UI at `ce30b736362d0310e908eb3d1cdd9408f89f237c` received bounded read-only
reviews. Their one MEDIUM blocker was the same cleanup ordering issue: a failed
ledger write could skip notification cleanup after deleting cached readings.
The production `LocalHealthDataCleanup` path now attempts every independent
step, rerenders after successful reading cleanup or hides the old card if it
failed, and reports aggregate failure afterward. Four fault-injection tests
cover failed ledger/reading cleanup, multiple failures and success.

Two localized LOW UI findings were corrected: the chart viewport pill now uses
`onPrimaryContainer`, and Logbook values expose a spoken source trend and unit
instead of relying on the arrow glyph. Sign-out now gives a specific ledger
cleanup failure message; documentation records the encrypted/hidden residual
bytes and the required cleanup before another explicit sign-in.

The corrected debug fixture passed five focused native UI checks: reading and
navigation visibility, spoken Logbook units/trends for all four source values,
cancelled log clear, cancelled sign-out and the dark chart header. Both light
and dark Now and Logbook were inspected visually. The fixture SHA-256 and source
checkpoint are in [the correction receipt](evidence/api36-review-fixes-0.23.0.json).
Reproduce with:

```sh
python3 scripts/validate_redesign_023.py emulator-5556 --review-fixes
```

Cleanup failure-path unit tests simulate previously published cards and check
independent cleanup invocation. A real Android notification teardown with an
injected disk failure has not been exercised; the MainActivity effect wiring is
part of the source review. The focused accessibility checks verify semantics,
rather than running TalkBack.

The final independent read-only correction review approved production source at
`310d908229e434684de649d27de3f5adca330f66`: no HIGH/MEDIUM blocker remains, and
the original cleanup blocker and targeted LOW findings are closed. See
[review-023.md](review-023.md). The subsequent documentation-only clarification
above distinguishes failed local deletion from signed-out hiding; it changes no
production source. Final signed APK identity, clean release checks and delivery
source revision are recorded separately in the release manifests.

## Device limits and owner update

The owner reports that updating 0.21.0 through Android Studio on the same
computer succeeded after the release-signed APK could not replace that
installation. Debug and personal release signing keys remain distinct. The
owner's target phone is a Galaxy S25 Ultra with Android 16 / API 36 and One UI
8.5. A successful Studio update is not a phone qualification test for this
redesign. Proton compatibility testing of the 0.22.0 cookie candidate remains
pending.

Not run for 0.23: live account/source parity, physical Samsung UI/notification
behavior, TalkBack, display scaling, overnight/24-hour cadence, battery impact,
Android Auto, physical watch and audible alert delivery. Font-size and gesture
checks in the emulator cannot qualify Samsung behavior. The private LibreLinkUp
reference photos contain health data and were used only as local layout
references; all published evidence uses synthetic fixtures.
