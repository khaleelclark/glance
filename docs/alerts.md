# Glucose threshold alerts

Glance offers four optional categories: Low, Urgent Low, High, and Urgent High.
They all start disabled. The initial threshold values (70, 55, 240, and 300
mg/dL respectively) are editable examples, not a recommendation for the owner.
Each rule has a one-to-60-minute repeat cooldown, initially five minutes.
Settings → Alerts opens an edit sheet with a scrolling threshold wheel and
a repeat-cooldown slider. The
threshold wheel shows whole mg/dL values or 0.1 mmol/L steps next to the unit;
preferences store the underlying mg/dL boundary so changing display units does
not move an alert. The wheel is constrained by adjacent urgent/ordinary levels.

## Decision and delivery

Alerts use the existing monitoring polls; they never add a network request or
claim to run on an independent deadline. The decision requires monitoring to
be running, a successful poll whose returned reading was published for the
selected person, a fresh numeric source value, and a nonfuture source
measurement timestamp. A repeated response for the same sample cannot alert.
When both categories on one side match, the urgent one wins. If a new sample
still matches the same category, it can alert again only after that category's
cooldown. Crossing from High to Urgent High or Low to Urgent Low can alert
immediately because those categories have separate cooldowns. A new normal
reading clears an old threshold card.

The last processed sample fingerprint and last alert attempt time per category are
persisted. The fingerprint is a SHA-256 digest over identity and source sample
fields; preferences do not store the patient's name or the glucose value in
plain text. The four alert cards use a different ID and channel from the
ongoing silent monitoring card. Only one threshold card remains at a time;
it includes the value, unit, and actual measurement time. If lock-screen
values are hidden, the public version says only that Glance has an alert;
numeric status icons are suppressed too. While visible, an eligible numeric
alert icon may follow a newer source reading without changing its triggering
body/time or logging another event. System-owned expiry bounds those numeric
cards to the latest eligible source freshness deadline; the log remains.
See [notification implementation notes](../app/src/main/java/com/khaleel/glance/notification/README.md).

Each category has its own Android notification channel, registered at app
startup so all four appear in Android's app notification settings before an
alert has fired. Low and High request
default importance; Urgent Low and Urgent High request high importance and an
alarm sound. The app's Settings button opens that category's Android channel
settings so the owner can choose its sound and interruption behavior. The alert
list links directly to each category's settings and to the app's full
notification settings. On Samsung, the category list may be hidden until
**Settings > Notifications > Advanced settings > Manage notification categories
for each app** is enabled. On Android 16, expand **Show unused categories** to
see categories that have not fired yet. Android lets users change channel behavior after
creation; the app cannot enforce it.
Android 13+ also requires the user's notification permission, and Do Not
Disturb can silence channels unless the system/user grants an exception.
Glance does not request Do Not Disturb policy access or a full-screen intent.

## Qualification still required

There is no independent time-sensitive delivery guarantee: Glance depends on
LibreLinkUp publishing a new sample, the phone's polling opportunity,
notification permission, channel settings, and device background policy.
Recorded unit/debug-emulator coverage is described in
[device validation](device-validation.md) and
[0.27 icon/expiry validation](notification-027-validation.md).
Galaxy S25 Ultra checks remain necessary for category sound, Do Not Disturb,
lock-screen presentation, process recovery and delayed/offline live source data;
synthetic notification posting does not qualify audible delivery. A live follower account
must confirm that the source values and timestamps match. This feature is a
convenience display and does not replace the CGM manufacturer's own alerts.

For a visual and sound check on a **debug** emulator build, after granting
notification permission, target that emulator explicitly (example serial below):

```sh
adb -s emulator-5556 shell am start -n com.khaleel.glance/.debug.DebugNotificationActivity --es mode alert-demo --es kind URGENT_HIGH --ei value 305
adb -s emulator-5556 shell am start -n com.khaleel.glance/.debug.DebugNotificationActivity --es mode alert-clear
```

The debug fixture is labeled `DEMO` and does not test LibreLinkUp, the
threshold engine, or cooldown. Those cases need the unit tests and an
on-device monitored session.

Android references: [notification channels](https://developer.android.com/develop/ui/views/notifications/channels),
[runtime notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission),
and [NotificationChannel API](https://developer.android.com/reference/android/app/NotificationChannel).
