# Glance 0.24 refinement validation

Status: integrated implementation and focused font correction at d6db76e;
final source review and clean release verification remain in progress. Base is clean 0.23 source d5a91c9.

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
debug-only and never a release deliverable. The same baseline fixture passed **11 integrated UI checks** for header dot,
Refresh, low/in-range/high and stale presentation, all presets, scrubbing,
Settings/Logbook decluttering, dark states, large-text navigation and landscape.
The UI receipt records source 52ff7a3, dirty=true (work-in-progress documentation,
fixture-script and later font corrections were present while the already-built
52ff7a3 fixture ran). Its APK hash identifies the installed baseline exactly.

Visual inspection caught 200% font clipping in the fixed-width selected
value/unit and timestamp rails; the baseline largest-font screenshot retains
that evidence. Correction d6db76e scales those label widths, the axis rail and
time ticks with font size, and reduces large-font axis label density without
removing grid lines. The focused follow-up passed **two native UI checks** at
200% font in portrait and landscape, retaining current reading, preset selection
and full source semantics. Its receipt records d6db76e, dirty=true (generated
evidence), fixture SHA-256
`82d92e37c4a1c0f9f941004f2b7745550f11f994b192eb5aaedd43ae4d6ef896`.
The corrected portrait and landscape screenshots were inspected for full
value/unit and measurement date/time. Landscape's chart column scrolls
independently to reach its presets; the top and bottom screenshots show this
rather than claiming the entire 200% chart fits simultaneously.

This follow-up is not a full baseline rerun or execution of the final signed
release APK. Final read-only source review and clean release verification are
pending.

## Device limits

Live source parity, Samsung behavior, TalkBack, overnight/background reliability,
battery impact, Android Auto, physical watch and audible delivery remain
unqualified. A synthetic emulator screenshot cannot establish those results.
