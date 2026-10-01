# Glance

Glance is a native Android app for a LibreLinkUp follower to view one selected person's latest shared glucose reading in an ongoing notification. The app communicates directly from the phone to LibreLinkUp. It has no backend.

**Current status:** The LibreLinkUp adapter, encrypted local state, Compose setup,
foreground monitoring service, and silent notification are integrated. Synthetic
tests and API 36 emulator lifecycle checks pass. Live follower-account and
Galaxy S25 Ultra behavior remain unverified.

The main screen includes a 24-hour graph of validated source samples. Drag
across it to inspect readings. The notification shows the current glucose,
trend arrow, and recent change when available; Refresh remains in the app.
Stop monitoring and a choice of app colors are available in Settings. Custom
colors can be chosen on a color wheel with a brightness slider or entered as
six-digit hex values.

Optional Low, Urgent Low, High, and Urgent High alerts can be enabled separately
in Settings. Each has a threshold and a repeat cooldown (five minutes by
default). Glance checks newly received readings during its existing monitoring
polls; Android's channel settings control each alert's sound and interruption.

## Open in Android Studio on another machine

1. Clone the private repository: `git clone git@github.com:khaleelclark/glance.git` (or use the GitHub HTTPS clone URL), then open that directory in Android Studio.
2. Install JDK 17 and the Android SDK Platform 37, SDK Build-Tools 36.0.0, and Platform-Tools through Android Studio's SDK Manager. Android Studio can download missing SDK components during sync.
3. Let Gradle sync. The checked-in Gradle wrapper downloads Gradle 9.5.0 and verifies its SHA-256 checksum. The project uses Android Gradle Plugin 9.3.0 and Kotlin Compose compiler plugin 2.2.10.
4. Select the `app` run configuration and an Android 11 or newer device/emulator. The app targets API 37 and supports API 30+.

From a terminal with `ANDROID_HOME` or `local.properties` pointing to your SDK:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

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
glucose number can serve as the small icon in regular mode; promoted chips use
the Glance icon beside the value. **Keep monitoring card visible** defaults on:
while monitoring is running, a delivered dismissal callback restores the same
silent regular card after Clear or swipe. Turn it off for manual restoration.
Stop and sign-out clear the card. Samsung delivery and Now bar admission still
need a phone check; see [notification validation](docs/notification-0.18-validation.md).

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
