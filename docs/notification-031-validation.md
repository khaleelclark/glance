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
- [15 revised framework checks](evidence/api36-notification-compatibility-0.31.0.json)
  pass on clean `db8eb4e`, debug fixture SHA-256 `920530e626bcfaa2c67f6f9326fa5c3f8401b2586a87e5848ece0a8b30c64f16`.
  They check encoded image delivery/pixels, numeric initial automatic summary,
  the API37 production gate, separate cards, latest-number refresh, FGS expiry,
  safe diagnostics and actual framework layout eligibility. Android 17-only
  dynamic-summary/number-removal assertions are NOT RUN on API36.
- [19 alert checks](evidence/api36-alert-icons-0.27.0-grouping-031.json) and
  [10 retention/privacy checks](evidence/api36-notification-0.31.0-retention.json)
  pass against `db8eb4e` with dirty-source debug exception-reporting/evidence
  changes. Their exact installed fixture hash is recorded; the later debug
  change only adds failure stack/completed-check reporting. Retention includes
  actual glucose-card swipes with Keep visible on/off. It does not test a
  Samsung automatic-group swipe. An earlier alert-probe attempt failed without
  useful detail; the instrumented retry passed all 19, and is the claimed run.
- [Pinned primary-source audit](evidence/android-framework-icon-audit-0.31.0.json)
  records exact Android 16/17 revisions and hashes for GroupHelper and Icon.
  Source inspection is not an Android 17 runtime test.
- Clean signed build at `4be0762` passes 236 phone / 15 watch tests with zero
  failures/errors/skips. Both debug/release lints have zero errors (phone 93
  debug / 57 release warnings; watch zero). Both APK signatures retain the
  existing personal signer, version 0.31.0 / code 36, package identity and
  non-debuggable status; debug components/fixture markers are absent. The APKs
  have static verification and are not runtime-tested as signed artifacts.
- [Independent correction review](review-031.md) closes the prior MEDIUM with no
  HIGH, MEDIUM or concrete LOW at exact `db8eb4e`. Signed source `4be0762` has
  byte-identical production source; later changes only add debug reporting,
  guides and evidence. [Release files](../apks/README.md) include both APKs,
  immutable manifests, checksums and the verification/install bundle.
- Temporary quota/build and fixture-annotation failures were corrected; failed
  attempts are not counted as passes. Java temporary output was redirected to
  an ignored disk-backed directory.

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
python3 scripts/validate_notification_compatibility.py emulator-5556 --suffix rerun-YYYYMMDD
python3 scripts/validate_alert_icons.py emulator-5556 --suffix grouping-031
python3 scripts/validate_notification_retention.py emulator-5556 --version 0.31.0
```

Never run these destructive synthetic fixtures on a real account/device.
API36 construction/pixel tests and source inspection do not qualify physical
Android 17 / One UI 9 grouping, audible/DND delivery, Samsung Now bar, actual
promotion, live LibreLinkUp, watch bridging, overnight cadence or battery use.

### Post-release owner observation — 22 October 2026

After the explicit **Show notification** retry, the owner's reported 0.31.0 /
API37 diagnostics show promotion requested=true, promoted=true, eligible=true,
chip text supplied=true, custom layout=false and colorization=false, with no
active alert or automatic group. This confirms framework promotion at the
capture. The paired shade image still shows the standard Glance card below
other notifications, and no prominent status chip is visible. Lock-screen/Now
bar evidence was not supplied. The [redacted observation receipt](evidence/owner-api37-live-update-promotion-20261022.json)
records the limited result; no installed APK hash was collected. Samsung visual
admission and the low-alert icon issue remain open. No production patch or new
APK follows from this observation; the next check is Samsung's available
Live notifications/Now bar controls.

Primary references inspected 21 October 2026:

- [Android 17 GroupHelper](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/notification/GroupHelper.java)
- [Android 16 GroupHelper](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/notification/GroupHelper.java)
- [Icon comparison](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/graphics/java/android/graphics/drawable/Icon.java)
- [Android 16 eligibility](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/Notification.java)
- [Current Live Update requirements](https://developer.android.com/develop/ui/views/notifications/live-update)
