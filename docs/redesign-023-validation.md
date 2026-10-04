# Glance 0.23.0: focused redesign and alarm log

## Accepted scope

The owner approved ocean blue, charcoal dark surfaces and soft-white light
surfaces, a new native vector G logo, a smaller reading summary, a more prominent
graph and bottom navigation. The navigation destinations are Now, Logbook and
Settings. Existing source reading, chart interaction, notification, widget,
watch, privacy, monitoring and alert controls must remain available.

Multiple-person monitoring, its dashboard, per-person thresholds and multiple
notification summaries are explicitly deferred by the owner. No extra patient
poller, roster format or notification routing belongs in this release.

The Logbook contains locally posted Glance threshold alerts, distinguishing Low,
Urgent Low, High and Urgent High. Entries include the person, source reading and
measurement time, and the time Glance posted the alert. They do not represent
alarms generated independently by the official LibreLinkUp application, and
posting a notification does not prove that Android sounded it.

The log is encrypted in no-backup storage and bounded to 1,000 records and 30
days. Clearing the log must preserve the existing alert fingerprint/cooldown
state. Explicit new sign-in, sign-out and deletion of local health data clear
the log. Default diagnostic exports continue to exclude patient names, values
and alarm records. Custom accent choices are preserved; the default accent is
now ocean blue.

## Validation status

Implementation and validation are in progress. No automated, emulator, signed
APK or physical-device pass is claimed for this revision yet. Planned checks
include alert priority/deduplication and storage limits, real Android encrypted
storage recreation/clearing in an isolated synthetic directory, native screen
navigation and graph interactions, log type/clear confirmation, rejected alert
saves, privacy controls, light/dark, larger fonts and landscape.

The owner reports that updating 0.21.0 through Android Studio on the same
computer succeeded after the release-signed APK could not replace that
installation. Debug and personal release signing keys remain distinct. The
owner's target phone is a Galaxy S25 Ultra with Android 16 / API 36 and One UI
8.5. A successful Studio update is not a phone qualification test for this
redesign. Proton compatibility testing of the 0.22.0 cookie candidate remains
pending.
