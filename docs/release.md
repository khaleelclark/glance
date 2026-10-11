# Build, signing, installation and upgrades

Current phone/watch version: **0.30.0 / code 35**. Both use application ID
`com.khaleel.glance` on their respective devices and require matching certificates
for Wear Data Layer sharing. Source repository: `khaleelclark/glance`.
Last documentation audit: 11 October 2026. Version changes must update both modules.

## Delivered artifacts and qualification

The signed 0.30 APKs were built from clean source
`12919d633cc6ebc63277f796eff81b8350513bd2`. The subsequent Studio configuration
fix (`57a0902`) and this documentation update require no new APK or signer.

| Artifact | SHA-256 |
|---|---|
| `glance-0.30.0-personal.apk` | `ac7c1f3edafeb9181b358fadc80c7282828f1c84c23b61a1a6792a8609d6c724` |
| `glance-0.30.0-watch.apk` | `f1bfd01fc1a1849d980506a49d586729155acc54fd54382977cf3aa736f85ee2` |

Personal certificate SHA-256:
`837148EAF9B9DEFAB6617ED0EB2F62FA49E04A14F216C4CFD719B8D71778977E`.
This is a public certificate fingerprint, not the private key.

On the build host, artifacts and manifests are under ignored `release/`:
`glance-0.30.0-manifest.json`, `glance-0.30.0-watch-manifest.json`,
`glance-0.30.0-delivery-manifest.json`, `glance-0.30.0-static-verification.json`,
and `glance-0.30.0-verification-and-install.zip`. They are delivery files,
not files downloaded by cloning Git. Keep immutable copies with the APKs;
`manifest.json` and `clean-build.log` are overwritten by later signing runs.
A rebuild from newer source must publish its own checksum/source manifest.

The recorded clean release checks passed 236 phone and 15 watch unit tests,
zero failures/errors/skips, and zero lint errors. Phone lint retained 90 debug
and 57 release warnings; watch lint retained zero warnings. Both signatures,
package/version, non-debuggable status and debug-component exclusion passed
static verification. Signed APK runtime checks are separate from the bounded
debug-emulator receipts. See [device status](device-validation.md),
[0.30 source review](review-030.md), and [watch evidence](watch-validation.md).
Owner reports of phone use do not complete controlled live-account parity,
Samsung/Watch7, overnight/24-hour cadence or battery qualification.

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
