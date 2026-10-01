# Glance 0.18.1 notification retention

Retention base: `3fc28032b180516b711cf1778b4283d701f61026`.
Race-fix base: clean main `a3a8281` (0.18.0).
Privacy-transition fix base: clean main `a27fca0`.
Privacy persistence fix base: clean main `953d939`.
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
| Unit tests | Pass, 138 tests; zero failures/errors/skips | Five controlled publication tests, three retention-storage tests, six lock-screen privacy-storage tests and alert overlap/failure-clear tests. Clean signing re-runs the suite. |
| Cancellation/publication ordering | Pass | Controlled receiver pause after early validation rejects publication after Stop, sign-out and person-change cancellation, including ABA; overlapping notify/cancel leaves the host clear. Final session check rejects stopped/superseded actions. |
| No delayed re-promotion | Pass | Dismissal advances the publication generation, rejecting pending promoted builds after regular restoration. |
| Failed retention save | Pass | Simulated commit failure mutates preference memory but preserves the accepted choice; successful persistence/recreation and later retry are covered. UI reports failure with a Toast. |
| Lock-screen privacy transition ordering | Pass | The choice cannot change after an in-flight final privacy check and before notify. Clearing and changing the preference share the notification publication monitor, invalidate pending public cards and also serialize against alert delivery. Controlled overlap tests cover both card and alert publication. |
| Durable privacy revocation | Pass for simulated storage failures | Synchronous commit is checked. Failed opt-out removes only the dedicated privacy grant and recreation remains hidden; failed opt-in/read hides values. If commit and deletion both fail, the current process stays hidden and the UI reports failure rather than claiming durable success. |
| Legacy grant / actual process recreation | Pass on API 36 | The debug fixture seeds the legacy visible preference, recreates the process, and observes hidden lock-screen values with unit and interval still preserved; [boolean evidence](evidence/api36-notification-0.18-legacy-privacy.json). |
| API 36 foreground card flags | Pass | [Before](evidence/api36-notification-0.18-before.txt) has ongoing, NO_CLEAR and foreground flags; its redacted public version also has NO_CLEAR. |
| Real emulator Clear all | Pass | A disposable shell DEMO notification was removed while the active Glance card remained; [probe](evidence/api36-notification-0.18-clear-all.txt). |
| Real emulator sideways swipe, retention on | Pass | Delete intent delivered; same ID 1001 regular card restored, suppression false, promotion request false. [Probe](evidence/api36-notification-0.18-retained-swipe.txt), [DEMO screenshot](evidence/api36-notification-0.18-retained.png). |
| Real emulator swipe, retention off | Pass | [Probe](evidence/api36-notification-0.18-manual-dismissal.txt) shows card absent and suppression true. |
| Old-generation delete intent | Pass | Explicit debug callback with generation -1 leaves dismissal state unchanged; [probe](evidence/api36-notification-0.18-stale-delete-intent.txt). |
| Stop after restoration | Pass | [Probe](evidence/api36-notification-0.18-stopped.txt) has no posted card after a delete callback carrying the saved pre-Stop generation. |
| Person change / sign-out | Pass | [Person change](evidence/api36-notification-0.18-person-change.txt) and [sign-out](evidence/api36-notification-0.18-sign-out.txt) show no posted card after clearing and delivery of the saved active-session delete intent. |
| Actual privacy replacement, API 36 | Pass | Changing values from visible to hidden through the production method posts a private card with redacted public text and no numeric icon, graph or chip text; [posting facts](evidence/api36-notification-0.18-privacy.json) contain only booleans. |
| Promoted construction / Samsung Now bar | Not qualified | This API 36 image does not allow promotion. The owner's 0.17 promotion report does not establish Samsung chip admission or visibility. |
| Galaxy S25 Ultra Clear/swipe on 0.18 | Not run | Owner must install the same-key upgrade and check both paths. Restore depends on the OEM delivering the delete intent; a brief disappearance can still occur. |
| Real account, overnight and power | Not run for this change | No new network worker, cadence or polling ownership change. Only synthetic data used here. |

Reproduce on a dedicated synthetic-data `Glance_Notification_Test_API36` AVD:
install the debug APK, grant POST_NOTIFICATIONS, then run
`python3 scripts/validate_notification_retention.py emulator-5556`.
The driver refuses physical devices and non-debug apps; it clears its DEMO account
in a finally block. It does not operate on the owner's phone. Startup opens the
main screen and observes the posted card for up to 25 seconds before checking
dismissal, accounting for Android foreground-service presentation deferral. The recorded checks are in
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

The 0.18.1 application uses one process-scoped publication monitor and generation
for final state/privacy validation, notify, and cancellation. Building stays
outside the monitor. Cancellation invalidates pending work before calling the
host; if notify is already in flight, cancellation waits and clears it last.
Dismissal validates the current running action generation again under the same
monitor before changing dismissal state or posting a regular card. All production
notification render/hide paths use this gate. This fixes the review's Stop and
account/person-change restoration race without another scheduler or service.
Lock-screen setting changes also acquire the monitor, cancel the old card before
changing the choice, and reject every earlier build. AlertEngine serializes the
same choice with its delivery and clears existing alert cards. The replacement
monitoring card is then built with the updated privacy setting outside the gate.

Lock-screen values now use the separate `lock_screen_privacy` grant, default off.
The old `settings.lock_visible` value is deliberately never imported: otherwise a
deleted grant could be silently restored from the legacy preference. The first
upgrade to this store therefore hides lock-screen values until a fresh explicit
opt-in; later upgrades retain a successfully saved grant. Unit, monitoring intent,
cooldowns and other preferences are not deleted by the privacy fallback. A total
write-and-delete failure cannot promise durable revocation after process death:
the UI tells the owner to hide Glance lock-screen notifications in Android settings
before restarting. Sign-out still clears account data when revocation fails, and
alerts are cleared even if the privacy update throws. Genuine filesystem failure
on the target phone remains unverified.

Primary references:

- [Android ongoing dismissal and Clear all](https://developer.android.com/about/versions/14/behavior-changes-all#non-dismissable-notifications)
- [Live Update construction, OEM criteria and dismissal](https://developer.android.com/develop/ui/views/notifications/live-update)
- [Samsung Now bar and notification controls](https://www.samsung.com/us/support/answer/ANS10002524/)
