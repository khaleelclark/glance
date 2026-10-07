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

Two additional controller regression tests exercise repeated equal readings,
sparse backfill, duplicate source times, controller recreation, and a real hour
gap without invented samples. All 228 phone tests pass. The isolated Android
storage probe at e297c97 round-trips 1,440 same-value readings at distinct minute
timestamps through the production LocalStateStore and SecureFileStore, reopens
the store, and merges sparse samples without discarding captured readings.
It verifies encrypted storage and clears its isolated synthetic namespace.
Receipt: evidence/api36-reading-history-storage-0.30.0.json. These checks do not
prove what happened during a specific missing interval on the owner's phone.

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
Independent read-only source and protocol investigation results are tracked.
Native screens use
debug synthetic data; final signed APKs are checked separately. Samsung layout,
live-source cadence, TalkBack, overnight/battery and physical-watch qualification
remain unverified.
