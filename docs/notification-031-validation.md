# 0.31 notification grouping and platform eligibility

Candidate work over released `ebe3028`, 21 October 2026. The owner reports
Android 17 / One UI 9: the ongoing glucose card remains, but a low/high alert
replaces the status-bar number with a droplet. That is the reported symptom,
not a controlled Samsung reproduction.

## Implementation

The monitor and the one existing threshold alert now share an explicit group.
The existing alert is its summary, supplying the latest eligible numeric icon;
its original triggering text/time remain unchanged. The monitor is a child,
never a summary, so it retains platform Live Update shape eligibility. No third
notification, new ID/channel, polling loop, medical threshold or privacy grant
is added. Numeric preference, freshness, selected-person checks, silent icon
refresh and system-owned source-age expiry remain enforced.

Android's automatic GroupHelper compares icons with `Icon.sameAs`; separate
bitmap icons compare by bitmap identity. Equal-looking icons can therefore
yield an app-glyph automatic summary. The API36 fixture deliberately uses four
synthetic cards to cross its grouping threshold. Production retains at most
the monitoring card and one alert. This explains a plausible platform path,
not proof of the exact Samsung implementation.

Live Update requests first try the normal uncolorized template. If the running
framework reports it ineligible, they try colorization and retain it only if
that makes the shape eligible. The inspected early Android 16 implementation
requires colorization; current documentation says not to colorize. The actual
framework method decides, rather than an assumed mapping from SDK/One UI.
Eligibility does not grant promotion permission or prove Now bar appearance.
Diagnostics now includes shape eligibility, grouping and icon types without
exporting glucose, names or group identifiers.

## Evidence so far

- [Baseline 19 alert checks](evidence/api36-alert-icons-0.27.0-investigation-20261021.json)
  passed on clean `ebe3028` / existing 0.30 source. They show the prior build
  already supplies numeric individual alerts; they do not test Samsung grouping.
- [Candidate framework receipt](evidence/api36-notification-compatibility-0.31.0.json)
  records eleven passing API36 synthetic assertions, with its actual dirty-source
  and installed debug APK hash. It reproduces the automatic app-glyph summary,
  verifies the real two-card explicit group, current-number updates, unchanged
  triggering body/time, and the alert delete-intent callback retaining the
  real foreground-service card. Calling that callback is not an actual UI swipe.
- The fixture reports plain layout eligibility false, colorized eligibility true,
  requested layout eligibility true, and promotion permission false. Actual
  promotion is not tested. It does not establish Android 17 / One UI 9 behavior.
- A twelfth system-expiry check has been added but is not yet verified. A later
  rerun timed out during a build-host failure. It is not counted as a pass.
- Phone/watch JVM result files contain 236 / 15 tests with zero failures/errors/
  skips. The candidate debug build passed, but final lint initially found seven
  API-level annotations missing in the debug fixture. Its explicit API36 runtime
  guard now has a matching TargetApi annotation; verification remains pending.
- Subsequent compilation reported a temporary-file disk quota failure. Final
  clean build/lint, post-correction native reruns, independent source review,
  signing/static APK checks and publication remain pending. No 0.31 APK has
  been delivered or pushed at this checkpoint.

The new shared **Glance watch** Studio configuration targets `wear` and
`com.khaleel.glance.wear.MainActivity`; XML/module/manifest consistency was
checked. Interactive Studio pairing/deployment and physical Watch7 behavior
are not tested. [Watch setup](watch.md) explains same-computer debug signing,
Wi-Fi pairing, the separate pairing/connection ports, sharing and complications.

Reproduction requires a dedicated synthetic API36 emulator and debug APK:

```sh
./gradlew :app:testDebugUnitTest :wear:testDebugUnitTest :app:assembleDebug :app:lintDebug
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
python3 scripts/validate_notification_compatibility.py emulator-5556
python3 scripts/validate_alert_icons.py emulator-5556 --suffix grouping-031
python3 scripts/validate_notification_retention.py emulator-5556 --version 0.31.0
```

Do not run these destructive synthetic fixtures on a real account/device.
Source references inspected 21 October 2026:

- [Android 16 GroupHelper](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/notification/GroupHelper.java)
- [Android 16 Icon comparison](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/graphics/java/android/graphics/drawable/Icon.java)
- [Android 16 notification eligibility](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/Notification.java)
- [Current Live Update requirements](https://developer.android.com/develop/ui/views/notifications/live-update)
