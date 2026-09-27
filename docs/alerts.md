# Glucose threshold alerts

Glance offers four optional categories: Low, Urgent Low, High, and Urgent High.
They all start disabled. The initial threshold values (70, 55, 240, and 300
mg/dL respectively) are editable examples, not a recommendation for the owner.
Each rule has a one-to-60-minute repeat cooldown, initially five minutes.
The UI displays thresholds in the selected unit, while preferences store the
underlying mg/dL boundary so changing display units does not move an alert.

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
values are hidden, the public version says only that Glance has an alert.

Each category has its own Android notification channel. Low and High request
default importance; Urgent Low and Urgent High request high importance and an
alarm sound. The app's Settings button opens that category's Android channel
settings so the owner can choose its sound and interruption behavior. Android
lets users change channel behavior after creation; the app cannot enforce it.
Android 13+ also requires the user's notification permission, and Do Not
Disturb can silence channels unless the system/user grants an exception.
Glance does not request Do Not Disturb policy access or a full-screen intent.

## Qualification still required

There is no independent time-sensitive delivery guarantee: Glance depends on
LibreLinkUp publishing a new sample, the phone's polling opportunity,
notification permission, channel settings, and device background policy.
The API 36 emulator and Galaxy S25 Ultra need explicit tests for each
threshold, category sound, cooldown, lock-screen privacy, Do Not Disturb,
process recreation, and delayed/offline source data. A live follower account
must confirm that the source values and timestamps match. This feature is a
convenience display and does not replace the CGM manufacturer's own alerts.

For a visual and sound check on a **debug** emulator build, after granting
notification permission, run:

```sh
adb shell am start -n com.khaleel.glance/.debug.DebugNotificationActivity --es mode alert-demo --es kind URGENT_HIGH --ei value 305
adb shell am start -n com.khaleel.glance/.debug.DebugNotificationActivity --es mode alert-clear
```

The debug fixture is labeled `DEMO` and does not test LibreLinkUp, the
threshold engine, or cooldown. Those cases need the unit tests and an
on-device monitored session.

Android references: [notification channels](https://developer.android.com/develop/ui/views/notifications/channels),
[runtime notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission),
and [NotificationChannel API](https://developer.android.com/reference/android/app/NotificationChannel).
