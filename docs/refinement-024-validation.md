# Glance 0.24 refinement validation

Status: integrated implementation and chart review corrections at fc28c35;
final source review is complete with no HIGH/MEDIUM blocker. Base is clean
0.23 source d5a91c9. Clean build/signature/push results are supplied separately
in the generated release manifest and verification report at handoff.

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
release APK. The integrated source review at 52ff7a3 and large-text correction review at
d6db76e found no HIGH/MEDIUM blocker. Two localized LOW findings were corrected
at fc28c35: selected tooltip text now uses the existing range container/
on-container colors (all six light/dark pairs exceed 10:1 by calculation), and
empty presets announce no source readings without a hidden selected value.

The corrected fixture passed **17 native chart checks**, including all 15 prior
checks and two empty-window/wider-preset checks. Its receipt records clean
fc28c35, APK SHA-256
`7734fbcb5b13b8c869dd6be374a8bb406c5f9a339df4171e318796774e77485b`.
Three focused light-mode source-range screenshots verify the final tooltip
containers. The final LOW correction review at clean fc28c35 closes both findings and finds
no HIGH/MEDIUM blocker or new concrete LOW. See [review record](review-024.md).
The repeatable clean release command is `python3 scripts/sign_release.py`;
final phone/watch unit totals, lint, signatures, APK hashes and exact clean source
are recorded in generated release manifests and the artifact verification report.
Source-only approval does not prove that build succeeded; those files and the
handoff must establish it. The signed APK is not launched in an emulator as part
of this source/fixture evidence.

## Device limits

Live source parity, Samsung behavior, TalkBack, overnight/background reliability,
battery impact, Android Auto, physical watch and audible delivery remain
unqualified. A synthetic emulator screenshot cannot establish those results.
