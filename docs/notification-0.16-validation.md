# Glance 0.16.0 reference layout

Base: clean main6608205. Compile/target37, min30. Reference supplied by the
owner shows an arrow before a light-weight reading and unit, graph thumbnail
and numeric status-bar glyph. No other app's image/logo was copied.

Implementation: regular DecoratedCustomViewStyle with separate compact and
expanded views. Healthy fresh compact content is one arrow/value/unit row.
Expanded content preserves selected-person name, absolute source time, source
trend, numeric change with its actual elapsed interval, and connection notes.
Nonfresh/error variants keep explicit labels and last-reading details. The
original public redaction, dismissal generation, silent channel and NO_CLEAR
behavior are preserved. Stored Live Update choices are not reset; switch off
Status chip Live Update for the custom card. Promoted cards retain standard
Android templates and do not include RemoteViews or the graph thumbnail.

The large-icon thumbnail contains only same-person, valid numeric samples
within three hours ending at the current source time. Current corrections
take priority over history at an equal timestamp. It requires two samples;
gaps over15 minutes are not joined. Old/stopped/special/hidden states omit it.
The graph is not displayed if lock-screen values are hidden, even unlocked.
Bitmap memory is128x128 ARGB, plus the96x96 numeric icon when enabled.

Initial API36 Google APIs x86_64 checks: matching170 mg/dL compact row; dark
16.9 mmol/L at1.5 font scale; expanded view with source time/trend/change. AOSP
places the graph on the right; Samsung owns final placement, shell and tint.
Final screenshots, privacy/stale checks, clean tests/lint/signature, same-signer
upgrade and exact independent-review result are in the release manifest and
evidence bundle. Only those final results qualify the delivered candidate.

Debug-only synthetic fixtures carry a visible DEMO label. No real account,
credentials or patient data are used in screenshots/tests. Live account parity,
S25 Ultra appearance/promotion, overnight scheduling and battery remain not run.
