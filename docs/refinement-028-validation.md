# Glance 0.28 reading emphasis and accent presets

Released base a49a939 (0.27). Root owns UI, integration and release writes.
The user requested color coding on only the top reading again, a slightly larger
number, accent presets and matching app logo. The top number changes 42sp to
50sp/56sp line height, with green/red/yellow source-range classification. Neutral
card/time/unit/trend/supporting labels remain unchanged in color. Freshness and
source values/limits are unchanged; nonfresh/invalid values stay neutral.

Seven curated pastel accents retain black/graphite surfaces and readable controls.
Core AccentPresets is shared by theme/UI and native logo presentation.
Android supports independent launcher icons through activity aliases targeting
the still-enabled MainActivity. The implementation and OS checks are recorded
separately when integrated. Primary references:
[activity aliases](https://developer.android.com/guide/topics/manifest/activity-alias-element)
and [system splash defaults](https://developer.android.com/develop/ui/views/launch/splash-screen). Selection
recomposes the root scheme and native in-app droplet immediately. Legacy inactive
custom values are kept in storage but display as Lilac; new/reset settings use
Lilac. Only preset selection is exposed.

UI implementation 3d3125f passes 225 phone JVM tests, zero failures/errors/skips,
debug assembly and debug lint (zero errors). Three additional tests cover every
preset's role contrast, legacy/invalid fallback and accent-independent semantic
reading colors with contrast. Existing range classification tests cover strict
limits, special source values, stale/invalid states and source units.

The first screen profile passes all 12 API 36 native assertions: green/red/yellow
only on the fresh top number; neutral old value and truthful warning/source time;
all seven preset callbacks once with matching in-app droplet pixels and black
background; saved synthetic accent survives an actual process/activity restart;
three actual isolated UserSettings persistence/reset checks; both system modes;
and 200% portrait/landscape value/time/unit/chart/preset reachability. Root visually
inspected normal low/high/stale, preset sheet/Teal, and large-text portrait/landscape
screens. The complete reading and unit remain visible; overflowing lower content
is reachable by scrolling. Fixture production UI is 3d3125f, before the separately
added badge styling. Receipt retains actual observed commit/dirty state and APK
hash; additional source edits during collection are explicitly described.

The integrated production code passes 226 JVM tests, zero failures/errors/skips,
debug assembly and debug lint (zero errors). The preliminary UI source review
found no HIGH/MEDIUM blocker or concrete LOW; final combined source review is tracked.
Release signing/push remain pending. No live-account/Samsung, TalkBack, overnight/battery,
physical-watch or audible-alert qualification is inferred from synthetic tests.


Additional owner steering requests Libre-style alert colors, with a LibreLinkUp
Logbook screenshot showing a red-bordered burgundy badge around the low glucose
value/unit/trend. Logbook now applies that badge family to recorded Low/Urgent Low
and an amber/yellow family to recorded High/Urgent High. Classification uses the
stored event category, never today's range limits or latest reading. Labels,
source timestamps and posting timestamps remain explicit. Settings alert labels
use the same semantic red/yellow families. This does not alter alarm delivery,
threshold evaluation, sounds, numeric notification icons or recorded event data.


The additional badge profile passes all eight API 36 checks on the 3a25de1
fixture: actual red/burgundy and yellow/amber pixels on all four recorded
categories, original source/posting times with no callbacks, a stored Low event
with an otherwise in-range 113 reading retaining its Low badge, semantic Settings
labels without changing rules, and all four badge values reachable at 200% text.
Root visually inspected normal and large-text Logbook screenshots. Receipt
api36-alert-badges-0.28.0.json retains the fixture hash/commit, observed source
commit and dirty generated-evidence state. Badge collection follows c47e7fc
production styling, with only the historical-Low debug fixture added afterward.
The integrated badge rerun passes the same eight checks. The updated receipt
records the installed fixture hash and observed 5fa5b4e source/dirty state;
the installed fixture was built at 1e56dc7, before a debug-only persistence-flush
correction. Production code matches the final 307aa28 integration in both fixtures.
Final combined review/sign/build/push remain pending. These are source/synthetic UI results, not signed-release device tests.


Native launcher integration uses seven aliases targeting the unchanged enabled
MainActivity. Fresh installs have only Lilac enabled. Stored accent is synchronized
in Application startup and MainActivity settings refresh, covering saves/reset/
sign-out; repeat calls are package-manager no-ops. API33+ switches atomically;
API30-32 enables the target first. Recoverable RuntimeExceptions return FAILED
without preventing startup, and later refresh can repair multiple enabled aliases.
The API31 fixed-lilac splash override is removed, retaining black backgrounds and
light system-bar icons; the launched alias supplies its own icon. Android themed
monochrome icons use the system-selected tint. OEM launcher caches may refresh late.

`scripts/validate_launcher_accent.py emulator-5556` passes all fifteen native
assertions against the final production code on API36: every preset is the sole
launcher, exact foreground pixels match core colors, reapplication is a no-op,
the process survives changes, invalid legacy values select Lilac, multiple-alias
state is repaired, failure returns safely, explicit MainActivity remains available,
stored Teal repairs an intentionally mismatched alias on actual cold startup, and
the selected alias launches MainActivity. The enable-first helper is exercised
against the real API36 framework; this is not an API30 emulator result. API30/32
and Samsung One UI launcher/cache behavior remain unverified.

A separate early cold-start screenshot captured the actual Teal splash with no
Lilac pixels in the center; root visually inspected the Teal droplet on black.
The native receipt includes actual installed APK SHA256 and source/dirty state.
This fixture was built from the 5fa5b4e source (no production changes after 307aa28).
The badge fixture predates only the debug flush. The shell's initial mismatch setup
was rejected under its UID; the committed driver now uses the debug app's own UID.
The first cold-start attempt also exposed immediate instrumentation process exit
before asynchronous synthetic settings flush; the debug-only probe now commits
before exit. Production setting persistence is unchanged.

No release/signed APK device behavior is inferred from these debug fixtures.
Live-account/Samsung, TalkBack, overnight/battery, physical-watch and audible-alert
qualification remain device-dependent work.
