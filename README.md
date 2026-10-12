# Glance

Glance is a personal Android app that reads one selected person's shared CGM
readings from LibreLinkUp and displays them in an ongoing notification. The phone
connects directly to LibreLinkUp; there is no Glance backend or analytics.
An optional Wear OS companion receives readings from the phone.

Current app version: **0.30.0**, versionCode **35**, package
`com.khaleel.glance`. The Studio launch configuration fix is included on `main`.
The signed 0.30 APKs were built from `12919d6`; later Studio and documentation
changes do not change the app version or replace those artifacts.

The owner has reported using the app and updating through Studio. Recorded
qualification consists of synthetic tests, bounded emulator checks and static
signed-APK verification. Controlled live-account parity, Samsung/Watch7 behavior,
paired watch transport, overnight cadence and physical battery measurements
remain unverified. See [verification status](docs/device-validation.md).

## Install or update

For an existing **Android Studio installation**, use the same computer and its
existing debug signing key:

```sh
git pull --ff-only
```

Select **Glance phone** beside Run, choose the phone `app` debug variant and
connected device, then Run. If Studio asks for a module, select the phone `app`
module. This configuration opens `com.khaleel.glance.MainActivity` directly,
so a saved Teal or other accent cannot disable the launch target. If the old
`app` configuration still reports `LauncherLilac does not exist`, use
**Run → Edit Configurations → Launch → Specified Activity** and enter that class.
See [Studio recovery and evidence](docs/studio-accent-launch.md).

For a new installation or an existing **personal release installation**, download
the [latest signed phone and watch APKs](apks/README.md), then follow
[build, signing and upgrades](docs/release.md) and [phone setup](docs/setup.md).
A personal release APK cannot update a differently signed Studio build. Cancel
an uninstall prompt and check the signer before proceeding; uninstalling removes
local data.

## Use the app

- **Now** shows the source value/unit, source trend, measurement time and actual
  age. Fresh readings use a green, red or yellow card according to the effective
  Low/High limits; old or invalid readings retain warnings and neutral styling.
- The graph retains up to **24 hours / 1,500 validated numeric samples** for the
  selected person. Choose **3 hr**, **6 hr**, **12 hr** or **24 hr**; 6 hours is
  the default. Hold and drag to inspect real samples; release or cancel to hide
  the tooltip. Multi-finger gestures do not change the window. Accessible
  Latest/Earlier/Later/Dismiss reading actions remain available.
- **Logbook** records threshold notifications accepted by Android, preserving
  the triggering category, reading and source/posting times. It does not prove
  a sound was heard. Clearing the log preserves alert rules and cooldowns.
- **Settings** offers units, nickname, one/five-minute cadence, seven accent
  presets, notification privacy/presentation, optional alerts, device sharing,
  diagnostics and account controls. Stop monitoring is under Monitoring;
  Refresh remains on Now.

Routine updates are silent. Optional threshold alerts start off and use the
existing polling schedule; see [alerts](docs/alerts.md). Notification
**Presentation → Live Update** requests promotion where permitted;
**Graph** uses the regular collapsible card. Android/Samsung control promotion
and Now bar admission. **Keep notification visible** defaults on and restores
the regular card after a delivered dismissal callback while monitoring runs.

Lock-screen values, widget values and watch sharing are separate opt-ins.
Hidden lock-screen values also suppress the numeric status icon, thumbnail and
chip value. Existing successfully saved privacy choices survive normal upgrades;
the first migration from the pre-0.18 privacy store requires a new opt-in.
See [setup and privacy](docs/setup.md), [widget](docs/car-widget.md), and
[watch setup](docs/watch.md).

0.30 accepts minimal historical graph rows, imports up to the shared 1,500-point
bound and preserves genuine provider display precision across full/minimal
same-sample duplicates. Equal values at different timestamps remain distinct.
Glance cannot recover samples LibreLinkUp no longer returns or invent missing
minute readings. See [0.30 verification](docs/reading-card-030-validation.md).

## Build from source

Clone `git@github.com:khaleelclark/glance.git` (or its HTTPS equivalent) and open
the repository root in Android Studio. Repository access is required. Use JDK
17, Android SDK Platform 37, Build-Tools 36.0.0 and Platform-Tools. Set
`ANDROID_HOME` or `local.properties` to your SDK. The wrapper pins Gradle 9.5.0
with SHA-256 verification; the catalog pins AGP 9.3.0 and Compose compiler
plugin 2.2.10. Phone and watch support API 30+ and compile/target API 37.

```sh
./gradlew :app:testDebugUnitTest :wear:testDebugUnitTest \
  :app:lintDebug :wear:lintDebug :app:assembleDebug :wear:assembleDebug
```

Debug outputs are `app/build/outputs/apk/debug/app-debug.apk` and
`wear/build/outputs/apk/debug/wear-debug.apk`. Release signing requires the
existing private key outside Git; see [release guide](docs/release.md).

## Documentation and development

Start with the [documentation index](docs/README.md) for current guides,
architecture, evidence boundaries and historical records. The original
[specification](GLANCE-ANDROID-SPEC.md) retains the baseline and lists subsequent
product changes; [Ponderosa handoff](PONDEROSA-START-HERE.md) explains how to resume
work on this existing repository.

Credentials are entered only in the installed app, encrypted with Android
Keystore and excluded from backup. Keep account data, patient screenshots,
signing keys and passwords out of Git and support logs. The unofficial
LibreLinkUp interface can change. Glance is a follower convenience display;
manufacturer alerts and treatment decisions remain outside its qualification.
