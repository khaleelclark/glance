# Alarm log

Glance keeps a clearable local record of threshold notifications that Android
accepted for posting. It remains a single-person feature: alert settings,
deduplication, notification IDs, and runtime behavior retain their existing
interfaces and semantics.

## Account binding

The backend supplies a stable lowercase SHA-256 `accountScopeKey`; raw provider
account IDs must not be passed or stored. Before alert settings are read or a
poll can be evaluated, integration calls both:

```kotlin
alertPreferences.bindAccount(accountScopeKey)
alertHistory.bindAccount(accountScopeKey)
```

Call these during startup when saved authentication exists and immediately
after successful sign-in, before accepting new poll results. A repeated binding
to the same key preserves alert rules, deduplication/cooldowns, and log entries.
Changing the key atomically clears each store before recording the new binding.

For upgrade compatibility, the first `AlertPreferences` binding adopts the
existing unbound rules and snapshot. The history store does not adopt an
unbound version-1 log because its account cannot be proven: first binding writes
an empty bound ledger. No history is published through `events` until binding
succeeds. Invalid keys, unreadable encrypted state, or failed persistence throw;
callers must stop alert evaluation rather than continue with another account's
data. Explicit `clear()` can delete an unreadable ledger so setup can recover.

Signed-out UI should hide the ledger. Signing out alone need not discard
same-account preferences/history; binding a different account performs the
privacy clear. Full local-data deletion can still call the explicit clear APIs.

## Encrypted local history

`EncryptedAlertHistoryStore(SecureFileStore(context), clock)` stores health
data in an AES-GCM authenticated `AtomicFile` under `noBackupFilesDir`. Each
entry includes the exact `AlertKind`, `Connection`, source `Reading` (including
value and source measurement timestamp), and alert firing timestamp.

The observable list is newest first, deduplicated by event ID, limited to 1,000
events, and pruned after 30 days. Loading bounds the parsed input and rejects
invalid enums, timestamps, numeric fields, special-value alerts, and
cross-person reading identities. Names, internal connection IDs, readings, and
the account-scope digest remain only in encrypted no-backup storage; the alarm
log is not part of diagnostics or exports.

`AlertEngine` takes `history: AlertHistoryPersistence = NoAlertHistory` as its
trailing constructor argument. It appends only after `AlertDelivery.post()`
returns successfully. Blocked channels, duplicate samples, cooldown-suppressed
samples, stale readings, and ordinary in-range samples never create rows. A
history write failure propagates after delivery; the already-posted alert and
committed dedup/cooldown state remain truthful.

`AlertEngine.clearHistory()` is synchronized with evaluation and append. It
clears only the ledger, preserving the alert snapshot and rules, so a user log
clear cannot make the same source sample alert again. The same method is safe
for explicit local-data deletion. Account binding is performed before polling,
outside an active evaluation.

The log proves that Android accepted a notification post, not that sound was
audible. Notification permission, channel importance/sound, Do Not Disturb,
device audio state, OEM policy, and upstream/background delays remain outside
the ledger's guarantee.
