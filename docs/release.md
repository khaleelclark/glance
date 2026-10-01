# Personal APK build and installation

Glance uses application ID `com.khaleel.glance`, versionCode `20`, and versionName
`0.18.1`. The Android Studio project is the root of this repository. The private
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

1. Transfer `release/glance-0.18.1-personal.apk` to the phone and verify its
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
When lock-screen values are hidden, the original notification also uses the
Glance icon and no critical text, including while unlocked: publicVersion alone
cannot reliably hide the original icon/chip on every Samsung surface.
Promoted Live Updates use the Glance icon beside their chip value, avoiding a
duplicate number; the numeric icon applies to the regular card. The bitmap has
padding for OEM icon scaling, but Samsung decimal/three-digit readability still
needs the owner's check. Android's lock-screen privacy settings still apply.

The monitoring notification requests ongoing and NO_CLEAR on both its original
and redacted public versions. Stock Android excludes it from Clear all, but the
owner reports Samsung removing it even through Clear. Android 14 and newer can
also permit an individual swipe while unlocked. See the 0.18 retention choice
below; an app cannot make every OEM surface impossible to dismiss.

Primary references (checked 2026-09-30):

- https://developer.android.com/develop/ui/views/notifications/live-update
- https://developer.android.com/reference/android/app/Notification.Builder#setRequestPromotedOngoing(boolean)
- https://developer.android.com/reference/android/provider/Settings#ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS
- https://developer.android.com/about/versions/14/behavior-changes-all
- https://www.samsung.com/uk/support/mobile-devices/how-to-use-the-now-bar-on-the-lock-screen-of-your-samsung-galaxy-device/

## Reference card layout (0.16.0)

Turn off **Status chip Live Update** to use the custom card matching the owner's
reference: trend arrow, lighter large reading, then unit. Fresh compact cards
have one clean row; expand for the source reading time, person, trend, recent
change and connection notes. Old/error cards retain their explicit status and
last-reading details. Android owns the shell, background, icon placement and
expand control; Samsung's exact appearance needs a device check.

A small purple graph thumbnail uses only genuine, same-person numeric samples
from the three hours ending at the current source measurement. It requires at
least two samples, leaves gaps over fifteen minutes unconnected, and never
manufactures history. Nonfresh/stopped/special states omit it. Hidden lock-screen
values suppress the original numeric icon, thumbnail and chip text even while
unlocked. The numeric status icon uses a lighter condensed face, retaining
mg/dL, mmol/L and stale fallbacks. Live Update remains available with Android's
standard layout; existing display-mode preferences are preserved.

## Phone and car widget (0.17.0)

Enable **Widget values** in Glance Settings; readings on widgets are visible to
anyone who can see the phone/car screen, including while the phone is locked.
This choice is separate from lock-screen notification privacy and defaults off.
Sign-out turns it off. Add **Glance glucose** using the phone widget picker.
For Android Auto, add it in the car-widget picker if that feature is offered;
Google's rollout controls availability. This is a mobile AppWidget prepared for
car hosts, not a separately qualified Android Auto app or an overlay over Maps.

The card uses the existing monitoring session and encrypted selected-person
data. It has no second network scheduler. Its measurement time always remains
visible; fresh values have a source arrow, actual change interval when available,
and a four-hour graph in larger sizes. Compact/large-font sizes omit the graph
before hiding essential reading information, and retain available delta on
a separate line at narrow widths. Delayed, stale, missing, stopped,
wrong-person and action-required states do not appear as a fresh numeric card.
Source gaps over 15 minutes are not joined; missing history is not fabricated.
Android may retain a widget's last rendered view if the app cannot execute, so
check the absolute measurement time. Host refreshes are best effort.

See [car-widget.md](car-widget.md) for official references, setup and verification
limits. Actual Android Auto dashboard hosting, Samsung appearance, live source
parity and screen-off/battery qualification require the owner's device tests.

## Notification retention (0.18.1)

The owner reported on 0.17.0 that both Clear and a sideways swipe removed the
card, although Android reported it promoted. **Keep monitoring card visible**
now starts enabled in this personal build, reflecting the owner's explicit
request to retain the card. While the current session is running, a valid
delete intent restores the same silent regular glucose card with the latest
controller state. It does not restart monitoring, fetch data, or repeatedly
request promotion. After dismissal the regular layout remains in use across
process recreation; an explicit Start/Resume, Show notification again, or
switching Live Update off/on can request promotion again.

Turn retention off to retain the previous behavior: dismissal suppresses the
card until an explicit restore. Stop monitoring, sign-out, system Stop and
force-stop remain stop controls. Android/Samsung can still remove a card briefly,
block notifications, or prevent delivery of its delete intent; this is recovery,
not an unremovable notification guarantee.

Diagnostics now shows the posted ongoing, Clear-all protection, foreground and
promoted flags, whether chip text/custom layout was supplied, channel importance,
and retention state. It exports no chip contents or patient details. If Glance's
lock-screen values are hidden, it deliberately supplies no numeric chip text.
Android promotion alone does not establish Samsung Now bar admission. The Now
bar still requires an owner check and can be enabled for Glance if Samsung lists
it under Settings → Lock screen and AOD → Now bar → View more.

See [notification-0.18-validation.md](notification-0.18-validation.md) for bounded
verification. This choice supersedes the baseline's manual-only dismissal
restoration for this personal build; dismissed Live Updates return as regular
cards rather than being re-promoted automatically.

In 0.18.1, notification cancellation and publication share a generation gate.
Stop, sign-out and person change cancel last when a publication is already in
flight, and reject callbacks prepared before cancellation. Dismissal also
invalidates a pending promoted card. The settings switch retains its previous
accepted choice and reports an error when persistence fails.
