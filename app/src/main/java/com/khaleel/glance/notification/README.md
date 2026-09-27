# Notification implementation notes

The standard renderer is the baseline. It uses one stable notification ID, an
`IMPORTANCE_LOW` channel with sound/vibration disabled, platform `BigTextStyle`, and explicit
immutable `PendingIntent`s. Refresh targets `MonitoringService`; that service must
coalesce Refresh through its single-flight/rate-limited poller and re-check the current
monitoring generation before acting. The intents contain no patient or account identifiers.
The prior Pause action is no longer posted. `MonitoringService` still accepts an old
generation-checked Pause intent from a card created before upgrade.

After the standard renderer compiled and its formatting tests passed, the optional enhancement
was added. `enhanced = true` requests promoted-ongoing presentation only on Android API 36 or
newer with the API 36.1 promoted-notification methods available, and only when
`NotificationManager.canPostPromotedNotifications()` is true. Android still decides whether to
promote it. Android's primary documentation (checked
2026-09-27) says an eligible Live Update must use a standard supported style (including
`BigTextStyle`), be ongoing, have a title, avoid custom `RemoteViews`, group-summary and
colorized presentation, use a channel above `IMPORTANCE_MIN`, request the non-runtime
`POST_PROMOTED_NOTIFICATIONS` manifest permission, and request promotion. The API 36
`NotificationManager.canPostPromotedNotifications()` check covers user permission/settings;
`Notification.hasPromotableCharacteristics()` checks notification structure but does not
guarantee promotion. OEMs may impose additional criteria, and users may demote or dismiss the
presentation. Consequently enhanced mode has no separate update or polling loop and always
falls back to this standard notification.

On eligible promoted notifications, `setShortCriticalText` puts a fresh glucose
value in Android's status chip beside the required small icon. A delayed reading
shows `OLD`, a stale reading shows `STALE`, and an invalid time shows `TIME?`;
the chip never presents those historical values as current. The normal card
keeps its measurement time and Refresh action. The public lock-screen
version explicitly supplies empty chip text when values are hidden. Android
can still shorten or omit chip text depending on available space and device
policy, so this presentation requires emulator and phone checks.

The renderer does not claim device or Samsung One UI behavior. The manifest permission and
settings flow are integration-owned and must be coordinated before enabling enhanced mode in
the UI.

Primary references:

- https://developer.android.com/develop/ui/views/notifications/live-update
- https://developer.android.com/reference/android/app/NotificationManager#canPostPromotedNotifications()
- https://developer.android.com/reference/android/app/Notification#hasPromotableCharacteristics()
- https://developer.android.com/privacy-and-security/risks/pending-intent
- https://developer.android.com/about/versions/14/behavior-changes-all
