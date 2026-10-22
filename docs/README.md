# Documentation index

Current guides cover Glance **0.32.0 / code 37**, including the notification
theme/recovery update on **22 October 2026**. Phone/watch source lives in this repository;
signed APKs/manifests are included in versioned `apks/` folders
and delivered artifacts; the APK index identifies the latest included version. Additional build-host outputs remain in ignored
`release/`. See [APK downloads](../apks/README.md).

## Current operating and engineering guides

| Guide | Purpose |
|---|---|
| [setup.md](setup.md) | Phone setup, actual Settings names, privacy and daily use |
| [release.md](release.md) | Current build, artifact identity, signing, Studio updates and sideloading |
| [troubleshooting.md](troubleshooting.md) | Launch/signing, data gaps, notifications, VPN, device sharing and storage errors |
| [architecture.md](architecture.md) | Current contracts, data flow, concurrency, storage and UI |
| [protocol.md](protocol.md) | Implemented unofficial protocol and pinned discovery references |
| [background-strategy.md](background-strategy.md) | Implemented scheduling/recovery and bounded platform evidence |
| [device-validation.md](device-validation.md) | Current verification status plus historical device/scenario ledger |
| [alerts.md](alerts.md) | Optional threshold decision/delivery and sound controls |
| [alarm-log.md](alarm-log.md) | Encrypted Logbook, retention and cleanup failure behavior |
| [car-widget.md](car-widget.md) | Phone widget setup and Android Auto qualification boundary |
| [watch.md](watch.md) | Current watch install, same-signer pairing, grants and lease/expiry |
| [studio-accent-launch.md](studio-accent-launch.md) | Stable Studio activity target and pinned launch evidence |
| [vpn-compatibility.md](vpn-compatibility.md) | Proton/provider-cookie investigation and unverified live compatibility |

[Project overview and build entry point](../README.md),
[Wear module implementation notes](../wear/README.md) and
[notification implementation notes](../app/src/main/java/com/khaleel/glance/notification/README.md)
complete the active guides. [Original specification](../GLANCE-ANDROID-SPEC.md)
now has a current implementation addendum; [Ponderosa handoff](../PONDEROSA-START-HERE.md)
explains how to resume the existing app rather than start it again.

## Evidence and historical records

[Release history](changelog.md) preserves earlier UI/defaults and migration notes.
The records below describe their exact version/source/APK checkpoints. A passing
debug fixture or bounded source review does not prove the final signed artifact,
live account, audible delivery or physical device behavior. Historical reproduction
commands may require their recorded source; drivers in current main can have
changed. Never run synthetic fixtures on the owner's account/device.

- [Graph gestures in 0.19.2](chart-gesture-validation.md)
- [Graph limits and colors in 0.19.3](chart-limits-validation.md)
- [Glance 0.23 visual design](design-023.md)
- [Glance 0.15.0 notification checks](notification-0.15-validation.md)
- [Glance 0.16.0 reference layout](notification-0.16-validation.md)
- [Glance 0.18.1 notification retention](notification-0.18-validation.md)
- [Glance 0.27 glucose status icons and retention](notification-027-validation.md)
- [Glance 0.32 themed Live Update and recovery](notification-032-validation.md)
- [Glance 0.31 notification grouping and platform eligibility](notification-031-validation.md)
- [Glance 0.29 taller range-filled reading card](reading-card-029-validation.md)
- [Glance 0.30 richer green, smaller card and larger text](reading-card-030-validation.md)
- [Glance 0.23.0: focused redesign and alarm log](redesign-023-validation.md)
- [Glance 0.24 refinement validation](refinement-024-validation.md)
- [Glance 0.25 polish validation](refinement-025-validation.md)
- [Glance 0.26 validation](refinement-026-validation.md)
- [Glance 0.28 reading emphasis and accent presets](refinement-028-validation.md)
- [0.19.1 manual refresh after connection failure](refresh-recovery-validation.md)
- [Glance 0.23 independent review record](review-023.md)
- [Glance 0.24 review record](review-024.md)
- [Glance 0.25 review record](review-025.md)
- [Glance 0.26 independent source review](review-026.md)
- [Glance 0.27 independent source review](review-027.md)
- [Glance 0.28 independent source review](review-028.md)
- [Glance 0.30 independent source review](review-030.md)
- [Glance 0.31 independent source review](review-031.md)
- [Phone UI overhaul in 0.21.0](ui-overhaul-validation.md)
- [Phone UI refresh in 0.20.0](ui-refresh-validation.md)
- [Glance 0.19.0 watch verification](watch-validation.md)

Raw receipts and synthetic screenshots are under `evidence/`, linked from each
report. Do not overwrite a prior receipt when collecting a newer run; record the
actual source, dirty state and installed APK hash. Signer/checksum verification
belongs to each immutable release manifest. Current signed 0.31 checks are
summarized in [release](release.md); open physical/account tests are in
[device validation](device-validation.md) and [watch validation](watch-validation.md).

## Documentation maintenance

When changing behavior, update the current guide and architecture/protocol as
needed, both version declarations for an app release, and the release/evidence
status. Preserve historical findings and test limits. Use exact UI labels and
matching signer/package instructions. Check local links and runnable command
syntax; recheck evolving external platform references when changing guidance.
A documentation-only update does not require rebuilding/signing an unchanged APK.

See [11 October documentation audit](documentation-audit.md) for coverage and
checks of this update. This index describes documentation readiness; outstanding
live/physical qualification remains outstanding.
