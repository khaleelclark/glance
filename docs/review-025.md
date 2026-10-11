# Glance 0.25 review record

Historical evidence for the version/source identified below. Commands, UI names
and results describe that checkpoint; use the [documentation index](README.md)
for current setup and verification status. Later changes do not extend these receipts.

The independent reviewer inspected exact 79d1006, comparing from 0.24 release
source a9ef2d1. This first bounded review excludes the then-unintegrated chart.
All source reads were pinned; no reviewer edit/build/test/device/sign/push action
was performed. No HIGH/MEDIUM blocker was found.

The nickname projection changes display text only; stable provider identity,
selected connection and reading values remain intact. Production injects the
account-scoped encrypted store. Blank restores the source name, storage failure
retains the prior alias/display, and automatic reauthentication rejects account
identity changes. Main UI, notification, widget and watch consume the same
projected DisplayState. Version, logo, status dot, casing and navigation-bar
changes showed no source blocker in that bounded review.

Two LOW findings received initial corrections at 6c54812:

- Permanent old-blue getter remapping prevented later Ocean selection. A one-time
  preference migration now retains an explicit Ocean selection afterward; the
  Android probe checks migration, explicit selection and store recreation.
- Silent nickname deletion failure on sign-out could leave an encrypted file
  after a UI-confirmed sign-out. The controller now returns cleanup success,
  and MainActivity includes a failed encrypted nickname cleanup in its existing
  residual-data warning while clearing the visible account. A JVM fault-injection
  test covers failure and the signed-out display.

The chart was integrated as c61c4a7 from worker a3d40f5. Root visual inspection of
6c54812 caught a new normal-font 40/50 axis-label overlap. Presentation correction
30d3f02 moves the 40 endpoint into the lower axis row, scales that row with text
size, and extends the Y-axis bounds to contain it. Canvas plot geometry and
source/selection/gesture rules are unchanged.

Independent integrated review of exact 6c54812 found no HIGH/MEDIUM blocker.
The encrypted-nickname cleanup finding was closed. Chart source values,
selection, gestures, 48dp touch targets, and combined value/time tooltip were
accepted within the source-review boundary. The Ocean correction remained LOW:
normal sign-out cleared the migration marker, so a subsequent explicit Ocean
choice could be remigrated after restart.

Independent axis review of exact 30d3f026e615ebbbdac079dae11fdb847b42cedd
closed the 40/50-label overlap in source, finding no HIGH/MEDIUM regression or
new concrete LOW. Scaling the dedicated lower axis area leaves the fixed Canvas
and its source/threshold/selection geometry unchanged. Native/UI evidence is
separate from this bounded source approval.

At 24f6500, UserSettings.clear preserves the completed installation migration
marker while resetting account preferences. The debug Android probe exercises
clear → default teal → explicit Ocean → store recreation. This targets the exact
remaining lifecycle finding. Independent source closure against exact clean
24f650085f062b7db67cc8c4bc174385966b5581 accepted this correction and found no
HIGH/MEDIUM regression or new concrete LOW. The remaining Ocean lifecycle LOW
is closed. The focused native receipt passes all seven checks, including the
new reset lifecycle. No HIGH/MEDIUM blocker or open LOW remains in the bounded
0.25 reviews. Only documentation/evidence commits follow that production source.

Source closure is separate from release qualification. Final clean-build,
signature, APK hash and debug-component evidence is recorded under release/
and in the delivery verification report. Live-account/Samsung, TalkBack,
overnight, battery, physical-watch and audible-alert checks remain unqualified.
