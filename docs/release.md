# Personal APK build and installation

Glance uses application ID `com.khaleel.glance`, versionCode `9`, and versionName
`0.9.0`. The Android Studio project is the root of this repository. The private
GitHub repository is `khaleelclark/glance`.

## Current qualification

The APK is an **early personal test build**. Synthetic protocol tests, local
state tests, lint, and the API 36 emulator checks in
[device-validation.md](device-validation.md) pass. A real LibreLinkUp follower
account, Patrick's same-timestamp values, the Galaxy S25 Ultra, Samsung One UI,
screen-off cadence, and battery impact have **not** been tested. Do not use this
build as a medical alert or replace LibreLink's own warnings.

## Build from a clean checkout

Clone the repository and open its root in Android Studio. Use JDK 17, Android
SDK Platform 37, SDK Build-Tools 36.0.0, and an SDK path in `ANDROID_HOME`
or `local.properties`. Run:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease
```

This produces `app/build/outputs/apk/release/app-release-unsigned.apk`.
Android Studio can run the debug variant without the personal signing key.

## Keep the upgrade key

The personal release keystore and its password file live outside the repository
at `~/.local/share/glance/personal-release.jks` and
`~/.local/share/glance/signing-password.txt` on the build host. They are mode
0600. **Back up both files to a secure location you control before installing
the release APK.** The key was created on 2026-09-27 and expires in 2054.
Losing the key or password prevents normal in-place upgrades. Neither file
belongs in Git, an APK, a screenshot, or a support log.

On another build machine, copy both files securely to the same directory and
keep them mode 0600. Alternatively, point `GLANCE_SIGNING_DIR` at the directory
containing the two files. From a clean checkout, run:

```sh
python3 scripts/sign_release.py
```

The script refuses dirty source, cleans and rebuilds the APK, runs unit tests
and both lint variants, signs with the existing private key, verifies the APK
signature, and writes the signed APK and metadata under the ignored
`release/` directory. The exact APK SHA-256, certificate SHA-256, source commit,
and verification status are in `release/manifest.json`. Keep the same
application ID and signer for every upgrade; increment versionCode.

## Install and verify on the phone

1. Transfer `release/glance-0.9.0-personal.apk` to the phone and verify its
   SHA-256 against `release/manifest.json`.
2. Open the APK on the phone. If Android asks, permit installation from that
   trusted file manager. Do not disable broader device protections.
3. Sign in **on the phone** with the LibreLinkUp follower account, select
   Patrick explicitly, and compare the reading and timestamp with LibreLinkUp.
4. Allow notifications, choose whether the lock screen may reveal the value,
   review battery settings, then tap **Start monitoring**.
5. Confirm one silent card appears with the measurement time, glucose value,
   trend arrow, and recent change when an earlier reading is available. Glance
   requests the status chip Live Update by default where Android supports it;
   promoted cards stay expanded. Turn that switch off in Settings for the large,
   collapsible card. The main screen has a 24-hour timeline of validated source
   samples: drag across the graph to inspect exact readings; swipe the time
   labels sideways to browse the full day. Landscape places the graph beside
   the current reading. Use Refresh in the app, and Stop
   monitoring in Settings to end the session. Test screen-off
   behavior before relying on the chosen polling interval.

To upgrade, build a higher versionCode APK signed with the same keystore and
install it over the existing app. Verify account/settings retention. An APK
signed with another key cannot replace the installed app without uninstalling,
which removes local Glance data.

## Recovery

Use Refresh and the in-app Diagnostics view first. If a reading is old,
check its measurement time, connection state, notification permission, battery
limits, and next retry time. If terms or policy acceptance is required, complete
that in the official LibreLinkUp app, then reconnect in Glance. After a
force-stop or Android Task Manager Stop, open Glance and explicitly resume.
Reinstall only after checking the state and preserving the signing key.
