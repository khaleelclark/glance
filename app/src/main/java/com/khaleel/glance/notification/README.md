# Notification implementation notes

The standard renderer is the baseline. It uses one stable notification ID, an
`IMPORTANCE_LOW` channel with sound/vibration disabled, custom compact and expanded
views, and explicit immutable `PendingIntent`s for opening and dismissal. The
notification no longer posts Refresh or Pause. Refresh remains available in the
app. Old generation-checked service intents remain recognized for cards created
before an upgrade. The intents contain no patient or account identifiers.

Enhanced mode requests promotion on Android 16 (API 36.0) and newer when
`NotificationManager.canPostPromotedNotifications()` allows it. The
`android.requestPromotedOngoing` extra is used instead of the platform builder
setter, which was added in API 36.1. Short critical text is available in API
36.0. The supported standard BigText style avoids custom RemoteViews in
promoted mode. Android and Samsung still decide whether to promote the card.
From 0.32, a promoted request replaces the numeric small icon with the droplet
logo and supplies the selected curated theme accent with `setColor`. The chip
then contains one value. This does not force colorization: actual-framework
eligibility still determines that flag. Samsung may adjust or ignore the tint.
The running framework's `hasPromotableCharacteristics()` decides colorization:
early Android 16 requires it, while newer platform documentation prohibits it.
The default uncolorized template is retained when eligible; colorization is
tried only if needed and retained only if that makes the layout eligible.
This does not bypass permission/OEM controls or prove actual promotion.
Settings reports the effective permission/channel/posting state and links to
`ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` with `EXTRA_APP_PACKAGE`;
missing screens fall back to app notification settings.

Fresh numeric readings use a generated 96×96 alpha-mask small icon in the
selected unit by default. Nonfresh, special, absent, stopped, and unrenderably
long values use the Glance icon. The user can disable numeric icons. Public
versions always retain the ordinary icon. Android controls tint and icon size.
Glucose alerts share this same number policy, including numeric-icon preference,
privacy, source-person identity and freshness. A visible alert's icon follows the
latest reading/unit silently (ONLY_ALERT_ONCE) while preserving its original
category and triggering reading/time. Promoted requests use the logo and separate
chip text instead. Alert delete intents use an opaque per-post token and explicit non-exported
receiver; delayed callbacks cannot clear a later alarm. Missing/dismissed alerts
are not republished by icon refresh. A new process discards an unscoped prior
alarm card on first render, keeping its encrypted history record.

From 0.31, Android 17+ numeric small icons encode the same 96×96 mask as PNG
Icon.TYPE_DATA. `Icon.sameAs` compares encoded bytes, whereas TYPE_BITMAP
compares object identity. Android 17 GroupHelper updates summary attributes on
child changes, allowing a common current number to follow updates. API36's
section-based grouping can retain initial summary attributes, so its production
path keeps the previous bitmap representation. No app-provided group/summary
is added. This removes the proposed parent/child dismissal coupling found in
review; the discarded explicit-group candidate is recorded separately.
Diagnostics exports group-summary counts, automatic-group presence, icon types
and layout eligibility, never glucose content or group/person identifiers.

Ongoing and NO_CLEAR request protection from Clear all on both card variants.
The owner reports Samsung clearing the card anyway. The explicit personal-build
retention choice (default on in 0.18) restores the regular card on a valid current
session delete intent. It checks generation, enabled state and RUNNING runtime.
There is no new scheduler or fetch, and no automatic re-promotion after dismissal.
Turning retention off restores the earlier persistent-suppression behavior.
Stop/force-stop still stop the session; Samsung Now bar admission is unverified.
See docs/notification-0.18-validation.md for actual verification limits.

The standard custom view uses `sans-serif-light` for its large glucose value
and a normal `sans-serif` arrow in compact and expanded layouts. Its system-template fallback also bolds the
fresh reading. Promoted cards use
Android's own template, which removes app-supplied text-size spans; Glance
bolds the fresh reading with a `StyleSpan`, but Android controls its rendered
font size and may vary by device.

On eligible promoted notifications, `setShortCriticalText` puts a fresh glucose
value in Android's status chip beside the required small icon. A delayed reading
shows `OLD`, a stale reading shows `STALE`, and an invalid time shows `TIME?`;
the chip never presents those historical values as current. The normal card
keeps its measurement time and, when a recent previous observation exists, the
change in the selected unit. The public lock-screen
version explicitly supplies empty chip text when values are hidden. Android
can still shorten or omit chip text depending on available space and device
policy, so this presentation requires emulator and phone checks.

The integrated manifest declares promotion permission and Settings exposes
Presentation → Live Update / Graph with the platform status/settings link.
The renderer does not qualify Samsung One UI appearance or Now bar admission.
See the [current setup guide](../../../../../../../../docs/setup.md) and
[documentation index](../../../../../../../../docs/README.md).

Primary references:

- https://developer.android.com/develop/ui/views/notifications/live-update
- https://developer.android.com/reference/android/app/NotificationManager#canPostPromotedNotifications()
- https://developer.android.com/reference/android/app/Notification#hasPromotableCharacteristics()
- https://developer.android.com/privacy-and-security/risks/pending-intent
- https://developer.android.com/about/versions/14/behavior-changes-all

Numeric alarm cards have a system-owned timeout at the current source reading's
five-minute freshness boundary. A new sample renews it even with the same number;
duplicates do not extend it. The card expires if the process dies or no fresh
sample arrives. Its event remains in Alarm Log, and the ongoing card is unaffected.
