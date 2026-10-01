# Phone UI overhaul in 0.21.0

The owner requested a complete visual overhaul with definition/depth, fewer
settings clicks, 6/12-hour chart choices, removal of two-finger scrolling and
a comprehensible replacement for Restore card.

## Product changes

The visual system now uses a pale slate background with white bordered/elevated
panels, a bold reading hero, clearer hierarchy, consistent spacing and defined
controls; dark mode uses layered slate surfaces. Accent presets, custom wheel,
brightness and hex entry remain. Settings uses three pages with direct controls
instead of section accordions and per-setting About expanders.

Everyday contains common display/notification/monitoring preferences; Alerts
contains existing glucose rules/sound actions; More contains appearance, widget,
watch, system and account controls. Privacy consequences stay at their controls.
Show notification moves to notification settings and invokes the same restoration
callback. Sign-out and local deletion retain confirmation.

The chart remains selected-person filtered, observation-only, fixed 40–350 mg/dL
and alert-limit driven with independent 70/240 defaults. It opens at the latest
6 hours; 6/12/24-hour radio choices are below the plot. Pinch remains available
with one-to-24-hour bounds; two-finger translation is ignored. Landscape uses
a compact plot with culling of close axis labels; exact thresholds remain in
the legend, and fixed 40/350 endpoints remain labeled. One finger selects
readings without scrolling the parent. Two-finger gestures do not change the
selection simply on first-down. Accessibility exposes actual visible times and
returns false when zoom/time clamping leaves the viewport unchanged. Coincident
limit lines now paint Low/red last. Special source values never become exact
numeric plot points. Alert wheel changes dispatch immediately so leaving a
page cannot discard a pending debounced save.

Notification renderer, polling, credentials/preferences formats, alert engine,
widgets and watch runtime are unchanged. The phone/watch version bump is
coordinated at code26/name0.21.0; the existing watch needs no update.

## Validation

DebugUiActivity renders production GlanceRoot with visibly DEMO synthetic
readings and counter-only callbacks. It never starts monitoring, contacts an
account, or uses credentials. Native UI checks are run with
`python3 scripts/validate_ui_overhaul.py emulator-5556` after installing the debug
APK. DebugChartActivity and ChartGestureInstrumentation test production pointer
handling, viewport choices, synthetic color geometry and actual out-of-scale
selection via `python3 scripts/validate_chart_gestures.py emulator-5556`.
These activities and instrumentation exist only under app/src/debug.

Evidence belongs in docs/evidence/api36-ui-0.21.0.json and
docs/evidence/api36-chart-0.21.0.json, with DEMO screenshots of main, Settings,
dark, stale, sign-in, large-font and landscape. The final reports record 20 passing UI checks and 16 passing chart checks.
The UI checks cover direct settings and fixed navigation, callback dispatch,
notification styles, alert wheel changes before page exit, color controls,
privacy, destructive cancellation, Back/unit retention, stale/offline/sign-in,
dark mode, 1.5x font and landscape. Native chart checks also cover source
limits/colors and actual out-of-scale selection. Twelve screenshots are
visibly DEMO; image inspection complements node checks. Real TalkBack mapping
and actual settings/credentials persistence are not exercised. Two final short
summary clarifications (number-icon privacy dependency and notification
restoration while monitoring) followed the native run; those callbacks and
behaviors are unchanged. The native run precedes the documentation/evidence
commit and is not an exact-head post-commit run.

Final clean JVM tests, debug/release lint, signed APK hashes/certificate/source
are in ignored release manifests/build log. Signed upgrade acceptance/launch is
recorded separately. Credential/settings retention is not exercised by synthetic
callbacks or a signed install alone. Independent source review is attached to
the release validation archive.

## Device boundaries

Emulator screenshots and node/counter checks do not establish subjective comfort,
real TalkBack, callback failure/persistence behavior, real-account/person changes,
Samsung touch/notification shell, overnight/battery reliability or physical
watch/Data Layer. Install over the current phone app; check main/graph layout,
6/12-hour choices, no two-finger drag, notification preferences, existing saved
settings and your preferred text size.
