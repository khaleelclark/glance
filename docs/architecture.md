# Current architecture

Applies to 0.32.0, including themed Live Updates and explicit recovery; checked 22 October 2026.
The phone connects directly to LibreLinkUp. Pine/Ponderosa build the app and
are outside its runtime data path. The optional watch receives a credential-free
projection from the phone, not its own account or polling worker.

## Source, state and persistence

`app/src/main/java/com/khaleel/glance/core/Contracts.kt` defines `GlucoseSource`,
`SourceResult`, `SourceErrorKind`, `SourceSnapshot`, `Reading` and `DisplayState`.
`LibreLinkUpSource` validates the unofficial protocol, routes only to allowed
HTTPS origins, bounds responses and separates strict current parsing from
historical minimal rows. See [protocol](protocol.md) for pinned references.

`AppController` owns selected connection, session recovery, observable state and
history. A generation changes at person/account transitions so old responses
cannot populate another person's display. One deferred operation coalesces
manual/automatic work. Provider cooldowns persist and cannot be bypassed;
manual Refresh may bypass transient backoff with a three-second monotonic debounce.

Freshness uses `Reading.measuredAt`, not response receipt: fresh <5 minutes,
delayed <15, otherwise stale; excessively future timestamps are invalid.
Out-of-order readings cannot replace current. Identical samples retain the
original receipt; validated same-time corrections may replace them.

`SecureFileStore` uses Android Keystore AES-GCM and `AtomicFile` under
`noBackupFilesDir`. `AuthStore`, `LocalStateStore`, encrypted nicknames and
`EncryptedAlertHistoryStore` keep sensitive state encrypted. Manifest/backup
rules exclude account/health state from cloud backup/device transfer. Ordinary
preferences retain display/cadence/retry intent; dedicated checked grants govern
notification/widget/watch exposure. Storage failures are reported explicitly.

`ReadingHistory` filters by selected connection and age, sorts by source time
and keeps at most 1,500 numeric points within 24 hours. Equal values at distinct
times remain distinct. Adapter deduplication and store merge share reported-value
preservation only for equal connection/time/canonical mg/dL; corrections clear
obsolete metadata and incoming unknown trends remain unknown. History never
replaces strict current parsing or fills absent source samples.

## Monitoring and outputs

`MonitoringService` is the user-visible foreground session; `PollCoordinator`
owns automatic scheduling. The bounded wake strategy, recovery paths and
unverified physical cadence/power are in [background strategy](background-strategy.md).
Stop/sign-out invalidate work and remove the monitoring notification.

The regular notification uses a silent channel and custom compact/expanded
views. Live Update uses a standard Android template with promotion checks.
On Android 17+, numeric small icons use deterministic PNG data, which platform
GroupHelper compares by content. Matching monitor/alert values can therefore
supply a numeric automatic summary instead of an app glyph. No app-provided
notification group or summary is added; prior independent dismissal semantics
are retained. Earlier Android versions keep bitmap icons because their grouped
summary update behavior differs. Live Update chooses colorization only if the
actual framework's `hasPromotableCharacteristics()` requires it. Diagnostics
separates shape eligibility, requested/actual promotion, grouping and icon types.
A shared publication gate orders final state/privacy checks and notify/cancel.
The default-on retention choice restores the regular card on a delivered
current-session dismissal callback; there is no automatic re-promotion loop.
An explicit restore action is available on Now after dismissal and in Settings.
Live requests use the droplet resource icon plus one short-critical-text value,
and the same curated accent as the UI. Accent selection immediately republishes
the card; the final publication gate also validates the captured accent so an
older render cannot replace the newer theme. Samsung controls displayed tint.
Explicit content intents target always-enabled `MainActivity`. See
[notification notes](../app/src/main/java/com/khaleel/glance/notification/README.md).

`AlertEngine` evaluates only eligible fresh selected-person samples from existing
polls. Per-category cooldowns/fingerprints persist. Logbook is encrypted, bounded
to 1,000 events/30 days, and records accepted posts rather than audible delivery.
Numeric alarm icons can track the latest eligible reading without changing their
original trigger; system timeouts bound numeric-card freshness. See
[alerts](alerts.md) and [alarm log](alarm-log.md).

Widgets observe existing state and use a publication gate to order redaction
against rendered host views. Watch publishing is serialized and sends only
formatted value/unit/trend/time/status with a five-minute lease and ordered
content-free revocations. Disconnected receivers cannot be cleared instantly;
see [widgets](car-widget.md) and [watch](watch.md).

## UI and launch

Now / Logbook / Settings uses black/graphite surfaces, seven accent presets and
matching native droplet aliases. Fresh reading-card bands are independent of
accent. Graph windows are 3/6/12/24 hours, with hold/drag inspection and dismissal
on release; no pinch or pan. The fixed 40–350 mg/dL scale uses ordinary enabled
Low/High boundaries or 70/240 defaults, keeps actual off-scale values and breaks
gaps >20 minutes. Notification/widget thumbnails use their own >15-minute gap rule.

The shared `.run/Glance_phone.run.xml` targets MainActivity directly; selecting a
nondefault launcher accent cannot disable that Studio target. Runtime and signer
are unchanged by this configuration. See [Studio launch evidence](studio-accent-launch.md).

Debug synthetic fixtures live only under debug source sets. Historical checks
retain exact source/APK boundaries; they do not qualify a signed APK, live account
or physical Samsung/watch/car behavior by inference. See the
[documentation index](README.md) and [device validation](device-validation.md).
