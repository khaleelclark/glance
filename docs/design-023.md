# Glance 0.23 visual design

## Direction

Glance 0.23 uses a clear ocean-blue identity (`#1677D2`) on soft white/slate
light surfaces and charcoal/slate dark surfaces. The blue is the only default
brand accent; Material roles are explicitly derived from the selected accent so
controls do not fall back to the library's purple defaults. Existing saved custom
colors continue to render, and the earlier teal remains available as Lagoon.

The launcher and status assets use the same rounded, open **G** mark. They are
native Android vectors with no raster source, patient data, or borrowed artwork.
The launcher uses a blue field and white mark; the notification glyph remains a
monochrome alpha-safe white vector for Android tinting.

## Information architecture

The signed-in shell has persistent bottom navigation:

- **Now** combines a compact person header, value/unit/source trend, source time,
  an observation-only delta when a real prior sample exists, runtime/connection
  status, and the full glucose graph. Refresh and Pause/Resume are compact actions.
- **Logbook** lists real local alert history only. It groups by local fire date,
  preserves the exact alert type, person, source value/trend/time, and fired time,
  offers an optional person filter, and requires confirmation before clearing only
  the alert log.
- **People** appears only when more than one person is available. Cards show each
  person's reading and state. Opening a person changes the local reading view and
  notifies the host without changing the notification person. Pinning is a separate,
  explicit action.
- **Settings** keeps common values in compact grouped rows. Units, cadence,
  notification presentation, alert person, and appearance use bottom sheets;
  direct switches remain direct. All prior privacy, notification, background,
  alerts, widget, watch, diagnostics, reconnect, deletion, and sign-out actions
  remain reachable.

Android Back returns non-Now destinations to Now. A single-person account opens
the reading directly and never exposes an empty People destination.

## Data fidelity and interaction rules

The UI projects a person with `DisplayState.forPerson(id)` and does not synthesize
readings. Delta copy is shown only from the latest earlier numeric source sample
for the same connection, includes its actual elapsed minutes, and disappears over
a 30-minute gap or for special/missing values. The chart continues to draw actual
observations, breaks source gaps, preserves touch inspection and pinch zoom, and
offers 6/12/24-hour viewports. Two-finger translation does not pan the timeline.

Alert thresholds edit in a modal sheet rather than expanding a large wheel inside
the settings list. A compact source-unit wheel stages the threshold; a 1–60 minute
slider stages repeat timing. Changes retain the existing 300 ms coalesced checked
save, flush on Done/dismissal, resynchronize from authoritative host state, and
save enable switches immediately. Per-person edits use the host's
`alertPersonId`/`alertPersonName`; selecting another person calls
`onViewedPersonChanged` before that person's rules can be edited.

## Public UI boundary

`GlanceRoot` adds one trailing source-compatible argument after `locale`:

```kotlin
alertHistory: List<AlertHistoryEntry> = emptyList()
```

The existing positional and named call sites remain valid. The screen consumes
the frozen `DisplayState.people`, `DisplayState.forPerson`, per-person alert fields,
and callbacks without changing shared models. The host must pass real alert history
to populate Logbook; an omitted argument intentionally shows the empty state.

## Accessibility and responsive behavior

Primary actions and setting rows retain at least 48 dp touch targets. Navigation
uses selectable Material semantics and labels; switch rows remain whole-row
toggle targets; trend arrows have spoken descriptions; headers are marked as
headings; long names and summaries wrap. Portrait content scrolls above fixed
navigation. Landscape keeps the reading/status and graph in independently
scrollable columns. Theme accent generation enforces readable primary contrast.

## Verification and limits

Executed from the design worktree:

```text
ANDROID_HOME=/home/phoenix/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Result: build successful; 194 JVM tests, 0 failures/errors/skips; debug lint
completed without errors. New tests cover roster de-duplication, real-sample delta
selection/formatting/suppression, local-day log grouping, the ocean-blue default,
and retention of the legacy teal choice.

No emulator, live account, signing, push, or user-image publication was performed.
The lead still needs to integrate the host's alert-history collection and multi-person
callbacks, then run synthetic API 36 visual/interaction checks. Physical Galaxy S25
Ultra checks remain necessary for TalkBack order, 200% font/display scale, dark-mode
contrast, bottom-sheet keyboard/gesture behavior, Samsung navigation insets, and
comfortable chart height. The private LibreLinkUp reference photos were inspected
only as layout references and are not included in this repository.
