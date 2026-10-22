# 0.32 themed Live Update and explicit recovery

The owner enabled Samsung's **Live notifications for all apps** developer option
on the reported Android 17 / One UI 9 phone and confirmed that the Live format
appeared. The supplied crop shows a default-blue pill with the value twice:
once in the numeric small icon, then in short critical text. This is owner
evidence for that device/configuration, not a supported universal enablement
route. No screenshot or health/person content is copied into Git.

The 0.32 request uses the existing monochrome droplet resource icon and supplies
the selected curated accent through `Notification.Builder.setColor`. The chip
has one short-critical-text value. Colorization remains governed by the running
framework's eligibility method; the change does not force it on API37. Samsung
controls final tint, contrast, icon size and placement. No private Samsung API,
allowlist bypass, progress bar, custom Live layout or extra notification is added.

Accent changes immediately render the notification. The final publication gate
checks the captured accent with the other presentation/privacy settings, so a
render built under an earlier accent cannot publish after that choice changes.
Regular Graph mode retains its numeric status icon and independent alarm logic.
Freshness, source identity, private chip text and public redaction are unchanged.

After a swipe, Keep notification visible still restores the regular card. With
Live Update selected, **Restore Live Update** is now visible on Now after a
dismissal and in Notification settings. Restoring is an explicit user action;
there is no automatic re-promotion loop or permanently non-clearable pin.

## Verification

Debug build, 236 phone tests, 15 watch tests and lint (zero errors) pass.
All 20 API36 framework checks pass, including the three appearance checks.
A subsequent alert fixture exceeded Android’s per-package notification enqueue
rate: NotificationService reported shedding a post at 6.84 enqueues/second.
That attempt is not counted as a pass. The debug fixture now paces synthetic
posts and excludes automatic group summaries when selecting the real alarm.
The paced rerun passes all 23 alert checks. Ten retention/privacy checks, including
real monitor shade swipes, also pass. The first recovery-UI attempt saw the
SystemUI shade left open by that fixture; its driver now collapses the shade
before opening MainActivity. That incomplete attempt is not counted as a pass.
The final debug build/lint pass with zero errors. A later compatibility rerun
also hit the platform enqueue limiter (6.19/second); its synthetic posts are now
paced too. The paced compatibility (20), alert (23) and actual recovery UI (4) checks now
pass on the same debug APK, with installed-hash verification in the UI receipt.
The UI driver collapses the previous shade and starts an isolated synthetic
process before testing the new monitoring session, avoiding unfinished prior
fixture teardown. Failed attempts are not counted as passes. Source review and
clean signing are still pending. The legacy retention driver did not capture exact
source/APK identity; that receipt is kept with this provenance limitation, not
reported as exact-source qualification.
A debug-only API36 getter needed an explicit version guard to satisfy lint; the
guarded fixture is included in the final build.
The native compatibility fixture adds three checks: all seven accent colors
plus the logo/single text/actual-framework eligibility; the shared Lilac fallback
for an unknown accent; and a private chip retaining the logo without value text.
The existing number-icon/alert/group/privacy/expiry checks remain in the fixture.
Samsung's new accent rendering, contrast and dismissal recovery are not yet
verified on the updated APK. The previously reported low-alert icon issue also
remains open; no fix is claimed for it.

## References

- [Notification accent color](https://developer.android.com/reference/android/app/Notification.Builder#setColor(int))
- [Live Update requirements and dismissal](https://developer.android.com/develop/ui/views/notifications/live-update)
- [Samsung's supported Now bar controls](https://www.samsung.com/us/support/answer/ANS10004605/)

Current 0.32 native receipts (API36, dirty source at `d7b43ee`; Samsung/API37
and actual promotion remain outside these checks):

- [20 framework checks](evidence/api36-notification-compatibility-0.32.0-paced-reviewed-source-20261022.json)
- [23 alert checks](evidence/api36-alert-icons-0.27.0-appearance-032-final-20261022.json)
- [4 actual restore UI checks](evidence/api36-live-update-recovery-0.32.0-final-20261022.json)
- [10 earlier retention/privacy checks](evidence/api36-notification-0.32.0-retention.json), with the legacy source-identity limitation recorded above.
