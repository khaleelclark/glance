# 0.31 numeric group icons and Live Update eligibility

Work over released `ebe3028`, 21 October 2026. The owner reports Android 17 /
One UI 9: the ongoing glucose card remains, but a low/high alert replaces the
status-bar number with a droplet. This is the reported symptom, not a controlled
Samsung reproduction.

## Current implementation

Android 17+ monitor and alert numeric small icons encode the same existing
96×96 alpha mask as deterministic PNG `Icon.TYPE_DATA`. Android's `Icon.sameAs`
compares data contents, while bitmap icons compare object identity. Equal
numbers can therefore supply a numeric automatic group summary rather than an
app glyph. Android 17 GroupHelper refreshes summary attributes when child
attributes change, allowing the common number to follow newer readings.

The production API36-and-earlier path retains the original bitmap representation:
its section-based GroupHelper can keep initial summary attributes, so a numeric
automatic summary could otherwise become frozen. No app-provided notification
group or summary, extra card, ID, sound channel, polling loop, medical threshold
or privacy grant is introduced. The alert keeps its triggering text/time while
its icon follows the latest eligible reading. Source-person, privacy, numeric
preference, freshness and system-owned alert-expiry gates remain unchanged.

Live Update first tries the uncolorized template. If the running API>=36 framework
reports it ineligible, Glance tries colorization and retains it only when that
makes the shape eligible. Early Android 16 requires it; current documentation
prohibits it. The running framework decides, rather than an assumed SDK/One UI
mapping. Eligibility does not grant permission or prove Now bar appearance.
Diagnostics separates eligibility, requested/actual promotion, grouping and
icon types, without exporting glucose, names or group identifiers.

## Verification status

- [Baseline 19 alert checks](evidence/api36-alert-icons-0.27.0-investigation-20261021.json)
  passed on clean `ebe3028` / existing 0.30 source. The prior build already
  supplies numeric individual alerts; this does not test Samsung grouping.
- The revised fixture tests encoded image delivery/pixels, a numeric initial
  automatic summary, the API37 production gate, separate monitoring/alert cards,
  latest-number refresh, safe diagnostics and actual framework layout eligibility.
  Android 17-only assertions additionally test summary updates and numeric
  removal, but are not run on API36. Final revised results are pending.
- Phone/watch JVM result files contain 236 / 15 tests with zero failures/errors/
  skips. The prior candidate's corrected debug lint passed with zero errors.
  Final clean release build/lint, revised native checks, review, signing/static
  verification and publication remain pending. No 0.31 APK is delivered yet.
- The temporary-file quota failure was worked around by using an ignored,
  disk-backed Java temp directory; failed attempts are not counted as passes.

The shared **Glance watch** Studio configuration targets `wear` and
`com.khaleel.glance.wear.MainActivity`; XML/module/manifest consistency is checked.
Interactive Studio pairing/deployment and physical Watch7 behavior remain
unverified. [Watch setup](watch.md) covers matching signing keys, pairing versus
connection ports, phone sharing and watch-face complications.

## Discarded explicit-group candidate

`dc708a4` made the existing alert an explicit group summary with the monitoring
card as a child. Its [12-check API36 receipt](evidence/api36-notification-compatibility-0.31.0-group-candidate.json)
records real FGS preservation after a delete-intent callback and alert timeout,
but did not test a genuine UI swipe. Independent review found a MEDIUM: a user
clear of an app-provided summary can cascade to its children, unlike the tested
callback. That parent/child relationship has been removed entirely. The candidate
was never pushed, signed or delivered, and its receipt does not qualify the
revised implementation.

Reproduction uses a dedicated synthetic emulator and debug APK:

```sh
./gradlew :app:testDebugUnitTest :wear:testDebugUnitTest :app:assembleDebug :app:lintDebug
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
python3 scripts/validate_notification_compatibility.py emulator-5556
python3 scripts/validate_alert_icons.py emulator-5556 --suffix grouping-031
python3 scripts/validate_notification_retention.py emulator-5556 --version 0.31.0
```

Never run these destructive synthetic fixtures on a real account/device.
API36 construction/pixel tests and source inspection do not qualify physical
Android 17 / One UI 9 grouping, audible/DND delivery, Samsung Now bar, actual
promotion, live LibreLinkUp, watch bridging, overnight cadence or battery use.

Primary references inspected 21 October 2026:

- [Android 17 GroupHelper](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/notification/GroupHelper.java)
- [Android 16 GroupHelper](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/notification/GroupHelper.java)
- [Icon comparison](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/graphics/java/android/graphics/drawable/Icon.java)
- [Android 16 eligibility](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/Notification.java)
- [Current Live Update requirements](https://developer.android.com/develop/ui/views/notifications/live-update)
