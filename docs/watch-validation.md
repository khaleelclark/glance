# Watch verification status and 0.19 evidence

## Current status — 0.30.0

The signed 0.30 watch was built at `12919d6`, with 15 watch unit tests passing,
zero failures/errors/skips, zero debug/release lint errors or warnings, the same
personal signer as the phone, and static non-debuggable/debug-component checks.
See [release identity](release.md). This is build/static evidence; the 0.19 native
receipts below are retained at their original boundaries, not rerun on 0.30.
Physical Watch7 pairing/Data Layer delivery, complication host/face expiry,
ambient/off-wrist behavior, disconnected timing and battery remain unverified.
Current install instructions are in [watch setup](watch.md).

## Historical 0.19 integration record

Historical evidence for the version/source identified below. Commands, UI names
and results describe that checkpoint; use the [documentation index](README.md)
for current setup and verification status. Later changes do not extend these receipts.

Owner target: Galaxy Watch7 / One UI Watch 8.0 / Wear OS 6.
Synthetic emulator results do not establish paired transport or Samsung behavior.
The final signed manifests and clean build log identify the exact production source
and final unit/lint results; the table records the evidence available at integration.

| Check | Result / evidence |
|---|---|
| Phone projection and sharing-store tests | 151 phone tests pass, zero failures/errors/skips; includes 13 watch projection/grant cases |
| Watch cache/format/publication tests | 15 watch tests pass, zero failures/errors/skips |
| Both debug APKs compile | Pass; integrated phone and watch modules |
| Clean signed builds | Both APKs built and verified at `9a4dcd0`; final rebuild after watch contrast correction is recorded by release manifests |
| Debug/release lint | Zero errors in both modules at `9a4dcd0`; watch zero warnings; final manifests report final totals |
| Phone same-key upgrade | API36 emulator: signed 0.18.1 → 0.19.0 install -r and launch pass; first installation time retained; no live account or individual preference-value claim |
| Native Wear OS app | API36 Wear OS6 round emulator: fresh value/unit/source arrow; explicit historical delayed state; lease-expired state without value |
| Revocation and order | Hidden and stopped tombstones clear values/cards; older reading rejected; process recreation preserves tombstone |
| Notifications | One stable native card; watch opt-out, phone disable and denied watch permission remove it; silent/low/local-only settings inspected in source |
| Freshness/timeline model | Measurement delay removes arrow; future timestamp removes value; five-minute transport expiry removes value/card; safe default and bounded timeline checked |
| Complication host selection/render/expiry | Not verified. Provider is present in release manifest; short/long formatter and timelines unit-tested. Emulator face editor did not provide a completed host-render check |
| Phone/watch Data Layer pairing | Not verified. The unpaired Google APIs phone image reported Data Layer unavailable; watch tests inject synthetic snapshots, not real delivery |
| Stop/person/sign-out phone integration | Projection/grant tests and source review; no paired end-to-end device claim |
| Release fixture exclusion | Signed watch manifest contains no DemoFixtureActivity; release phone has no debug fixture activities |
| Independent review | Requested against integrated source; final result is provided separately with the release |
| Galaxy Watch7 face / One UI Watch rendering | Not run; owner device required |
| Off-wrist / ambient / disconnected physical timing | Not run; owner device required |
| Watch overnight / battery impact | Not run; physical measurement required |

`docs/evidence/watch-0.19/native-watch-scenarios.json` contains thirteen native
synthetic scenarios with status/boolean evidence, not patient readings.
`scripts/validate_watch_companion.py` runs these only on a dedicated debug emulator.
Android notification cancellation is asynchronous: the driver records the platform
active list after the Activity settles, separately from the immediate fixture probe.

A screenshot check found black text on a black watch window when Compose lacked a
Surface. The watch root now supplies an explicit dark Surface and readable content
colors. The corrected debug screenshot is included as
`docs/evidence/watch-0.19/watch-app-demo.png`. The final signed rebuild includes
this correction; the earlier signed candidate had not passed visual review.

Complication validity depends on the face honoring its public validity ranges and
timeline. Older faces may use only the safe default status. AndroidX restricts its
persistence-policy setter; no library restriction is suppressed and no face-cache
erasure guarantee is made. Revocation to an offline watch takes time, bounded in
Glance's own presentation by the five-minute lease. See [watch.md](watch.md).
