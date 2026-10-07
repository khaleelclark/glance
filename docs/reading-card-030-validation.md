# Glance 0.30 richer green, smaller card and larger text

Released base: 465d9cd (0.29). Production change: bea5cdf.
The owner requested a less pastel green, a slightly smaller reading section and
slightly larger fonts inside it. The in-range card uses #22C55E, with dark
#15151A text at greater than 7:1 contrast. Low red, High yellow, neutral old-data
fallbacks and the source/freshness/threshold gate are unchanged.

The minimum height decreases from 196dp to 176dp. Horizontal/vertical padding
is 20dp/16dp and spacing is 8dp. The reading is 60sp/66sp; time and warnings are
14sp/20sp, units 18sp/24sp, arrow 36sp/42sp, trend/change 16sp/22sp. Minimum height,
FlowRow wrapping and portrait/landscape scrolling preserve growth at large text.
Phone/watch versionCode is 35, versionName 0.30.0. Watch UI is unchanged.

## History investigation

The owner supplied a screen recording of chart scrubbing and asked why every
minute is not present. Sampled frames show sparse older segments and a denser
recent segment; the recording alone does not establish the phone's actual poll
timings or the samples delivered by LibreLinkUp.

AppController merges each successful snapshot's current reading and graphData
into encrypted per-person history. ReadingHistory deduplicates by source
measurement time, not by glucose value, and retains 24 hours up to 1,500 points.
Equal values at different minutes are preserved. The chart does not decimate
saved numeric points; inspection selects the nearest real source timestamp.
Duplicate responses cannot create another reading at a new minute, and the app
cannot reconstruct samples absent from both polling and source graph history.

The protocol specialist's follow-up established a concrete importer defect,
separate from the successful storage checks: minimal graph rows can omit the
GlucoseUnits/Value fields required by the strict current parser, and released
Glance used that parser for history too. The adapter also truncated valid graph
imports to 300. Corrective production commit 660775f accepts canonical minimal
history with a valid factory timestamp/value, preserves full valid metadata,
leaves missing trends UNKNOWN, keeps current parsing strict, and shares the
1,500-point cap with merge/loading. Same-time minimal backfill preserves an
already captured reported value/unit only if its canonical value is unchanged;
canonical corrections invalidate the older reported value. Source/poll timing
and live payload shape on the owner's phone remain unknown. See protocol.md
for exact primary-source contracts and limitations; no fixed provider cadence
or guaranteed recovery of every missing minute is claimed.

Final history source review of 660775f found no HIGH/MEDIUM regression and one
LOW: a full/minimal duplicate within the first response could discard genuine
provider display precision before reaching storage. Correction 3aefcbd shares
the guarded display-metadata rule with adapter deduplication. Two new tests
verify both duplicate orders retain provider 3.9 mmol/L for canonical 69 mg/dL,
missing trend stays UNKNOWN, and a corrected canonical value clears old display
metadata. All 236 phone tests, debug assembly and zero debug lint errors pass.
The bounded correction review is tracked. Earlier native fixtures retain their
recorded source/hash boundaries; this correction does not change presentation,
chart capacity, storage encoding, polling or source field validation.

Two additional controller regression tests exercise repeated equal readings,
sparse backfill, duplicate source times, controller recreation, and a real hour
gap without invented samples. The corrected candidate passes 236 phone tests,
zero failures/errors/skips, debug assembly and zero debug lint errors. Six added
adapter/merge tests cover minimal and mixed-unit history, a full 1,440-row minute
backfill plus current reading, invalid times/values and duplicates, strict
current-only required fields, reported-value preservation and corrected values.
The isolated Android
storage probe at e297c97 round-trips 1,440 same-value readings at distinct minute
timestamps through the production LocalStateStore and SecureFileStore, reopens
the store, and merges sparse samples without discarding captured readings.
The expanded probe at 6b5fca7 also reopens 1,440 canonical rows with null
reported display values, verifies minimal backfill preserves previously
captured display values, and leaves missing historical trends UNKNOWN.
It verifies encrypted storage and clears its isolated synthetic namespace.
Receipt: evidence/api36-reading-history-storage-0.30.0.json. These checks do not
prove what happened during a specific missing interval on the owner's phone.

At 8f6fb60, a separate native chart profile passes all three checks: all 1,440
minute samples reach the production chart, the independent current reading
remains 113, and the three-hour preset remains actionable. History uses integer
canonical values, null reported display values and UNKNOWN historical trends.
Receipt: evidence/api36-minute-history-chart-0.30.0.json; the installed debug APK
SHA256 is 54c613025199b89d372c229745b49a10699e653937fecbe3c2d0b382b314de86.
Root inspected the screenshot. The first ad hoc driver encountered the restored
emulator notification shade; the final repeatable driver explicitly dismisses
system overlays before launching the fixture. This is synthetic chart capacity
qualification, not evidence of live provider cadence.

## Verification boundaries

All thirteen focused API36 native card checks pass at e297c97, using installed
fixture APK SHA256 486a1c7485de40f112be27085082756238e96c71e3af3d4253fb94bd442de776.
The observed normal card height is 176dp for all five fresh source states. Exact
green/red/yellow background and dark content pixels pass; old/invalid/no-reading
states remain neutral. Both system modes and accent independence pass. At 200%
text, all time/value/unit/arrow/trend/delta fields are reachable, chart presets
remain actionable, and Settings remains accessible in portrait and landscape.
Root visually inspected the fresh card and both large-text orientations; the
landscape summary scrolls independently and lower content remains legible.
The receipt records the complete run, clean fixture source and no reused checks.
The presentation source review of bea5cdf found no HIGH/MEDIUM regression or
concrete LOW. The history review and remaining bounded correction review are
recorded above; the earlier UI review does not qualify the history changes.
Native screens use
debug synthetic data; final signed APKs are checked separately. Samsung layout,
live-source cadence, TalkBack, overnight/battery and physical-watch qualification
remain unverified.
