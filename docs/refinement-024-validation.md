# Glance 0.24 refinement validation

Status: integrated implementation at 52ff7a3; source review and release
verification remain in progress. Base is clean 0.23 source d5a91c9.

## Scope

The owner requested a Libre-like horizontal graph grid, graph-aligned source
value/time inspection, fresh reading range colors, a simpler logo, a small
header activity dot, removal of Now Pause/pinch zoom, a 3-hour preset and removal
of redundant subtitles/legends. The graph remains source-only with fixed
40–350 mg/dL scale, unit conversion, gap breaks and true out-of-scale markers.
Colors use existing ordinary Low/High limits, with the established 70/240 defaults
for disabled alerts. No new medical target, poller, account API or multi-person
feature is introduced. Continuous monitoring while enabled retains the existing
scheduler; removing Now Pause does not bypass Android/user stops.

The source screenshots contain private health data and are only local design
references. Published evidence must use DEMO fixtures.

## Verification

Root range-presentation tests cover strict limits, configured limits, canonical
units, unavailable/invalid/old readings and source LOW/HIGH special values. The
first root debug pass ran 209 phone tests with zero failures/errors/skips; it
precedes integration of the specialist graph and is not final release evidence.
The integrated debug build at 52ff7a3 passed **208 phone tests**, zero
failures/errors/skips, assembly and debug lint (zero errors, 74 warnings).
The chart implementation was integrated from corrected specialist 9643dc3;
the earlier e17f1f4 handoff was superseded and was not integrated.

The API 36 debug fixture passed **15 native chart checks**: scrub/no parent
scroll, inert pinch/two-finger gestures including finger lift, all four presets,
accessibility reading selection without zoom or hidden viewport movement,
fixed scale, default/configured threshold geometry, actual off-scale values,
mmol conversion and dark trace colors. Its receipt records source 52ff7a3,
dirty=false, APK SHA-256
`115c41759c04167e254abfc3436c9b4e6dac64b57845254ddf030aae2c086a3a`.
The fixture uses the emulator's existing debug signer and is local,
debug-only and never a release deliverable. Final integrated UI checks,
read-only source review and clean release verification are pending.

## Device limits

Live source parity, Samsung behavior, TalkBack, overnight/background reliability,
battery impact, Android Auto, physical watch and audible delivery remain
unqualified. A synthetic emulator screenshot cannot establish those results.
