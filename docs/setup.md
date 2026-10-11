# Phone setup and everyday use

Applies to Glance 0.30.0. For installation/signing, start with
[release and upgrades](release.md). The app follows one explicitly selected
LibreLinkUp connection; it does not connect directly to a sensor.

## Sign in and start

1. Accept the followed person's invitation in the official LibreLinkUp app.
   Enter that follower account's email/password only in the installed Glance
   app. Complete any provider terms/policy steps in the official app.
2. Sign in and explicitly select the intended person. Compare the name and
   source measurement time with LibreLinkUp before starting.
3. Allow Android notifications. Review **Lock-screen values** and battery
   settings, then choose **Start monitoring**. Confirm the ongoing card and
   runtime state; a successful sign-in alone does not prove monitoring started.
4. On Now, compare value, unit, trend and time at the same source timestamp.
   Refresh requests the latest available response; it cannot create a new
   sensor sample. See [troubleshooting](troubleshooting.md) if data is old.

## Settings map

| Section | Controls |
|---|---|
| Reading | Follower nickname, Units, Refresh cadence (every minute / every 5 minutes) |
| Appearance | Accent color: Lilac, Teal, Ocean, Berry, Forest, Coral, Sunset |
| Notification | Lock-screen values, Presentation (Live Update / Graph), Live Update system settings when selected, Glucose number icon, Keep notification visible, Show notification, Notification settings |
| Monitoring | Resume after restart, Battery settings, Stop monitoring while enabled |
| Alerts | Low, Urgent Low, High, Urgent High; thresholds, repeat cooldown and Android sound/priority links |
| Devices | Show widget values, Share watch readings, Watch notification |
| Account & support | Change person, Reconnect LibreLinkUp, Diagnostics, Delete local data, Sign out |

A blank nickname restores the provider name. Nicknames change display text;
they do not change the selected connection. Change person clears the previous
person's reading/history before loading the new selection.

## Reading and history

Age comes from the measurement timestamp: under five minutes is fresh, five to
under fifteen is delayed, fifteen or more is stale. An invalid/excessively
future time is not fresh. Successful duplicate HTTP responses never reset age.
The current card shows source time and computed age; do not infer freshness
from the accent, a successful request, or an OS promotion badge.

The graph keeps up to 24 hours and 1,500 validated numeric observations.
Choose 3/6/12/24 hours (6 by default). Hold/drag selects a genuine observation;
release/cancel hides inspection. Multi-finger gestures do not zoom or pan.
Screen readers have Latest/Earlier/Later/Dismiss reading actions. The vertical
scale stays 40–350 mg/dL, converted for mmol/L; extreme readings retain their
actual values in inspection. Gaps over 20 minutes are not joined.

Enabled ordinary Low/High settings supply chart limits; a disabled side uses
70/240 mg/dL. Urgent limits do not replace them. Fresh below-Low is red,
above-High yellow, otherwise green, with red priority for overlapping effective
limits. Changing display limits does not fabricate a reading or enable an alert.

Every distinct received valid numeric timestamp can be retained within the
age/count bounds, including equal values. Same-time source corrections replace
that sample. The provider's historical window may be incomplete; missing
measurements are not reconstructed. See [history evidence](reading-card-030-validation.md).

## Notification and privacy

Routine updates are silent. **Presentation → Graph** uses the regular collapsible
card and available three-hour source-history thumbnail. **Live Update** requests
Android's promoted presentation where permitted; Android controls the layout,
status chip and promotion, and Samsung controls Now bar admission.
The number icon is eligible only for fresh numeric values with privacy enabled.
**Keep notification visible** defaults on: a delivered current-session dismissal
callback restores the regular card. Turn it off for manual restoration with
**Show notification**. Stop/sign-out remove the card. OS blocking or a missing
dismissal callback can still prevent restoration.

**Lock-screen values** starts off. Hidden mode supplies a redacted public
notification and also suppresses the original numeric icon, graph thumbnail and
chip value, including while unlocked; the ordinary unlocked text still shows
the reading. The first upgrade from the legacy pre-0.18 grant needs a new opt-in;
later upgrades retain successfully saved choices.

**Show widget values** and **Share watch readings** start off and are separate
privacy grants. Their surfaces can reveal health data while the phone is locked;
watch complications may be visible in ambient mode. See [widget](car-widget.md)
and [watch](watch.md) setup before opting in. If a privacy save reports failure,
follow its device-controls instruction; do not assume revocation survived restart.

## Alerts, Logbook and background settings

All four threshold alerts start off. Edit a category's threshold wheel and repeat
slider, and use its Android sound/priority link. Android notification permission,
channel settings, Do Not Disturb, source delay and polling opportunity affect
delivery. See [alert behavior](alerts.md). Logbook retains posted categories and
source/posting times; Clear removes that log without resetting cooldowns/rules.
Sign-out and Delete local data are separate confirmed account/data actions.

Battery settings links open Android controls. For the pending Samsung cadence
check, inspect Glance's Unrestricted battery option and sleeping/deep-sleeping
app restrictions where offered. Those menu names vary by One UI. No overnight
or battery qualification is implied by changing a setting. **Resume after restart**
attempts restart after first unlock when Android permits it. After system Stop or
force-stop, open Glance and explicitly start monitoring.

The owner reported Galaxy S25 Ultra / Android 16 / One UI 8.5; that report is
not a completed test matrix. [Device validation](device-validation.md) records
what remains to verify. Glance's optional alerts do not replace manufacturer
alerts or establish treatment suitability.
