# Glance 0.25 review record

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

Two LOW findings were corrected at 6c54812:

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

Pending: independent closure for 6c54812 and the final 30d3f02 axis presentation
correction. Root build/native/visual evidence and final artifact verification are
separate from read-only source review.
