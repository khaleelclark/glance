# Glance 0.18.0 notification retention

Base: clean main `3fc28032b180516b711cf1778b4283d701f61026`.
The exact delivered revision and APK hash belong in the signed release manifest.

The owner reported on the S25 Ultra with Glance 0.17.0 that Diagnostics indicated
promotion, but no visible Now bar/chip appeared, and both Clear and a swipe removed
the card. This is reported phone evidence, not a host-side reproduction.

The new retention switch defaults on for this personal app at the owner's request.
It restores the same silent regular card using the current controller state on a
valid running-session delete intent. Persisted dismissal disables automatic
promotion for this retained card until an explicit restore/start or Live Update
switch action. Turning retention off keeps the original manual-only restoration.
This supersedes the baseline's manual-only dismissal requirement for the owner's
personal build. It does not make OS-controlled notifications impossible to remove.

| Check | Result | Evidence / limitation |
|---|---|---|
| Unit tests | Pass, initial 122 tests; zero failures/errors/skips | Three policy cases cover retained active sessions, optional manual restoration, old generations, disabled monitoring and stopped runtime. Clean signing re-runs the suite. |
| API 36 foreground card flags | Pass | [Before](evidence/api36-notification-0.18-before.txt) has ongoing, NO_CLEAR and foreground flags; its redacted public version also has NO_CLEAR. |
| Real emulator Clear all | Pass | A disposable shell DEMO notification was removed while the active Glance card remained; [probe](evidence/api36-notification-0.18-clear-all.txt). |
| Real emulator sideways swipe, retention on | Pass | Delete intent delivered; same ID 1001 regular card restored, suppression false, promotion request false. [Probe](evidence/api36-notification-0.18-retained-swipe.txt), [DEMO screenshot](evidence/api36-notification-0.18-retained.png). |
| Real emulator swipe, retention off | Pass | [Probe](evidence/api36-notification-0.18-manual-dismissal.txt) shows card absent and suppression true. |
| Old-generation delete intent | Pass | Explicit debug callback with generation -1 leaves dismissal state unchanged; [probe](evidence/api36-notification-0.18-stale-delete-intent.txt). |
| Stop after restoration | Pass | [Probe](evidence/api36-notification-0.18-stopped.txt) has no posted card; later debug dismissal action cannot restart the stopped session. |
| Promoted construction / Samsung Now bar | Not qualified | This API 36 image does not allow promotion. The owner's 0.17 promotion report does not establish Samsung chip admission or visibility. |
| Galaxy S25 Ultra Clear/swipe on 0.18 | Not run | Owner must install the same-key upgrade and check both paths. Restore depends on the OEM delivering the delete intent; a brief disappearance can still occur. |
| Real account, overnight and power | Not run for this change | No new network worker, cadence or polling ownership change. Only synthetic data used here. |

Reproduce on a dedicated synthetic-data `Glance_Notification_Test_API36` AVD:
install the debug APK, grant POST_NOTIFICATIONS, then run
`python3 scripts/validate_notification_retention.py emulator-5556`.
The driver refuses physical devices and non-debug apps; it clears its DEMO account
in a finally block. It does not operate on the owner's phone. Startup opens the
main screen before checking the foreground card to account for platform rendering
and service initialization. The recorded checks are in
[retention.json](evidence/api36-notification-0.18-retention.json).

Diagnostics exports posting facts only, including flags, channel importance,
chip-text presence and layout presence. It does not export glucose values, names,
source times, credentials, or chip contents. Hidden lock-screen values still
suppress the numeric icon and chip value on every surface; the ordinary unlocked
card continues to show the reading. No custom call/media/navigation category,
hidden API, device-admin/accessibility exemption, or second notification is used.

Android/Samsung still own promotion, status-bar/Now bar admission, dismissal,
notification permission and rendering. The retention choice is recovery while a
user-started monitoring session is active, not a claim to override those controls.

Primary references:

- [Android ongoing dismissal and Clear all](https://developer.android.com/about/versions/14/behavior-changes-all#non-dismissable-notifications)
- [Live Update construction, OEM criteria and dismissal](https://developer.android.com/develop/ui/views/notifications/live-update)
- [Samsung Now bar and notification controls](https://www.samsung.com/us/support/answer/ANS10002524/)
