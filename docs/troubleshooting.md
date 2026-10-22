# Troubleshooting

Applies to Glance 0.31.0. Start with **Settings → Account & support → Diagnostics**.
Record app/Android/One UI version, connection/runtime state, reading age, last
attempt/success, retry deadline and displayed HTTP status/response category.
Do not send credentials, patient names, raw HTTP bodies or health screenshots
in support logs. Diagnostics remains local unless you choose to share it.

## Android Studio launch or update fails

If `LauncherLilac does not exist` appears after an accent change, select the
shared **Glance phone** run configuration. For an existing local configuration,
open **Run → Edit Configurations**, choose the phone `app` module, set
**Launch → Specified Activity** to `com.khaleel.glance.MainActivity`, Apply and
Run. Keep the same computer's debug signer. See [launch explanation and checks](studio-accent-launch.md).

If an installer reports an incompatible signer, use the original build
computer/key or the matching personal release. Cancel an uninstall prompt.
A release key cannot replace another debug key. A lower versionCode is a
separate downgrade error; use the intended current or newer same-signer build.
See [signing and upgrades](release.md). Uninstall/data clearing are not normal
recovery steps and erase excluded-from-backup local state.

## Reading is old or graph minutes are missing

Check **Reading at** and the actual age, then connectivity, monitoring state,
battery restrictions and retry time. Refresh may retry a temporary network/server
failure early, with a three-second debounce. Provider 429 cooldowns remain
mandatory, including after restart. Repeated responses for the same measurement
cannot make it fresh or create new history. The graph shows retained real samples;
0.30 fixes minimal-row import and the old 300-row bound but cannot recover rows
LibreLinkUp no longer returns. See [history checks](reading-card-030-validation.md).

For VPN failures, retain the displayed coarse HTTP status/type. Reconnect
LibreLinkUp to exercise a fresh sign-in. Temporary provider cookies do not survive
process restart. The cookie compatibility change has not been qualified on the
owner's Proton setup; see [VPN investigation](vpn-compatibility.md).

Terms/policy required means completing that action in official LibreLinkUp,
then reconnecting in Glance. If no connections are listed, verify the invitation
in LibreLinkUp. A removed selected connection never silently switches person.

## Notification disappears or Live Update is absent

If the glucose card remains but a low/high alert changes the status-bar number
to the Glance droplet, 0.31 changes numeric icons on Android 17+ so Android can
compare equal monitor/alert icon contents when selecting an automatic summary
icon. The app adds no explicit group/summary or third card; independent alert
and monitoring dismissal behavior is retained. The alert text still identifies
its original triggering value/time while its icon follows the latest reading
silently. Privacy, freshness and the numeric-icon preference still apply.
Android/Samsung control the final status-bar layout; physical One UI 9 behavior
is unverified. Earlier Android versions keep their prior icon representation.

Follow-up on 22 October 2026: the owner reports that high alerts now retain the
number, but a low alert still replaces it with the app glyph. This remains open;
the 0.31 change is not a verified fix for every Samsung alert. While reproducing,
export **Account & support → Diagnostics**, including the app version, Android
API, numeric/privacy settings, active alert icon types and automatic-grouping
lines. A screenshot alone does not establish which small icons Android received.
The [23-check synthetic follow-up](evidence/api36-alert-icons-0.27.0-low-follow-up-20261022.json)
passes on API36, including low/urgent-low icon refresh to match the monitoring
icon, unchanged triggering text/time, quiet updates and renewed expiry for a
new sample with the same number. The receipt records the dirty debug-source run
and installed APK hash. This does not reproduce Samsung's status bar or exercise
the production API37 encoded-icon path. No production fix or new release is
claimed from these checks.

The owner's subsequent diagnostics confirm 0.31.0 / API37, fresh monitoring,
numeric icons and lock-screen values enabled, a data-type monitoring icon, and
automatic grouping. The old **Active alert icon types** row includes summaries
that Android places on an alert channel; `2, 3` does not prove two real threshold
alerts or prove that the threshold alert itself has a resource icon. Current
source separates individual alerts from posted summaries and lists each set's
icon types. It also compares icon pixels locally and reports only whether each
set matches the monitoring icon; no pixels, hashes or values are exported.
The summary count is labeled as including system summaries. Neither icon type
alone proves equal current numeric contents or visible pixels. These clearer
diagnostics are source changes, not yet included in the signed 0.31.0 APK.
The [17-check API36 diagnostic follow-up](evidence/api36-notification-compatibility-0.31.0-diagnostics-20261022.json)
passes, including a real automatic resource-icon summary on an alert channel
alongside numeric child icons, separate counts/types, and local matching versus
nonmatching pixel comparisons. Its debug build/lint pass with zero lint errors.
This verifies diagnostic classification, not a fix for the owner's low-alert
status bar; API37/Samsung runtime behavior still needs the explicit retry.

Check Android notification permission and the **Glucose monitoring** channel.
While monitoring is enabled, use **Settings → Notification → Show notification**.
**Keep notification visible** restores a regular card after a delivered dismissal
callback, but cannot override notification blocking or every OEM stop/dismissal.
After system Stop/force-stop, reopen and explicitly start monitoring.

**Keep notification visible** is recovery after dismissal, not a permanent pin.
Glance already sets ongoing and Clear-all protection on its monitoring card.
Android 14+ allows individual swipes of ordinary ongoing notifications while
unlocked; an app cannot guarantee that this card is never clearable or always
first. A promoted Live Update receives more prominent placement, but users can
still dismiss/demote it and the system controls promotion and ordering.

Select **Presentation → Live Update**, inspect its status and system-settings
link. If offered on Samsung, also inspect Glance Live notifications and Now bar
controls. Menu labels/admission vary. A promotion flag does not prove a visible
Now bar card. Choose **Graph** for the regular card/thumbnail. Hidden lock-screen
values deliberately suppress numeric icon/chip/thumbnail; enable that privacy
grant only if the resulting exposure is wanted. See [setup](setup.md).

If status says **Keeping the regular card after dismissal**, Glance intentionally
is not requesting promotion. Choose **Presentation → Graph**, then
**Presentation → Live Update** to explicitly request it again. A restored Graph
card having `Promotion requested: false` / `Platform layout eligible: false`
does not establish that Samsung rejected a Live Update template. Export the
status after the explicit retry to distinguish that case.

The owner's subsequent 22 October screenshots still show the dismissal fallback:
promotion not requested, a custom regular layout, no chip, and no active
alerts/automatic groups. The owner reports trying the Presentation change, so
the cause of the remaining fallback is unresolved; this is not evidence of
Samsung rejecting an eligible request. With **Presentation → Live Update**
selected, **Show notification** is a separate explicit restore action. Inspect
Diagnostics immediately afterward without swiping the card. A low alert is not
needed to check promotion. If the fallback persists, export the selected
Presentation and status together before making further changes.

The [four-check actual Settings UI probe](evidence/api36-live-update-restore-ui-20261022.json)
passes on the dedicated API36 debug emulator: selecting Live Update persists the
choice and clears dismissal; a synthetic current-session delete callback sets
the regular fallback; Graph → Live Update clears it; and Show notification clears
it while Live Update is selected. Promotion is disabled on that emulator, so
these are reset/control checks, not promotion or Samsung/API37 qualification.
The separately built standard request has eligible platform characteristics;
the actual app correctly retains the regular template when promotion is denied.
The receipt pins the dirty source and verified installed debug APK hash. No
production behavior change or new APK release is claimed from this probe.

The next owner capture, after **Show notification** at 11:48 on 22 October,
confirms that the restore succeeded on the reported 0.31.0 / API37 phone:
requested, promoted and platform-eligible are all true; chip text is supplied,
colorization is false, and custom views/groups/alerts are absent. The accompanying
shade still shows a small standard card below other notifications, and the
status bar has no visible prominent chip. This is bounded evidence of framework
promotion, not Samsung Now bar or top-placement qualification. The lock screen
was not supplied. The [redacted owner-observation receipt](evidence/owner-api37-live-update-promotion-20261022.json)
retains booleans and visual observations without health/person content; the
installed APK hash was not collected. The earlier fallback's cause remains
unestablished; a late-callback hypothesis is not a verified owner-device defect.
The owner subsequently confirms that Glance is absent from Samsung's normal
Now bar / Live notifications app list. The optional developer-switch test below
is the next discriminator; it has not been performed or verified for Glance.

For a missing Samsung Live Update, export **Account & support → Diagnostics**
after reproducing it. The report now distinguishes requested promotion, actual
promotion, platform layout eligibility, automatic grouping and icon types,
without exporting the glucose number or group identifiers. Android 16 builds
disagree about colorization requirements; Glance checks the running framework's
eligibility method. An eligible layout still does not guarantee Now bar admission.
Samsung's documented path is **Settings → Lock screen and AOD → Now bar → View
more**; enable Glance if offered. If it is absent, report that together with the
redacted diagnostics and Android/One UI version. Do not assume a menu entry exists.

If Android reports promoted=true but Samsung's presentation is still absent,
first inspect Glance's available **Live notifications** app switch and the
normal Now bar list. An independent BatteryBar developer reports that
**Developer options → Live notifications for all apps** admits their app on
Samsung ([first-hand forum report](https://forum.developer.samsung.com/), topic
“Now Bar: let users manually enable Live Updates from other apps”). This is an
optional diagnostic hypothesis, not an official Samsung requirement or a
verified Glance fix. The switch affects other compatible apps too. If trying
it, change only that switch, explicitly restore Glance, and compare the shade,
status chip and lock screen without a swipe. Keep the before/after observations
separate from the framework promoted flag.

References: [Android Live Update requirements](https://developer.android.com/develop/ui/views/notifications/live-update),
[Android ongoing-notification dismissal](https://developer.android.com/about/versions/14/behavior-changes-all),
[Clear-all flag](https://developer.android.com/reference/android/app/Notification#FLAG_NO_CLEAR),
[Samsung Now bar controls](https://www.samsung.com/us/support/answer/ANS10004605/).

## Watch or widget does not show values

Enable **Show widget values** before adding/resizing **Glance glucose** from the
phone picker. Android Auto hosting requires Google's widget feature on the
actual phone/car; a phone widget alone does not prove support.

For Wear OS, verify paired Play services Data Layer connectivity, matching
package/signing certificates, **Share watch readings**, and running phone
monitoring. **Watch notification** additionally needs watch-local notification
opt-in/permission. Expired projections replace values with status; the watch
cannot fetch independently. A personal watch APK will not share with a phone
signed by another debug key. See [watch setup](watch.md).

## Storage or privacy save fails

Keep the reported error. Failed saves do not establish that a choice survived
process death. If revocation cannot be saved/deleted, use Android's lock-screen
controls, remove placed widgets, or disable/remove the watch display as the app
instructs before restarting. Sign-out may leave encrypted bytes after a storage
failure while hiding account data; another explicit sign-in requires successful
log cleanup. Delete local data attempts each cleanup independently and reports
partial failure. See [alarm-log cleanup](alarm-log.md) before destructive actions.
