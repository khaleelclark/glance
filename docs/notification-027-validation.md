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
the small icon; the alert explicitly names High and retains its source time.

Reproduction (dedicated synthetic API 36 emulator, debug APK only):

```
ANDROID_HOME=/home/phoenix/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 shell pm grant com.khaleel.glance android.permission.POST_NOTIFICATIONS
python3 scripts/validate_alert_icons.py emulator-5556
python3 scripts/validate_notification_retention.py emulator-5556 --version 0.27.0
```

Independent bounded source review is tracked and pending. Clean release
assembly/signing, signer continuity and source push remain pending. Final signed
APKs are not claimed to run in the emulator. These synthetic checks cannot
qualify live LibreLinkUp, Samsung One UI, TalkBack, natural overnight/background,
battery, physical-watch or audible-alert behavior.
