# Personal APK build and installation

Glance uses application ID `com.khaleel.glance`, versionCode `16`, and versionName
`0.15.0`. The Android Studio project is the root of this repository. The private
GitHub repository is `khaleelclark/glance`.

## Current qualification

The APK is an **early personal test build**. Synthetic protocol tests, local
state tests, lint, and the API 36 emulator checks in
[device-validation.md](device-validation.md) pass. A real LibreLinkUp follower
account, Patrick's same-timestamp values, the Galaxy S25 Ultra, Samsung One UI,
screen-off cadence, and battery impact have **not** been tested. Do not use this
build as a medical alert or replace LibreLink's own warnings.

## Build from a clean checkout

Clone the repository and open its root in Android Studio. Use JDK 17, Android
SDK Platform 37, SDK Build-Tools 36.0.0, and an SDK path in `ANDROID_HOME`
or `local.properties`. Run:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease
```

This produces `app/build/outputs/apk/release/app-release-unsigned.apk`.
Android Studio can run the debug variant without the personal signing key.

## Keep the upgrade key

The personal release keystore and its password file live outside the repository
at `~/.local/share/glance/personal-release.jks` and
`~/.local/share/glance/signing-password.txt` on the build host. They are mode
0600. **Back up both files to a secure location you control before installing
the release APK.** The key was created on 2026-09-27 and expires in 2054.
Losing the key or password prevents normal in-place upgrades. Neither file
belongs in Git, an APK, a screenshot, or a support log.

On another build machine, copy both files securely to the same directory and
keep them mode 0600. Alternatively, point `GLANCE_SIGNING_DIR` at the directory
containing the two files. From a clean checkout, run:

```sh
python3 scripts/sign_release.py
```

The script refuses dirty source, cleans and rebuilds the APK, runs unit tests
and both lint variants, signs with the existing private key, verifies the APK
signature, and writes the signed APK and metadata under the ignored
`release/` directory. The exact APK SHA-256, certificate SHA-256, source commit,
and verification status are in `release/manifest.json`. Keep the same
application ID and signer for every upgrade; increment versionCode.

## Install and verify on the phone

1. Transfer `release/glance-0.15.0-personal.apk` to the phone and verify its
   SHA-256 against `release/manifest.json`.
2. Open the APK on the phone. If Android asks, permit installation from that
   trusted file manager. Do not disable broader device protections.
3. Sign in **on the phone** with the LibreLinkUp follower account, select
   Patrick explicitly, and compare the reading and timestamp with LibreLinkUp.
4. Allow notifications, choose whether the lock screen may reveal the value,
   review battery settings, then tap **Start monitoring**.
5. Confirm one silent card appears with the measurement time, glucose value,
   trend arrow, and recent change when an earlier reading is available. Glance
   requests the status chip Live Update by default where Android supports it;
   promoted cards stay expanded. Turn that switch off in Settings for the large,
   collapsible card. The main screen has a 24-hour timeline of validated source
   samples: drag across the graph to inspect exact readings; swipe the time
   labels sideways to browse the full day. The current reading places the
   value, unit, and trend arrow together, with a plain-language trend below;
   landscape places the graph beside it. Settings has color presets, a
   touchable color wheel, brightness slider, and a custom six-digit hex field; light and dark themes
   follow the phone. Use Refresh in the app, and Stop
   monitoring in Settings to end the session. Test screen-off
   behavior before relying on the chosen polling interval.

Optional threshold alerts start **off**. In Settings, enable each desired level,
scroll the value wheel to choose its threshold, and scroll the repeat wheel to
set a cooldown from one to 60 minutes (five minutes initially). Urgent Low and Urgent High take precedence
over their ordinary level. A new, fresh numeric reading can sound at most once
per category per cooldown; repeated source responses never sound again. Each
category's sound and priority are chosen in its own Android notification-channel settings.
Glance registers all four categories when opened and links to each category
from the alert list. Samsung may require enabling **Manage notification
categories for each app** under Advanced notification settings to show them.
On Android 16, expand **Show unused categories** to see alert categories before
their first notification.
Urgent channels request high importance and an alarm sound; Android and the
phone owner still control notification permission, Do Not Disturb, sound,
heads-up display, and delivery. Alerts share the existing polling schedule and
are not a substitute for LibreLink's own alerts.

To upgrade, build a higher versionCode APK signed with the same keystore and
install it over the existing app. Verify account/settings retention. An APK
signed with another key cannot replace the installed app without uninstalling,
which removes local Glance data.

## Recovery

Use Refresh and the in-app Diagnostics view first. If a reading is old,
check its measurement time, connection state, notification permission, battery
limits, and next retry time. If terms or policy acceptance is required, complete
that in the official LibreLinkUp app, then reconnect in Glance. After a
force-stop or Android Task Manager Stop, open Glance and explicitly resume.
Reinstall only after checking the state and preserving the signing key.

## Samsung Live Update and glucose icon (0.15.0)

The owner reports Android 16 / One UI 8.5 on the Galaxy S25 Ultra. Version
0.15 requests promoted ongoing notifications on Android 16.0 using the
platform promotion extra, avoiding the builder method introduced in 36.1.
It still checks whether promotion is allowed and uses the regular custom card
when unavailable. Settings displays the observed promotion state and provides
a Live Update settings shortcut, with the app notification page as fallback
when the device has no dedicated screen.

On Samsung, check Settings → Notifications → App notifications → Glance for
Allow notifications, lock-screen display, and Live notifications if offered.
Then check Settings → Lock screen and AOD → Now bar → Live notifications
(or See all) and enable Glance if listed. Samsung controls app eligibility;
Android 16 and a promotion request alone do not guarantee a Now bar card.

Glucose number icon starts enabled and draws the selected unit's fresh number
as a monochrome small icon. Paused, starting, absent, delayed, stale, special
LOW/HIGH, invalid and excessively long values retain the Glance icon.
The redacted public lock-screen card uses the Glance icon and no critical text.
Android's lock-screen privacy and icon visibility settings still apply.

The monitoring notification sets ongoing and NO_CLEAR: Clear all leaves it in
place. Android 14 and newer can still permit an individual dismissal while
unlocked. Glance respects that dismissal and offers Show notification again;
it does not repost a dismissed card in a loop or impersonate a call/alarm.

Primary references (checked 2026-09-30):

- https://developer.android.com/develop/ui/views/notifications/live-update
- https://developer.android.com/reference/android/app/Notification.Builder#setRequestPromotedOngoing(boolean)
- https://developer.android.com/reference/android/provider/Settings#ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS
- https://developer.android.com/about/versions/14/behavior-changes-all
- https://www.samsung.com/uk/support/mobile-devices/how-to-use-the-now-bar-on-the-lock-screen-of-your-samsung-galaxy-device/
