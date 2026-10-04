# Alarm log and per-person alerts

Glance keeps threshold choices, deduplication state, notifications, and alarm
history scoped to the LibreLinkUp connection that produced the source reading.
Every person's four rules start disabled. On upgrade, the lead must call
`AlertPreferences.migrateLegacyTo(primaryPersonId)` once after loading the
existing primary connection; this atomically assigns the prior global rules and
cooldowns to that person. Later people retain disabled defaults.

Use `configForPerson(personId)`, `setRuleForPerson(personId, kind, rule)`, and
`forPerson(personId)` for settings and engine state. Person preference prefixes
are stable SHA-256 digests, so raw provider connection IDs are not present in
ordinary SharedPreferences keys. `clearSnapshot()` clears deduplication and
cooldowns in every scope without changing rules. `clear()` removes every alert
rule and scope.

The alert engine must receive the current display for the same person as the
successful, published source reading. Its integration points are:

```kotlin
AlertEngine(
    preferences = alertPreferences,
    notifier = alertNotifier,
    clock = DeviceClock,
    currentDisplay = { controller.display.value },
    currentPrivacy = { controller.settings.showOnLockScreen },
    history = alertHistory,
    personStore = alertPreferences::forPerson,
    currentPersonDisplay = controller::displayForPerson,
)
```

The exact controller callback can differ, but it must return the current
same-person `DisplayState`, including account monitoring/runtime state. The
five-argument constructor remains compatible with the single-person path.

## Encrypted local history

`EncryptedAlertHistoryStore(SecureFileStore(context))` stores health data in an
AES-GCM authenticated `AtomicFile` under `noBackupFilesDir`. It records only an
alert that passed policy/channel checks and whose Android notification post
returned successfully. Each entry contains the exact alert kind, `Connection`,
source `Reading` (including measurement timestamp and value), and alert firing
timestamp. Blocked channels, duplicate polls, stale readings, special-value
sentinels, and ordinary in-range samples are not logged.

The observable list is newest first, deduplicated by event ID, limited to 1,000
events, and pruned after 30 days. Invalid enums, timestamps, numeric fields, or
cross-person reading identities are rejected while loading. A corrupt,
undecryptable, or unknown-version file fails closed to an empty in-memory list;
the data is never copied to diagnostics or backup.

History persistence is deliberately best effort after successful notification
delivery. If its atomic write fails, the alert remains delivered and its
cooldown remains committed, but no history row is claimed. The UI should
explain a storage failure if it adds a surfaced error path later. Explicit
**Clear alarm log** calls only `AlertHistoryPersistence.clear()` and does not
remove reading caches or alert settings. Account sign-out must also call that
method in addition to its existing cache/auth cleanup.

## Notification coexistence and routing

The four stable channel IDs and their system-selected sounds are unchanged.
Cards use a SHA-256-derived notification tag per person plus a fixed ID per
alert kind. Posting or clearing an in-range sample removes only that person's
other threshold cards; `clear()` removes all tagged alert cards without
canceling the ongoing glucose notification.

Alert taps use action `GlucoseAlertNotifier.ACTION_OPEN_ALERT` and the explicit,
immutable extra `GlucoseAlertNotifier.EXTRA_PERSON_ID`. The extra contains only
the stable internal connection ID and is carried by an explicit pending intent;
the lead can route it to that person's detail view and must not log or export
it. Lock-screen redaction remains independent of routing data.

This log is not proof that sound was audible. Android notification permission,
channel sound/importance, Do Not Disturb, OEM settings, background execution,
and device audio state remain user/system controlled.
