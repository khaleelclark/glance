# Glance 0.25 polish validation

Implementation in progress on clean 0.24 base a9ef2d1. Nickname backend
integrated as bb5ff96, with teal upgrade migration at 265b228. Initial phone
checks: 214 JVM tests, zero failures/errors/skips; debug assembly successful;
debug lint zero errors. That first invocation overlapped addition of a debug
probe, so it is preliminary evidence rather than final release qualification.
The complete integrated checks will follow the chart handoff. Native preliminary
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
Android migration assertions accompany the corrections. Review closure pending.

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

Pending: corrected native/visual checks at normal and 200% font in both
orientations, read-only review closure, clean release
signing, same-signer checks, APK/manifest publication, and source push.

Live LibreLinkUp, Samsung One UI, TalkBack, overnight/background, battery,
physical watch and audible-alert qualification are not claimed by emulator
checks. No live credentials are needed for the synthetic checks.
