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

Pending: final phone unit/lint/build, native synthetic graph/UI checks,
visual checks at 200% font in portrait/landscape, read-only review, clean release
signing, same-signer checks, APK/manifest publication, and source push.

Live LibreLinkUp, Samsung One UI, TalkBack, overnight/background, battery,
physical watch and audible-alert qualification are not claimed by emulator
checks. No live credentials are needed for the synthetic checks.
