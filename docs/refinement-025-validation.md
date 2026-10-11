# Glance 0.25 polish validation

Historical evidence for the version/source identified below. Commands, UI names
and results describe that checkpoint; use the [documentation index](README.md)
for current setup and verification status. Later changes do not extend these receipts.

Initial implementation phase on clean 0.24 base a9ef2d1. Nickname backend
integrated as bb5ff96, with teal upgrade migration at 265b228. Initial phone
checks: 214 JVM tests, zero failures/errors/skips; debug assembly successful;
debug lint zero errors. That first invocation overlapped addition of a debug
probe, so it is preliminary evidence rather than final release qualification.
The completed integrated checks are recorded later in this report. Native preliminary
nickname checks passed all six assertions in the real Android Keystore store,
including recreation, account isolation, encrypted bytes, Unicode normalization,
blank restoration and accent migration. Light/dark button-navigation screenshots
were inspected: the navigation background matches the app bar and buttons remain
readable. These previews retain the old chart until the chart handoff.
Evidence: docs/evidence/api36-nickname-0.25.0-preliminary.json and the two
api36-ui-0.25.0-preliminary light/dark DEMO images.

Requested changes: sugar/insulin-related logo (native sugar cube selected), new
muted teal default, source timestamp immediately below selected graph value,
matching system navigation background, slightly taller plot, smaller hour
presets, encrypted follower nickname reflected in notification, status dot next
to Refresh, no trend/delta separator dot, and “Just Now” title case.

Chart source integrated as c61c4a7 (worker a3d40f5). It puts both selected-value
and timestamp lines in one top tooltip, adds plot height, and reduces preset
visual height to 36dp while retaining at least 48dp touch targets.

Initial bounded non-chart review at 79d1006 found no HIGH/MEDIUM blocker and two
LOW issues: permanent legacy accent remapping prevented later Ocean selection;
nickname cleanup failure at sign-out was silent. Root corrected both with a
one-time accent migration and propagated nickname cleanup outcome to the
existing sign-out residual-data warning. Focused fault injection and updated
Android migration assertions accompany the corrections. At this checkpoint review closure was pending; final closure is recorded below.

At 6c54812, all 17 native chart checks passed with clean source; the 15-check
synthetic production-screen profile passed with generated evidence/script changes
only. The screen profile includes actual Android Keystore nickname round-trip,
one-time migration and explicit color choice, nickname UI save/header update,
status-dot placement, casing, all presets, scrub semantics, light/dark range
states, stale state, 200% fonts, and landscape reachability. Fixture SHA-256:
ac93f8d15c9f0d03cac03762eb6ae1c998ffa21cbe1d4b038d9b35fd49c8e57a.

Visual inspection caught overlapping 40/50 axis labels at normal font. The
baseline images deliberately retain that issue. Presentation correction 30d3f02
moves the 40 endpoint into the lower axis area and scales that area's height and
Y-axis bounds with text size. Source, Canvas plot geometry, thresholds and all
selection/gesture logic are unchanged. Corrected candidate: 217 phone JVM tests,
zero failures/errors/skips, debug assembly successful, debug lint zero errors.

Corrected native receipt api36-chart-0.25.0-axis-fix.json passes all 17 checks
at clean cfb0764 (docs-only after production 30d3f02). Corrected fixture SHA-256:
640e98fe43538b3537765e88305128fb0f51cc6dffa43d0ed97b9a13715f77ff. The focused
profile api36-ui-0.25.0-font-fix.json passes three checks at 1466ebf, with generated
evidence only dirty. Root inspected normal-font axis separation and both
portrait/landscape 200% tooltip and preset screenshots: full 113 mg/dL and full
localized measurement date/time are visible together, with reachable presets.
Canvas plot geometry and source rules remain unchanged since review candidate
30d3f02. A later non-chart account-reset correction is recorded below.

The final integrated source review accepted nickname cleanup and chart behavior
but found one remaining LOW: account reset erased the one-time accent migration
marker. Correction 24f6500 preserves that installation marker through clear().
The focused real-Android receipt api36-nickname-0.25.0-account-reset.json passes
all seven isolated synthetic checks, including clear → teal default → explicit
Ocean → UserSettings recreation. Fixture SHA-256:
9e9fc507fca86f884be54e04c7ec0644ed6429eea88771262702748c11f6b9cf.
At run time only review documentation was dirty; production was clean 24f6500.
Independent axis source closure of 30d3f02 found no HIGH/MEDIUM regression or new
LOW and accepted the label separation. Final accent source closure against
exact clean 24f650085f062b7db67cc8c4bc174385966b5581 closes the remaining LOW,
with no HIGH/MEDIUM regression or new concrete LOW. Docs/evidence alone follow
that production candidate.

Final release hashes, clean source commit, clean-build unit/lint totals,
signer continuity and debug/demo exclusion are recorded by scripts/sign_release.py
and release/verify-0.25.0.py under release/. The final delivery verification
report combines that artifact evidence with these bounded emulator/source
reviews. A signed release APK is not claimed to have run in the emulator.

Live LibreLinkUp, Samsung One UI, TalkBack, overnight/background, battery,
physical watch and audible-alert qualification are not claimed by emulator
checks. No live credentials are needed for the synthetic checks.
