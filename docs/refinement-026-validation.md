# Glance 0.26 validation

Production candidate 8cadd4d on released 0.25 base 26b1244. The owner requested
endpoint-circle removal, dismissal of graph inspection on release, and moving
source time/actual age above the glucose value. Subsequent steering requested a
smaller droplet, removal of custom themes and a black background with new accents.
Root implemented fixed black/graphite/off-white/lilac and owns all writes.

Initial phone checks: 216 JVM tests, zero failures/errors/skips; debug assembly
successful; debug lint zero errors. A debug-only native fixture checks actual
held touch, lift, cancellation, second-pointer handling, idle marker pixels,
accessible inspection/dismissal, presets, source values and threshold geometry.
All 20 native checks pass at clean 464aa90, with the production tree unchanged
from 8cadd4d. All 10 production-screen checks pass at fdfc16a, with generated
chart/screenshot evidence only dirty. Fixture SHA-256 for both:
92271cd66ad25540ff3775bd6486e03c27530436d8924a56487fbfdcd9cfa34e.
Receipts: docs/evidence/api36-chart-0.26.0.json and api36-ui-0.26.0.json.
The first UI script attempt expected an ordinary space before AM; Android's
localized formatter returns a narrow no-break space. The script now normalizes
whitespace for comparisons. Production formatting is unchanged; the final
10-check rerun passes. Source/script commits retain that correction history.

The fixed scheme has unit-verified text/control contrast. Production-screen
checks exercise the timestamp/age above the value once, truthful stale labels,
preset selection without reopening hover, Settings without a color picker,
system light/dark mode and 200% portrait/landscape reachability. Screenshot
inspection separately verifies colors, logo and clipping. Root inspected the
normal/light-mode preview, portrait/landscape 200% headers and stale screen:
black background, graphite card and smaller lilac droplet are visible; source
time/age wraps fully in landscape, and stale labeling remains explicit. System
mode does not restore a custom theme. The smaller plot's geometry is unchanged.
The native runner checks idle solid-green marker pixels, held selection and
release/cancel dismissal, independently of the synthetic UI callbacks.

Independent source review, clean release tests/signing, signer continuity,
artifact publication and source push remain pending. The final signed APK is
not claimed to run in the emulator. Synthetic debug evidence cannot qualify
live LibreLinkUp, Samsung One UI, TalkBack, overnight/background, battery,
physical watch or audible-alert delivery.
