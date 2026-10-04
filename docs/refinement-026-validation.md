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
Native and production-screen results are pending. Scripts changed after the
production candidate; production is pinned to 8cadd4d.

The fixed scheme has unit-verified text/control contrast. Production-screen
checks exercise the timestamp/age above the value once, truthful stale labels,
preset selection without reopening hover, Settings without a color picker,
system light/dark mode and 200% portrait/landscape reachability. Screenshot
inspection separately verifies colors, logo and clipping.

Independent source review, clean release tests/signing, signer continuity,
artifact publication and source push remain pending. The final signed APK is
not claimed to run in the emulator. Synthetic debug evidence cannot qualify
live LibreLinkUp, Samsung One UI, TalkBack, overnight/background, battery,
physical watch or audible-alert delivery.
