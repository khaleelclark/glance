# Graph limits and colors in 0.19.3

Historical evidence for the version/source identified below. Commands, UI names
and results describe that checkpoint; use the [documentation index](README.md)
for current setup and verification status. Later changes do not extend these receipts.

Current graph behavior is documented in [setup](setup.md): 3/6/12/24-hour
presets, hold/drag inspection with dismissal on release, no pinch/pan, and yellow High.

The owner requested a fixed 40–350 mg/dL vertical scale, a Low line at 70,
and a High line at 240, with the lines following enabled ordinary Low/High
alert settings. Below Low is red; above High is orange. The existing one-finger
selection, two-finger pan and pinch zoom remain unchanged.

## Behavior

`ChartLimits.kt` reads each ordinary alert independently: an enabled Low or High
uses its saved canonical mg/dL threshold, while a disabled one uses 70 or 240.
Urgent Low/Urgent High do not replace those lines. Both portrait and landscape
main-screen callers pass the current `settings.alerts`; the existing settings
callback saves the rule and refreshes the settings snapshot, so returning to
the graph uses that choice without a new poll.

Axis endpoints stay 40 and 350 mg/dL at every time zoom. Threshold lines,
axis labels and legend use the selected unit and the common formatting/conversion
code. The trace is drawn from the same real observations and keeps existing
20-minute gaps. Drawing clips its red/orange parts at the exact canonical limit
height, without adding interpolated source readings. The selected reading's
value/dot uses the same band plus plain-language Below Low/Above High text.
Colors and faint zone shading adapt to light/dark backgrounds.

Source observations outside 40–350 are not rewritten or dropped: small edge
arrows mark their positions, and selection shows the actual value with an
Outside graph scale label. Source timestamps, polling and alerts are unchanged.
Thresholds outside the visible scale retain their exact legend value and an
explanation; no misleading line is clamped to 350. Closely spaced axis labels
are suppressed to avoid overlap, while the legend retains both exact limits.

Rarely, independently disabled limits can overlap (for example enabled Low 260
and disabled High falling back to 240). Both actual effective values remain
visible, an overlap message explains red precedence, and red is drawn last.
Strictly below/above controls chart color: a value exactly on a threshold keeps
the normal dot/value color. This presentation does not modify the alert engine's
existing inclusive trigger policy or turn alerts on.

## Evidence

Eight additional JVM tests cover disabled/custom/independently enabled limits,
urgent isolation, exact-boundary colors, fixed scale/unmodified extreme values,
out-of-scale limits and label collisions, overlapping effective limits, and
mmol conversion. Existing chart gesture/person/DST tests remain in place.

The debug-only gesture instrumentation now includes six new native checks in
addition to its seven gesture checks. All 13 pass in
`docs/evidence/api36-chart-0.19.3.json`. Native checks inspect actual screenshot
pixels for both horizontal limit lines and the red/orange trace regions, confirm
independent enabled limit legends and disabled saved-value defaults, preserve
selected 25/380 mg/dL source values, and verify mmol geometry and dark colors.
These use the production chart in a synthetic scrolling debug fixture. They do
not operate the real Settings controls or contact LibreLinkUp.

Readable synthetic DEMO screenshots:

- `docs/evidence/api36-chart-0.19.3-defaults-demo.png`
- `docs/evidence/api36-chart-0.19.3-custom-dark-demo.png`
- `docs/evidence/api36-chart-0.19.3-mmol-demo.png`

Reproduce with `./gradlew :app:assembleDebug`, install the debug APK on a dedicated
emulator, and run `python3 scripts/validate_chart_gestures.py emulator-5556`.
The final clean release tests/lint/signatures and exact source commit are in
ignored generated release manifests; this document does not itself prove a
successful signed rebuild. Independent review is recorded separately at handoff.

Galaxy S25 Ultra gesture/rendering behavior, actual TalkBack navigation,
large-font/landscape comfort, settings retention during signed upgrade,
live-account parity, screen-off/battery and physical watch behavior remain
unverified. The owner should install the phone upgrade and check the default
lines, then enable/change ordinary Low/High settings and return to the graph.
The watch runtime, standard notification and Live Update presentation are unchanged.
