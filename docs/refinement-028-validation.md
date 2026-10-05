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

Native screen/pixel/persistence/200% portrait-landscape checks and launcher
integration are in progress. Independent UI source review is tracked. Release
signing/push remain pending. No live-account/Samsung, TalkBack, overnight/battery,
physical-watch or audible-alert qualification is inferred from synthetic tests.
