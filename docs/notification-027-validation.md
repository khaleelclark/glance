# Glance 0.27 glucose status icons and retention

Production candidate 227b9436cb61cb86d425059a5bc0817c2cdda410 on released
0.26 base c84ef6d. Root owns implementation, native/build/sign/push work.
The owner reported that an alarm replaces the status-bar glucose number with
the app glyph. Alerts now share the ongoing card's numeric policy, and a visible
alarm's icon updates to the latest reading quietly. The alarm body retains its
original category and triggering source reading/time. Enhanced requests no longer
intentionally substitute a glyph for an eligible number. User icon preference,
privacy, freshness, unit and source-person identity remain enforced.

The owner also requested a reading card immune to swipe/Clear all. Existing
ONGOING/NO_CLEAR and default-on Keep notification visible behavior is retained;
its explanatory setting text is improved. Android 14+ allows individual swipes
on ordinary ongoing cards, so restoration is the supported personal-build
behavior, with no promise that the card never moves or disappears briefly.
Stop/sign-out still remove it, and retention off respects a swipe.

Debug checks on committed candidate: 219 JVM tests, zero failures/errors/skips;
debug assembly succeeds; debug lint has zero errors. Two engine tests show
icon refresh neither reevaluates alarms nor modifies history/cooldowns, and
rejects an obsolete display while honoring the current privacy choice. A source
number test covers all four threshold ranges and mismatched person identity.
The initial build was in progress while further tests were added; only the
subsequent complete 227b943 build is used for these results.

Native framework instrumentation passes 16 assertions on clean 227b943, debug
APK SHA-256 94bfce960085a43225a78ab8486839feaab5408ec1b3bc208a12f63436a3b014.
Real NotificationManager small-icon bitmap pixels match the expected number
for Low/Urgent Low/High/Urgent High. A High event keeps its original 240 mg/dL
body/time/channel while its icon becomes 248, then 13.8 after a unit switch.
Refresh sets ONLY_ALERT_ONCE; actual audible delivery is not qualified. Stale
readings remove the number, hidden/private versions use resource icons, and
the number setting applies to alarms. An old delete token cannot remove a new
alarm; a real delete PendingIntent callback prevents refresh resurrection.
Missing host notifications are not reposted; person changes clear the old alarm.
A new notifier removes unscoped prior-process numeric alarms, retaining history.
Regular and enhanced requests use the same eligible number, but actual promoted
admission on this emulator is unavailable. See api36-alert-icons-0.27.0.json.

Ten additional foreground-service retention checks pass on the same candidate
and fixture, with generated evidence/scripts/docs dirty. Actual Clear all
removes a disposable DEMO notification while keeping Glance, and an actual swipe
restores the regular card. Privacy, old-generation rejection, retention off,
Stop, person change and sign-out are rechecked. Exact source/fixture metadata
was added to the new version's receipt after collection; the older 0.18 receipt
was not overwritten. See api36-notification-0.27.0-retention.json.
Root inspected the High DEMO screenshot: both alert and ongoing card show 240 as
the small icon; the alert explicitly names High and retains its source time. The separate
status-bar screenshot uses the direct notification fixture above the emulator
home screen; this isolates icon rendering rather than exercising a live controller.

Reproduction (dedicated synthetic API 36 emulator, debug APK only):

```
ANDROID_HOME=/home/phoenix/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 shell pm grant com.khaleel.glance android.permission.POST_NOTIFICATIONS
python3 scripts/validate_alert_icons.py emulator-5556 --suffix expiry
python3 scripts/validate_alert_expiry.py emulator-5556
python3 scripts/validate_notification_retention.py emulator-5556 --version 0.27.0
```

Independent source review accepts the original icon/retention changes with no
HIGH/MEDIUM blocker, but found one LOW: a numeric ordinary alert could remain
past the freshness boundary after process death. Correction ce47d06 uses system
Notification.Builder.setTimeoutAfter based on the source measurement deadline,
sharing the unchanged five-minute ReadingPolicy.FRESH_WINDOW. Numeric eligibility
is rechecked against current wall time before posting/updating. New samples renew
the timeout even when the value is unchanged; duplicate samples do not extend it.
Expiry removes only the numeric alarm card, keeping its encrypted Alarm Log entry;
it does not remove the ongoing reading notification. No new permission, application
scheduler or source poll is introduced.

Corrected debug assembly/lint passes, with 222 JVM tests and zero failures/errors/
skips. All 19 native icon checks pass on clean ce47d06, fixture SHA-256
37877c8b05111a370edde801793fbf701b29dd1f237b0f24681a0bcf3e54adb1.
The added native checks verify the remaining-source timeout, renewal for a new
sample with the same value, and no extension for a duplicate. Receipt:
api36-alert-icons-0.27.0-expiry.json. The older 16-check receipt is retained.

Android's [notification timeout contract](https://developer.android.com/reference/android/app/Notification.Builder#setTimeoutAfter(long))
cancels the notification after its duration. The inspected [Android 16 framework](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/notification/NotificationManagerService.java)
scheduleTimeoutLocked uses a system-owned exact-and-allow-while-idle elapsed-time
wake. This is platform-managed expiry, not an app alarm or foreground ticker.

The separate near-boundary process-death probe passes all three assertions on
the same ce47d06 fixture, with generated docs/scripts/evidence dirty. A source
reading is already 288 seconds old, leaving 12 seconds of the real five-minute
freshness window. The background app is actually killed with SIGKILL; its ordinary
alarm remains visible after death, then the system removes it without a manual
reopen/render. The first observation without the alert was at 18:18:56.141 UTC,
after its 18:18:55.291 source deadline (less than one second observation latency).
This does not promise millisecond scheduling or Samsung behavior. The system's
delete callback may start a new process after cancellation; its empty ledger
cannot remove the retained alarm by itself. Receipt: api36-alert-expiry-0.27.0.json.

Early attempts did not pass the probe preconditions: waiting for a window from a
short-lived activity timed out, and asynchronous fixture/notification posting
needed explicit completion and presence checks. The final script starts only the
isolated fixture task, confirms a newly written receipt and system notification,
then kills the background process. Setup force-stop is distinct from the tested
SIGKILL; no failed attempt is counted as expiry evidence.

Independent correction closure is tracked and pending. Clean release
assembly/signing, signer continuity and source push remain pending. Final signed
APKs are not claimed to run in the emulator. These synthetic checks cannot
qualify live LibreLinkUp, Samsung One UI, TalkBack, natural overnight/background,
battery, physical-watch or audible-alert behavior.
