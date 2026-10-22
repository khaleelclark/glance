# Signed APK downloads

Latest included release: **0.32.0 / code 37**.

- [Phone APK](0.32.0/glance-0.32.0-personal.apk)
- [Watch APK](0.32.0/glance-0.32.0-watch.apk)
- [Checksums](0.32.0/SHA256SUMS)
- [Delivery and signer manifest](0.32.0/glance-0.32.0-delivery-manifest.json)
- [Static verification](0.32.0/glance-0.32.0-static-verification.json)
- [Verification and install bundle](0.32.0/glance-0.32.0-verification-and-install.zip)

These immutable APKs were built from clean source `41fa21c`. Subsequent delivery
documentation/artifact inclusion does not change the app source. Both signatures
retain the personal release certificate. The previous [0.31 files](0.31.0/glance-0.31.0-delivery-manifest.json) and
[0.30 files](0.30.0/glance-0.30.0-delivery-manifest.json) remain immutable.

Use [installation and signing guidance](../docs/release.md). Personal-signed APKs
update matching-signer installations. Keep an existing Studio installation and
update through its original computer/key; a personal-signed APK cannot replace
a differently signed Studio build. Watch/phone certificates must match for
Data Layer sharing. For a phone installed from the personal APK, send the
matching signed watch APK directly to the watch using [ADB over Wi-Fi](../docs/watch.md).

Build/static checks and bounded API36 synthetic tests pass. Physical Android 17 /
One UI 9 grouping, Samsung Now bar, actual promotion, paired watch, live-source,
overnight cadence and battery qualification remain unverified. See
[0.32 verification](../docs/notification-032-validation.md).

For future releases, add a new versioned folder with both APKs, immutable
manifests, verification report/bundle and checksums. Preserve older release
bytes and keep all signing keys/passwords outside Git.
