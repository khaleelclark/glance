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
to the Glance droplet, update to 0.31. Both cards now share an explicit group;
the existing alert is its summary and supplies the latest eligible numeric icon.
No third permanent card is added. The alert text still identifies the original
triggering value/time; its icon follows the latest reading silently. Privacy,
freshness and the numeric-icon preference still apply. Android/Samsung control
the final status-bar layout; this change has emulator checks, not phone proof.

Check Android notification permission and the **Glucose monitoring** channel.
While monitoring is enabled, use **Settings → Notification → Show notification**.
**Keep notification visible** restores a regular card after a delivered dismissal
callback, but cannot override notification blocking or every OEM stop/dismissal.
After system Stop/force-stop, reopen and explicitly start monitoring.

Select **Presentation → Live Update**, inspect its status and system-settings
link. If offered on Samsung, also inspect Glance Live notifications and Now bar
controls. Menu labels/admission vary. A promotion flag does not prove a visible
Now bar card. Choose **Graph** for the regular card/thumbnail. Hidden lock-screen
values deliberately suppress numeric icon/chip/thumbnail; enable that privacy
grant only if the resulting exposure is wanted. See [setup](setup.md).

For a missing Samsung Live Update, export **Account & support → Diagnostics**
after reproducing it. The report now distinguishes requested promotion, actual
promotion, platform layout eligibility, automatic grouping and icon types,
without exporting the glucose number or group identifiers. Android 16 builds
disagree about colorization requirements; Glance checks the running framework's
eligibility method. An eligible layout still does not guarantee Now bar admission.
Samsung's documented path is **Settings → Lock screen and AOD → Now bar → View
more**; enable Glance if offered. If it is absent, report that together with the
redacted diagnostics and Android/One UI version. Do not assume a menu entry exists.

References: [Android Live Update requirements](https://developer.android.com/develop/ui/views/notifications/live-update),
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
