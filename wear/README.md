# Glance Wear companion

Current module: 0.30.0 / code 35. For installation, matching phone/watch
certificates and current Settings names, use [watch setup](../docs/watch.md) and
[release guide](../docs/release.md). These implementation notes describe the
passive read-only receiver; physical paired-device qualification is separate.

This module is a read-only Wear OS companion. It receives the credential-free
`WatchSnapshot` DataItem written by the phone, and never contacts LibreLinkUp,
stores credentials, writes Data Layer items, or controls phone monitoring.

## Platform behavior and limits

- The Data Layer is phone/watch synchronization, not a guaranteed real-time
  transport. Disconnected items are buffered, and non-urgent items can be
  delayed. The watch therefore treats every snapshot as expired five minutes
  after `sentAtMillis`, even if a cached item remains available.
- The app and complication refresh their local presentation as time advances;
  they do not start a second network or foreground-service loop.
- The complication uses `UPDATE_PERIOD_SECONDS=0` plus bounded push requests.
  Current-reading pushes are throttled to at most one per five minutes;
  tombstones request a prompt clear. Android's guidance says push requests
  should average no more than one per five minutes, so there is deliberately no
  one-minute complication guarantee.
- Timeline entries expire fresh glucose at the earlier of the five-minute
  measurement boundary and transport lease. A delayed "Last" entry may remain
  only until the transport lease expires; the default after expiry is status
  text, never a bare glucose number. Older watch faces can ignore timeline
  entries and use only the safe default status.
- The native watch notification is silent, stable-ID, permission-gated, and
  requires both the phone snapshot preference and a watch-local opt-in. Its
  timeout is the same bounded presentation expiry. `localOnly` applies only to
  that native watch card; this module does not disable normal phone notification
  bridging or phone alerts.
- `POST_NOTIFICATIONS` is runtime-controlled on API 33+. Notification channel
  settings remain under watch OS/user control. Galaxy Watch7 / One UI Watch 8
  layout, complication scheduling, bridge de-duplication, and permission UX
  require physical-device verification; an emulator cannot establish them.

Primary references:

- https://developer.android.com/training/wearables/data/data-items
- https://developer.android.com/training/wearables/data/events
- https://developer.android.com/training/wearables/complications/exposing-data
- https://developer.android.com/reference/androidx/wear/watchface/complications/datasource/ComplicationDataTimeline
- https://developer.android.com/training/wearables/notifications
- https://developer.android.com/training/wearables/notifications/bridger
