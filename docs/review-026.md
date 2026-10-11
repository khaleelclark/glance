# Glance 0.26 independent source review

Historical evidence for the version/source identified below. Commands, UI names
and results describe that checkpoint; use the [documentation index](README.md)
for current setup and verification status. Later changes do not extend these receipts.

The reviewer inspected exact 8cadd4da40e7279f6ecfbb064e76b64f5b38f068
against released 0.25 base 26b1244. All source reads were pinned with git show;
git diff --check passed. The reviewer made no writes and ran no build, test,
lint, emulator/device, signing, credential or push operation.

No HIGH/MEDIUM regression was found. Requested chart behavior is coherent:
there is no idle in-range endpoint circle, actual visible samples are selected
while held, UP/cancel hides tooltip/crosshair and clears parent selection,
and a second pointer dismisses and suppresses the gesture remainder. Explicit
accessible Latest/Earlier/Later/Dismiss actions retain intentional inspection.
Source, viewport, presets, gaps, thresholds, polling and notification data remain
intact. Source time/actual age appears once above the value, with nonfresh
warnings retained. The runtime scheme is fixed black/graphite/lilac and the
theme picker is removed.

One LOW: the application still inherited a platform light launch theme, allowing
a brief light-to-black flash before Compose. Correction ca538e7 assigns a dark
native theme with explicit black window/background/system bars and light system
icons, plus black API 31+ splash with the existing smaller native droplet.
Runtime Compose/inset settings are unchanged. Independent read-only closure of
exact clean ca538e7930817e1168c35bde0d302d646b58f76c accepted the correction,
found no HIGH/MEDIUM regression or new concrete LOW, and closed the launch-window
finding. git diff --check ca538e7^..ca538e7 passed. No HIGH/MEDIUM blocker or open
LOW remains in these bounded 0.26 source reviews. Only documentation/evidence
commits follow this production candidate; source approval is separate from runtime
and signed-release qualification.

Native chart/screen and cold-launch evidence is separately recorded in
refinement-026-validation.md. Final release hashes, signatures and clean-build
results belong to the delivery manifest and verification bundle. Live-account,
Samsung, TalkBack, overnight/battery, physical-watch and audible qualification
remain unavailable; synthetic fixtures cannot qualify them.
