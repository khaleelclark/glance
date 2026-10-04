# Personal APK build and installation

Glance uses application ID `com.khaleel.glance`, versionCode `29`, and versionName
`0.25.0`. The Android Studio project is the root of this repository. The private
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

The owner's current phone installation was updated successfully through Android
Studio on the same computer. Use that same computer and debug key for this update:
pull `main`, select the `app` debug variant and Run it on the connected phone.
The release-signed APK cannot update a differently signed debug installation.
Cancel an uninstall prompt to preserve local settings and inspect its error.

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

1. Transfer `release/glance-0.25.0-personal.apk` to the phone and verify its
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
   samples: use one finger to inspect exact readings on the graph.
   **3 hr**, **6 hr**, **12 hr** and **24 hr** select the latest window;
   6 hours is the default. Pinch and two-finger dragging do not change the window
   or selected reading. Its vertical scale
   stays at 40–350 mg/dL, converted to the selected unit. Low/High lines follow
   enabled ordinary alert settings, falling back independently to 70/240 when
   off. Below Low is red; above High is yellow. Edge arrows mark readings outside
   the scale; the value readout on the graph retains the actual value, with
   its measurement time aligned beneath the crosshair. The current reading places the
   value, unit, and trend arrow together, with a plain-language trend below;
   landscape places the graph beside it. Settings has color presets, a
   touchable color wheel, brightness slider, and a custom six-digit hex field; light and dark themes
   follow the phone. Use Refresh in the app, and Stop
   monitoring in Settings to end the session. Test screen-off
   behavior before relying on the chosen polling interval.

Optional threshold alerts start **off**. In Settings, tap a level to open its
edit sheet, scroll the value wheel to choose its threshold, and use the repeat
slider to set a cooldown from one to 60 minutes (five minutes initially).
Urgent Low and Urgent High take precedence
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

## 0.23.0 focused redesign and alarm log

The default accent is ocean blue (`#1677D2`), with a rounded native G logo on
the phone and watch. Existing custom accents remain supported. Now places the
reading above a larger source graph; signed-in navigation is Now / Logbook /
Settings. Common settings use compact rows and quick-edit sheets. Multiple-person
monitoring, dashboards and per-person thresholds remain deferred.

Logbook distinguishes Low, Urgent Low, High and Urgent High notifications posted
by Glance. Each row retains the source reading and measurement time separately
from the posting time. It records Android accepting a post, which does not prove
sound was heard. History is encrypted, excluded from backups and diagnostics,
and limited to 1,000 records with 30-day pruning on load and append. Clear asks
for confirmation and preserves alert settings, sample deduplication and cooldowns.
Explicit new sign-in requires successful history cleanup. Sign-out and local
health-data deletion attempt cleanup and report partial storage failures. If
sign-out cannot erase the ledger, it remains encrypted and hidden; another
explicit sign-in cannot proceed until log cleanup succeeds.

See [redesign-023-validation.md](redesign-023-validation.md) for the exact
automated/emulator evidence and remaining physical-device checks. For the owner's
existing Android Studio installation, update using the same computer's `app`
debug variant; a release APK with a different signer cannot update that install.

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
Lock-screen value changes share that gate, invalidate pending public cards,
and clear the current monitoring and alert cards before a private replacement.

This upgrade starts **Show readings on lock screen** off, including when the old
version had it on. Re-enable it in Settings only if you want public readings.
The separate checked privacy store then preserves successful choices on later
upgrades. A failed save leaves current rendering hidden and can delete just that
grant to revoke it durably. If both saving and deletion fail, Glance reports the
failure and directs you to Android's lock-screen notification controls before
restarting; it does not claim to have saved the hidden choice.

## Wear OS companion (0.19.1)

Glance now includes a companion watch app, a source-glucose/trend complication
and an optional native silent card. Install the separate same-key watch APK;
[watch.md](watch.md) covers Wireless debugging sideloading, sharing privacy,
complication selection and notification permission. Phone monitoring owns all
LibreLinkUp requests. Watch7 / Wear OS6 / One UI Watch8.0 was reported by the
owner; physical watch qualification remains pending. Existing successfully
saved lock-screen grants persist through this upgrade.

## 0.20.0 presentation refresh

The phone UI uses neutral surfaces, custom type/shape defaults and compact
controls. The current reading and history are flat sections rather than nested
cards. Settings groups expand on tap; Display opens initially. App color still
contains every preset, the wheel/brightness controls and the hex field. Short
setting summaries remain visible when their group is open, with original longer
explanations behind About/Setup buttons. Lock-screen/widget/watch privacy
consequences remain beside their controls. Alerts, cadence, restart, permissions,
battery links, account controls and destructive confirmations are retained.
Android Back closes Settings. No persisted settings migration was added.

The LibreLinkUp adapter, poller, alerts, notification layouts, widget/watch
runtime and chart math/gestures are unchanged. Existing Live Update preferences
are respected; turn Status chip Live Update off for the regular graph card.
See [UI refresh validation](ui-refresh-validation.md) for synthetic checks and
physical-device boundaries. The watch needs no reinstall for this UI change.

## 0.21.0 UI overhaul

A defined reading hero, layered surfaces, consistent toolbar and three Settings
pages replace the flat layout and accordion maze. Everyday shows the common
preferences directly; Alerts contains glucose limits and sounds; More contains
appearance, widgets, watch, system links and account controls. Individual
About expanders are replaced with concise summaries. Privacy consequences remain
visible at the relevant controls, and destructive actions retain confirmation.

Show notification is located with notification settings; it invokes the same
restoration action formerly labeled Restore card. The chart defaults to the latest
6 hours and has 6/12/24-hour choices below it. Touch inspects real observations;
pinching zooms without two-finger timeline scrolling. Axis limits and alert-driven
colors are retained. Accessibility viewport actions now report actual changes and
announce the visible time range. Special source values are excluded from numeric
graph geometry and coincident limit lines honor red priority. Alert wheels persist
each value change immediately so switching pages cannot cancel a pending save.

No notification renderer, polling, credential storage, alert policy, widgets or
watch runtime change. Existing watch installs remain compatible. Synthetic native
checks and live-device boundaries are recorded in docs/ui-overhaul-validation.md.

## 0.22.0 VPN compatibility candidate

LibreView-issued Secure cookies now remain in memory across bootstrap and
regional requests. The jar is restricted to reviewed HTTPS origins and clears
on sign-in/sign-out. No VPN routing, User-Agent, TLS validation or request-cadence
change was made. This is a candidate for the owner's Proton Free setup failure,
not a verified live correction. Setup errors and Diagnostics now include the
HTTP status and a coarse response type, without bodies, headers or cookie values.

Install the phone APK over the current version with Proton enabled, then retry
the shared-person screen. If it fails, report the displayed HTTP status/type.
If an existing session still fails, reconnect in Glance to test a fresh login
sequence; process restarts intentionally drop temporary cookie state. There is
no watch runtime change and an existing compatible watch install needs no update.
See [VPN investigation and verification](vpn-compatibility.md).

## 0.24.0 graph and reading refinement

Now gives the graph more space without a nested rounded card. The horizontal
value grid and green range band follow the existing ordinary Low/High settings
(or 70/240 mg/dL defaults when those alerts are disabled). Fresh current readings
use green/yellow/red range presentation; delayed, stale and unavailable states
retain truthful labels and neutral presentation. This is display classification,
not a change to alert policy or a new target-range setting.

Selected source value/unit/time appears on the graph crosshair. Touch scrubs real
observations. The only time-window controls are 3/6/12/24-hour presets; pinch zoom
is removed. A small accessible activity dot replaces the running-status strip,
Refresh uses a clear arrow, and Pause is removed from Now. Stopped/error states
still offer recovery; system Stop, Settings stop and sign-out retain their
existing effects. The native logo is now an eye mark. Redundant Settings,
Logbook and graph explanatory text is removed.

See [refinement validation](refinement-024-validation.md) for exact build,
synthetic fixture and physical-device boundaries. Update the owner's existing
Studio build through the same computer/debug key rather than installing a
differently signed personal APK over it.


## 0.25.0 personal polish

Sugar-cube marks replace the eye throughout phone and watch resources. Muted teal
replaces the prior default Ocean blue; other custom colors remain available.
The selected source time is directly below the value in the top graph tooltip.
The graph is taller, hour controls are visually smaller with accessible touch
targets, and the status indicator sits beside Refresh. The trend/delta separator
is removed and the freshest age reads “Just Now”.

Settings → Reading → Follower nickname changes the displayed name, including
the ongoing notification. Blank restores the source name; source connection IDs
and readings are unaffected. Nicknames are stored encrypted per connection and
cleared at account boundaries.

The app draws its own surface behind the transparent system navigation bar and
disables Android's three-button contrast scrim, following
[Android's edge-to-edge guidance](https://developer.android.com/develop/ui/views/layout/edge-to-edge).
Buttons remain light in dark mode and dark in light mode for readability.
Samsung-specific rendering remains an on-phone check.

See [0.25 validation](refinement-025-validation.md) for exact candidate and results.
Update with Android Studio using the same computer's existing signing key and
`git pull --ff-only` on main. This preserves the installed app's settings. A
personal signed APK cannot replace a build signed with another key.
