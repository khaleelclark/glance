# Glance 0.15.0 notification checks

Changes based on clean main `fe5d41e`: numeric small icon, API 36.0 promotion
request compatibility, promotion status/settings guidance, and explicit
NO_CLEAR on the ongoing monitoring card. The app remains min API 30, target
and compile API 37. The owner reports Android 16 / One UI 8.5 on the S25 Ultra.

| Check | Result | Evidence / limit |
|---|---|---|
| Unit tests | Pass, 94 tests before clean signing build | Four new tests cover numeric unit formatting, nonfresh/absent/invalid-time fallback, stopped/nonrunning/disabled behavior, special/nonfinite/excessively long values. The clean signing manifest records final results. |
| Debug/release lint | Pass, no errors | Existing warnings remain; intentional use of the inlined promotion extra is narrowly annotated and documented. |
| API 36.0 compatibility helper | Pass for API construction | [Debug-only API probe](evidence/api36-live-api-check-0.15.json): SDK full 3600000; request extra true, critical text 112, custom views absent, no linkage crash. Permission reports false, so no actual promotion is claimed. |
| Numeric small icon | Pass with synthetic data | [305 status bar](evidence/api36-status-icon-0.15-305.png), [305 card](evidence/api36-notification-icon-0.15-305.png), [16.9 mmol/L](evidence/api36-notification-icon-0.15-mmol.png). Android controls tint and final size; Samsung rendering remains unverified. |
| Stale icon | Pass with synthetic data | [Stale card](evidence/api36-notification-icon-0.15-stale.png) and dumpsys show the ordinary resource icon, never the old bare number. |
| Clear all | Pass with synthetic card | A dismissible shell DEMO card was posted, Clear all was tapped, and active notification records showed DEMO removed while Glance remained. [Before](evidence/api36-clearall-0.15-before.png), [after](evidence/api36-clearall-0.15-after.png). This does not make individual swipes impossible on Android 14+. |
| Lock-screen public card | Pass with synthetic data and system sensitive content hidden | [Redacted card](evidence/api36-private-lockscreen-0.15.png) uses the Glance icon and “Reading hidden on lock screen”. Android privacy settings control whether a private or public version appears. Test PIN was removed after verification. |
| Settings shortcut | Checked in debug app | This AVD lacks the dedicated promotion activity; the callback falls back to app notification settings. Samsung menu presence and Now bar eligibility still need device testing. |
| Signed in-place upgrade | Pending until final signing / install | Final result belongs in the release manifest. |
| Galaxy S25 Ultra / One UI 8.5 | Not run | Owner check required after installing the APK. Android 16 alone does not guarantee Samsung promotion. |
| Live account, overnight, power | Not run for this change | No live credentials or patient data were used. Prior device qualification limitations still apply. |

Independent review is tracked separately and must be tied to the candidate
source revision before delivery.
