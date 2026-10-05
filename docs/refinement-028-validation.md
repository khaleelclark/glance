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

Additional badge source passes 226 JVM tests, zero failures/errors/skips, debug
assembly and debug lint (zero errors). Launcher integration is in progress. Independent UI source review is tracked. Release
signing/push remain pending. No live-account/Samsung, TalkBack, overnight/battery,
physical-watch or audible-alert qualification is inferred from synthetic tests.


Additional owner steering requests Libre-style alert colors, with a LibreLinkUp
Logbook screenshot showing a red-bordered burgundy badge around the low glucose
value/unit/trend. Logbook now applies that badge family to recorded Low/Urgent Low
and an amber/yellow family to recorded High/Urgent High. Classification uses the
stored event category, never today's range limits or latest reading. Labels,
source timestamps and posting timestamps remain explicit. Settings alert labels
use the same semantic red/yellow families. This does not alter alarm delivery,
threshold evaluation, sounds, numeric notification icons or recorded event data.
