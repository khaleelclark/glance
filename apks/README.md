# Signed APK downloads

Latest included release: **0.30.0 / code 35**.

- [Phone APK](0.30.0/glance-0.30.0-personal.apk)
- [Watch APK](0.30.0/glance-0.30.0-watch.apk)
- [Checksums](0.30.0/SHA256SUMS)
- [Delivery and signer manifest](0.30.0/glance-0.30.0-delivery-manifest.json)
- [Static verification](0.30.0/glance-0.30.0-static-verification.json)
- [Verification and install bundle](0.30.0/glance-0.30.0-verification-and-install.zip)

These immutable artifacts were built from clean source `12919d6`, not from the
later documentation commit. Subsequent Studio configuration/documentation changes
leave phone/watch production source unchanged. Both signatures and SHA-256 values
were rechecked before inclusion on 12 October 2026. No rebuild or signing occurred.

Use [installation and signing guidance](../docs/release.md). Personal-signed APKs
update only matching-signer installations; an existing Studio build may use a
different key. Keep the installed app and update through the same computer/key.
The watch and phone must have matching certificates for Data Layer sharing.

These artifacts have build/static verification. Controlled live-account, physical
Samsung/watch, overnight cadence and battery qualification remain outstanding;
see [device evidence](../docs/device-validation.md).

For future releases, add a new versioned folder with both APKs, their immutable
manifest, verification report/bundle and checksums. Update these links and the
current guides. Preserve older release bytes and keep all signing keys/passwords
outside Git. Do not copy the whole ignored build-host `release/` directory.
