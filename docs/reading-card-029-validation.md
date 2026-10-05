# Glance 0.29 taller range-filled reading card

Released base: 9c0999a (0.28). Production change: 8ffd2ac.
The owner supplied a screenshot of the top current-reading card and requested
a green/applicable-value-color background plus a larger/taller section.

The existing source/freshness/threshold-derived currentReadingBand gate selects
the same green #8BDC9F, Low red #FF9B9B and High yellow #FFD967, now as the entire
card background. Fresh card text is dark #15151A, with contrast at least 7:1 on
every band; the existing theme contrast test is extended to verify this. A null
band retains graphite and the existing neutral content/secondary text. No source,
unit, trend, delta, warning or timestamp derivation changes. Accent, history
badges, launcher, notification and monitoring logic are unchanged.

The minimum height is 196dp (previous normal content about 128dp), with 24dp
vertical/22dp horizontal padding and 10dp spacing. The number is 56sp/64sp rather
than 50sp/56sp. heightIn is a minimum, preserving growth, wrapping and scrolling
at large fonts in portrait/landscape. Accessible source trend/range descriptions
remain. Both modules increment to code34/version0.29.0; watch UI is unchanged.

The integrated debug build passes 226 phone JVM tests, zero failures/errors/skips,
debug assembly and debug lint with zero errors. All thirteen focused API36 native checks pass; independent source review is tracked. Root owns all implementation/runtime/
release writes. Final clean release manifests record build/signature/source
evidence separately. No live-account/Samsung, TalkBack, overnight/battery,
physical-watch or audible-alert qualification is inferred from this UI change.


`scripts/validate_reading_card.py emulator-5556` passes all thirteen native checks.
Five fresh cases verify exact green/red/yellow backgrounds and dark text pixels
for the value/time/unit/arrow/supporting trend, plus an observed 196dp card height.
Source LOW/HIGH remain categorical. Three old/invalid cases verify graphite,
neutral number, original source time and warning. Missing reading retains the
neutral waiting card. Both system modes agree; choosing Coral leaves the fresh
range card green. At 200% text, portrait and landscape keep every reading field
reachable, permit chart-window selection and Settings access. Root inspected
normal fresh/Low/stale, large-text portrait and both landscape screenshots.
Portrait wraps time and delta without clipping; landscape summary/chart scroll
independently, so portions of the taller summary may require scrolling.

Receipt `api36-reading-card-0.29.0.json` records all booleans, observed heights,
installed fixture APK SHA256 and actual source/dirty state. Its production source
is 8ffd2ac; debug synthetic source modes were committed in c47424e. The final
driver at affcb27 fixes test-only assumptions about wrapped timestamps, which
landscape pane to scroll, and the current Settings section (Units rather than
the old Everyday heading). The complete final profile passes. Production layout
did not change during these driver corrections.

These are debug/synthetic screen results; signed-release/static verification and
source delivery are recorded separately. No Samsung, live account, TalkBack or
physical background/battery qualification is claimed.
