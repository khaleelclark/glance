# Glance

Glance is a native Android app for a LibreLinkUp follower to view one selected person's latest shared glucose reading in an ongoing notification. The app communicates directly from the phone to LibreLinkUp. It has no backend.

**Current status:** The LibreLinkUp adapter, encrypted local state, Compose setup,
foreground monitoring service, and silent notification are integrated. Synthetic
tests and API 36 emulator lifecycle checks pass. Live follower-account and
Galaxy S25 Ultra behavior remain unverified.

Version 0.30.0 refines the top reading card with a richer green in range,
red below Low or yellow above High. Dark
text keeps the value, unit, arrow, source time and change readable. The card has
a slightly smaller 176dp minimum height and a larger 60sp value, timestamp,
unit, arrow, trend and change text; it grows and scrolls
with large text. The smaller droplet logo, black app background and graphite
supporting cards remain. Settings → Appearance → Accent color offers seven
presets for controls and matching in-app, home-screen and launch-splash droplets.
Old readings retain their warnings
and a neutral number. No custom wheel or hex entry is exposed. Logbook uses Libre-style red badges for
Low/Urgent Low and yellow badges for High/Urgent High, preserving the original
alert category and source time. The reading card
starts with **Reading at <source time> · <actual age>**; delayed/stale/time warnings
remain explicit. Hold and drag the graph to inspect a real reading; release or
cancel to dismiss the tooltip and selection. The permanent endpoint dot is gone.
Screen-reader reading actions also offer **Latest reading** and **Dismiss reading**.
Follower nicknames remain under Settings → Reading and carry into notifications.
Glucose alerts now use the glucose number as their status icon. While an alert
remains visible, its icon follows the latest eligible reading quietly; its text
keeps the category and source reading/time that triggered it. Ongoing and enhanced
notifications also retain eligible number icons. Keep notification visible
protects the reading card from Clear all and restores it after individual swipes.

Version 0.30 also fixes historical imports: minimal LibreLinkUp graph rows no
longer need fields belonging to the full current reading, and the import bound
matches the 1,500-point store. Every distinct received source timestamp remains
saved for up to 24 hours, including repeated values. Backfill cannot recover
measurements the source does not return; no intermediate readings are invented.

See [0.30 card and history verification](docs/reading-card-030-validation.md),
[0.29 card verification](docs/reading-card-029-validation.md),
[0.28 verification](docs/refinement-028-validation.md) and
[0.27 notification verification](docs/notification-027-validation.md).

The 0.22.0 temporary, LibreView-only HTTPS cookie handling remains a VPN
compatibility candidate and safe HTTP response details for connection errors.
Its effect on the owner's Proton VPN connection remains unverified. See
[VPN investigation](docs/vpn-compatibility.md).

The main screen includes a 24-hour graph of validated source samples. Touch
to inspect readings. **3 hr**, **6 hr**, **12 hr** and **24 hr** select the latest
time window; 6 hours is the default. Multi-finger gestures do not change it.
The graph has a fixed 40–350 mg/dL scale, red below Low and yellow above High.
Enabled ordinary Low/High alert settings supply the limits; disabled alerts use
70/240 mg/dL. Labels follow the selected unit.
See [gesture verification](docs/chart-gesture-validation.md) and
[limit verification](docs/chart-limits-validation.md). The notification shows the current glucose,
trend arrow, and recent change when available; Refresh remains in the app.
Refresh retries connection failures even while automatic polling is backing off;
provider rate limits still apply. Stop monitoring is available in Settings. The
app keeps black surfaces and the chosen accent in either system display mode.

Optional Low, Urgent Low, High, and Urgent High alerts can be enabled separately
in Settings. Each has a threshold and a repeat cooldown (five minutes by
default). Glance checks newly received readings during its existing monitoring
polls; Android's channel settings control each alert's sound and interruption.

## Open in Android Studio on another machine

1. Clone the private repository: `git clone git@github.com:khaleelclark/glance.git` (or use the GitHub HTTPS clone URL), then open that directory in Android Studio.
2. Install JDK 17 and the Android SDK Platform 37, SDK Build-Tools 36.0.0, and Platform-Tools through Android Studio's SDK Manager. Android Studio can download missing SDK components during sync.
3. Let Gradle sync. The checked-in Gradle wrapper downloads Gradle 9.5.0 and verifies its SHA-256 checksum. The project uses Android Gradle Plugin 9.3.0 and Kotlin Compose compiler plugin 2.2.10.
4. Select the shared `Glance phone` run configuration and an Android 11 or newer device/emulator. It launches `com.khaleel.glance.MainActivity` directly, so changing the accent/icon does not break Studio launch. The app targets API 37 and supports API 30+.

From a terminal with `ANDROID_HOME` or `local.properties` pointing to your SDK:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

If the installed phone app came from Android Studio, pull the current `main` on
the same computer, select `Glance phone` with the `app` debug variant, and Run again. Keep that computer's
existing debug signing key to update without removing settings. The personal
release APK uses a separate key and cannot replace a differently signed debug
installation; cancel any uninstall prompt and inspect the installer error first.

If Studio reports `LauncherLilac does not exist` after selecting another accent,
open **Run → Edit Configurations**, select your phone configuration, set
**Launch → Specified Activity**, and enter `com.khaleel.glance.MainActivity`.
Apply and Run. This also fixes an existing locally saved `app` configuration.
The shared configuration lives in `.run/Glance_phone.run.xml`; after pulling,
select `Glance phone` explicitly because Studio may keep the old selection.
If the imported configuration requests a module, select the phone `app` module.
This launch error does not require uninstalling or clearing the app's data.

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. For a signed
personal APK, key backup, install and upgrade instructions, see
[docs/release.md](docs/release.md). The signing key stays outside this repository.

## Development boundaries

- `GLANCE-ANDROID-SPEC.md` is the product baseline; `PONDEROSA-START-HERE.md` records delivery and verification requirements.
- `docs/architecture.md` records shared types and decisions; `docs/protocol.md` and `docs/background-strategy.md` pin source/platform research.
- `app/src/main/java/com/khaleel/glance/core/Contracts.kt` is the shared model boundary.
- Account credentials are entered only on the phone, encrypted with Android Keystore, and excluded from backup. Never put real account data, signing keys, or patient screenshots in Git.

The LibreLinkUp interface is unofficial. Live account behavior and Samsung background operation require on-device verification before this is considered a qualified release.

On Samsung Android 16, Glance requests a Live Update when permitted. Settings
shows its observed status and links to Android's promotion controls. Also
check Samsung's Now bar and Glance Live notifications settings. The fresh
glucose number can serve as the small icon in both regular and promoted requests. **Keep notification** defaults on:
while monitoring is running, a delivered dismissal callback restores the same
silent regular card after Clear or swipe. Turn it off for manual restoration using **Show notification** in Settings.
Stop and sign-out clear the card. Samsung delivery and Now bar admission still
need a phone check; see [notification validation](docs/notification-0.18-validation.md).
This upgrade starts lock-screen values hidden because its checked privacy store
requires a new explicit opt-in. Re-enable them in Settings if wanted; successful
choices in the new store are retained on later upgrades.

The regular notification follows the owner's reference: trend arrow, lighter
large reading, unit, and a genuine three-hour history thumbnail when available.
Expand for source time, person, trend and connection information. Turn off
**Status chip Live Update** for this layout; Android owns promoted layouts and
Samsung controls the notification shell and icon placement.

The Glance glucose widget adds a large source reading, trend, measurement time,
actual change interval and four-hour history. Enable **Widget values** in Settings,
then add it from the phone's widget picker. It adapts to smaller sizes and both
themes without scrolling. Android Auto use requires Google's car-widget feature
to be available on the phone/car; availability on the target car is unverified.
See [docs/car-widget.md](docs/car-widget.md) for setup and limits.
# Galaxy Watch companion

Glance 0.19.0 adds a Wear OS companion app, a glucose/source-trend text
complication and optional silent watch notification. It uses readings from the
phone; it does not sign in or fetch separately. Watch sharing starts off.
See [watch setup and sideloading](docs/watch.md) and
[watch verification](docs/watch-validation.md).
