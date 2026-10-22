# Build, signing, installation and upgrades

Current phone/watch version: **0.32.0 / code 37**. Both use application ID
`com.khaleel.glance` on their respective devices and require matching certificates
for Wear Data Layer sharing. Source repository: `khaleelclark/glance`.
Release update: 22 October 2026. Version changes must update both modules.

## Delivered 0.32 artifacts and qualification

The phone/watch 0.32 APKs are signed from clean source
`41fa21cc0d8c5c2419db7e963e3fb497a22a512a`, with the existing personal signer.
236 phone and 15 watch tests pass; all four lints have zero errors (phone 96
debug / 58 release warnings; watch zero). Static package/version, signature
continuity and debug-component exclusion checks pass. The 47 current API36
checks (20 framework, 23 alert, 4 restore UI) are separately pinned debug
evidence, not runtime qualification of the signed APK or Samsung tint.

The [independent review](review-032.md) has no production-source findings; its
one documentation LOW is closed by the final build receipt. Updated Samsung
tint/contrast/recovery, physical/API37 runtime and the reported low-alert icon
remain unverified. No supported bypass for Samsung’s app admission is claimed.

- [Signed phone APK](../apks/0.32.0/glance-0.32.0-personal.apk)
- [Signed watch APK](../apks/0.32.0/glance-0.32.0-watch.apk)
- [Checksums](../apks/0.32.0/SHA256SUMS)
- [Delivery and signer manifest](../apks/0.32.0/glance-0.32.0-delivery-manifest.json)
- [Static verification](../apks/0.32.0/glance-0.32.0-static-verification.json)
- [Verification/install bundle](../apks/0.32.0/glance-0.32.0-verification-and-install.zip)

See [0.32 checks](notification-032-validation.md). Later documentation/artifact
commits do not change the signed app source. The immutable previously delivered
0.31 artifacts and evidence follow.

## Previous 0.31 artifacts and qualification

The 0.31 signed phone/watch APKs, immutable manifests, checksums and verification
bundle are under `apks/0.31.0/`. Use the [APK download index](../apks/README.md):

- [Phone APK](../apks/0.31.0/glance-0.31.0-personal.apk)
- [Watch APK](../apks/0.31.0/glance-0.31.0-watch.apk)
- [Checksums](../apks/0.31.0/SHA256SUMS)
- [Delivery and signer manifest](../apks/0.31.0/glance-0.31.0-delivery-manifest.json)
- [Static verification](../apks/0.31.0/glance-0.31.0-static-verification.json)
- [Verification and install bundle](../apks/0.31.0/glance-0.31.0-verification-and-install.zip)

Both APKs were signed from clean source
`4be0762d46442628009c78ac8b7b4b6343c8286b`. Later delivery documentation and
artifact inclusion do not change the APK source or require rebuilding them.
Their production source is identical to the revised review pin `db8eb4e`.
See [0.31 verification](notification-031-validation.md) and [source review](review-031.md).

| Artifact | SHA-256 |
|---|---|
| `glance-0.31.0-personal.apk` | `4190c66727cd3bff521c19d3df64859198f571bbe0507cd7d97f8d7e2e2721b0` |
| `glance-0.31.0-watch.apk` | `18a96e06c8667018a74aa3e46c6abb986760ffe1ae76355b037a968562c75245` |

Existing personal certificate SHA-256:
`837148EAF9B9DEFAB6617ED0EB2F62FA49E04A14F216C4CFD719B8D71778977E`.
This public fingerprint is retained from 0.30; it is not the private key.
The previous 0.30 files remain immutable under `apks/0.30.0/`.

Clean release checks pass 236 phone and 15 watch tests, zero failures/errors/skips,
and zero errors in both modules' debug/release lints. Phone lint has 93 debug /
57 release warnings; watch has none. Both signed APKs verify package/version,
matching existing signer, non-debuggable status and absence of debug components
and fixture markers. This is static signed-APK verification, not runtime proof.

Forty-four bounded API36 debug/synthetic checks cover encoded icon delivery and
initial grouping, independent cards, latest-number refresh, source-age expiry,
privacy, Clear all, real monitor swipes with retention on/off and lifecycle guards.
Each receipt retains its actual source, dirty state and fixture hash. The two
Android 17 dynamic-summary assertions are not run on API36. Current Android 17
framework source was inspected/pinned, but physical Android 17 / One UI 9,
Samsung Now bar, actual promotion, audible/DND delivery, live-source parity,
paired Watch7, overnight/24-hour cadence and battery qualification remain unverified.

Build-host files remain under ignored `release/`; the same intended files are
copied to versioned `apks/0.31.0/`. Keep immutable manifests with the APKs.
`manifest.json` and `clean-build.log` are overwritten by future signing runs;
a rebuild must publish a new checksum/source manifest. The verification bundle
contains reports/guides and public signature evidence; APKs are separate downloads.

## Clean-checkout build

Open the repository root in Android Studio. Use JDK 17, SDK Platform 37,
Build-Tools 36.0.0 and Platform-Tools. The checked-in wrapper pins Gradle 9.5.0
and its distribution checksum; `gradle/libs.versions.toml` pins AGP 9.3.0 and
Compose compiler plugin 2.2.10. Both modules have minSdk 30 and targetSdk 37.
Point `ANDROID_HOME` or `local.properties` at the installed SDK.

```sh
./gradlew :app:testDebugUnitTest :wear:testDebugUnitTest \
  :app:lintDebug :app:lintRelease :wear:lintDebug :wear:lintRelease \
  :app:assembleRelease :wear:assembleRelease
```

Unsigned outputs are `app/build/outputs/apk/release/app-release-unsigned.apk`
and `wear/build/outputs/apk/release/wear-release-unsigned.apk`. They cannot be
installed until signed. Studio's debug variant needs no personal release key.
Use an Android Studio version compatible with the pinned AGP; do not change
catalog/toolchain versions merely to work around a local sync failure.

## Existing Studio installation

Use the same computer and retain its debug signing key. In the existing checkout,
run `git pull --ff-only`, sync, select **Glance phone**, the phone `app` debug
variant and connected phone, then Run. Resolve an imported module to `app` if
needed. The configuration launches `com.khaleel.glance.MainActivity` directly.
For the old `LauncherLilac does not exist` failure, repair the existing
configuration through **Run → Edit Configurations → Launch → Specified Activity**
and enter that class. See [Studio launch fix](studio-accent-launch.md).

A different computer's default debug key may differ. A personal release APK
cannot replace a differently signed debug install. Cancel any uninstall prompt;
inspect the signer/version error before changing the installation. Normal
same-package, same-signer updates preserve data; verify settings and monitoring
after deployment. This uses Android's supported
[run configurations](https://developer.android.com/studio/run/rundebugconfig).

## Protect the release key

The existing personal key and password file are outside Git at
`~/.local/share/glance/personal-release.jks` and
`~/.local/share/glance/signing-password.txt`, mode 0600. Alias:
`glance-personal`. The key was created 27 September 2026 and expires in 2054.
Back up both files securely under the owner's control. Loss prevents future
normal in-place upgrades; generating a replacement key does not repair that.
Neither file belongs in an APK, artifact bundle, screenshot or support log.

On another authorized build machine, securely restore both files to that
location with mode 0600, or set `GLANCE_SIGNING_DIR` to their directory. Set
`ANDROID_HOME` for the signing script; it defaults to `~/Android/Sdk` and does
not discover a custom SDK solely from `local.properties`. Commit all source
and documentation before running:

```sh
python3 scripts/sign_release.py
```

The script requires clean Git state, rebuilds/tests/lints both modules, signs
with the existing key, verifies matching package/version and certificates, and
writes versioned APKs/manifests under `release/`. Its manifests conservatively
leave emulator/live/physical qualification untested for the exact new source;
attach any separately pinned runtime evidence rather than changing that claim
by inference. Static debug/demo exclusion and device qualification require the
release verification step in addition to signature verification.

## Install a personal release

1. Obtain the intended APK and its matching immutable manifest. On Linux/macOS,
   use `sha256sum`/`shasum -a 256` to compare the file to the checksum above or
   that release's manifest. Use `apksigner verify --verbose --print-certs` from
   the SDK Build-Tools to check the certificate when building/verifying locally.
2. For an update, confirm the same package/signer and a non-downgraded version;
   future app releases increment versionCode. Transfer the phone APK and open
   it using a trusted installer. Allow installation from that installer only if
   Android asks. Keep the existing app installed.
3. Follow [phone setup](setup.md), then compare value, trend, unit and source
   time with LibreLinkUp at the same measurement timestamp. Existing account
   and settings normally remain after a same-signer update; confirm them.
4. Install the separate matching watch APK only if wanted, following
   [watch setup](watch.md). A personal-signed watch APK cannot share with a
   phone signed by a different Studio/debug key.

No uninstall, data clearing or forced downgrade is part of the normal update.
Backups exclude account/health state; uninstalling is not a reversible migration.
Keep the original installer and signer available if diagnosing an update.

## Recovery and release history

Use [troubleshooting](troubleshooting.md) for old readings, missing notification,
provider cooldowns, VPN failures and Studio/signing errors. Reopen explicitly
after system Stop/force-stop and start monitoring; Android cannot be promised
to restart a force-stopped app. Use Diagnostics before deleting data.

Historical appearance, defaults, privacy migrations and validation notes are
preserved in [release history](changelog.md). Current controls and setup are in
[setup](setup.md); historical notes are not an installation checklist.
