# Graph gestures in 0.19.2

**Historical evidence.** Current 0.24 uses 3/6/12/24-hour presets only,
no pinch or time pan, yellow High, and graph-aligned source value/time readouts.
See [0.24 validation](refinement-024-validation.md) for current commands and results.

The owner's request changes the main glucose history graph: one finger selects
real observations, two fingers pan horizontally through the retained day, and
pinching changes time density. Notification and Live Update behavior is unchanged.

The graph starts at the latest three hours. Zoom is bounded to one through
24 hours; panning stops at the retained day's endpoints. Pinching anchors time
under the fingers, unless a retention boundary requires clamping. **Show 24
hours** and **Latest 3 hours** reset the view. Touches on the trace and time
labels are consumed so inspecting readings cannot move the scrolling page.
The page can still scroll outside this region. After any two-finger contact,
the remaining finger cannot select readings until all fingers lift.

Viewport state is keyed by selected person, retained during ordinary UI
recomposition, and saved for recreation of that composable. No gesture changes
history retention, source values, timestamps, units, notification content,
polling, rate limits, credentials, or watch sharing. Selection uses only
visible real observations; it never interpolates a glucose value. Existing
20-minute source gaps remain unjoined. Accessibility actions provide earlier/
later readings (revealing an offscreen sample), earlier/later time, and zoom
in/out. Time labels are culled to avoid overlap at the full-day scale.

## Verification

- JVM tests cover selected-person/valid-history filtering, nearest observed
  selection, local clock ticks, zoom anchoring and pan distance, one/day bounds,
  no visible sample, malformed transform input, and DST tick ordering.
- `scripts/validate_chart_gestures.py` runs the debug-only
  `ChartGestureInstrumentation` on a dedicated API 36 emulator. It injects
  actual one/two-pointer Android touchscreen events into the production
  `ReadingHistoryChart` in a vertically scrolling debug fixture. Seven checks
  pass: scrub without viewport/page movement (including vertical movement),
  pinch zoom, two-finger pan, remaining-finger suppression, full-day reset and
  zoom-out limit, one-hour zoom-in limit, and accessibility zoom.
- Boolean evidence: `docs/evidence/api36-chart-0.19.2.json`. Screenshot:
  `docs/evidence/api36-chart-0.19.2-demo.png`. Both use synthetic observations;
  the fixture is visibly labeled DEMO. Its activity/instrumentation exist only
  in the debug source set. The production main screen was also inspected with
  the existing synthetic source.
- Full clean release unit/lint/signing results are recorded in the generated
  ignored release manifests and `release/clean-build.log` at handoff. This
  document alone is not proof of a successful release build.

Reproduce after `./gradlew :app:assembleDebug` and installation on an emulator:

```sh
python3 scripts/validate_chart_gestures.py emulator-5556
```

## Remaining device checks

Galaxy S25 Ultra multi-touch feel, Samsung gesture interactions, TalkBack's
actual gesture mapping, landscape/large-font comfort, live-account parity,
screen-off reliability and power use remain unverified by these focused checks.
The native test uses the production chart in a debug parent, not the full real
account screen. The owner should install the same-key phone upgrade, slide one
finger through readings, pan with two fingers, pinch both directions, and try
both reset buttons. No watch upgrade is needed. Live Update promotion remains
subject to Android/Samsung, as in 0.19.1.
