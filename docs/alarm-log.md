# Alarm log

Glance keeps a clearable local record of threshold notifications that Android
accepted for posting. It remains a single-person feature: alert settings,
deduplication, notification IDs, and runtime behavior retain their existing
interfaces and semantics.

## Account cleanup

Explicit new sign-in clears the log before accepting another account session;
if that clear fails, sign-in stops with a storage error. Sign-out and full local
health-data deletion also clear the log. A routine restart with the existing
encrypted session retains its history. No new account-binding or multiple-person
API is introduced in this release.

## Encrypted local history

`EncryptedAlertHistoryStore(SecureFileStore(context), clock)` stores health
data in an AES-GCM authenticated `AtomicFile` under `noBackupFilesDir`. Each
entry includes the exact `AlertKind`, `Connection`, source `Reading` (including
value and source measurement timestamp), and alert firing timestamp.

The observable list is newest first, deduplicated by event ID, limited to 1,000
events, and pruned to 30 days on load and append. Loading bounds the parsed input and rejects
invalid enums, timestamps, numeric fields, special-value alerts, and
cross-person reading identities. Names, internal connection IDs, readings, and
source details remain only in encrypted no-backup storage; the alarm
log is not part of diagnostics or exports.

`AlertEngine` takes `history: AlertHistoryPersistence = NoAlertHistory` as its
trailing constructor argument. It appends only after `AlertDelivery.post()`
returns successfully. The posting timestamp is captured immediately after that
call returns, separately from the source measurement time. Blocked channels,
duplicate samples, cooldown-suppressed
samples, stale readings, and ordinary in-range samples never create rows. A
history write failure propagates after delivery; the already-posted alert and
committed dedup/cooldown state remain truthful. Unreadable history stays hidden
and cannot be appended until an explicit clear succeeds. Clear atomically writes
an empty encrypted ledger; a failed write preserves the visible list and reports
failure instead of claiming cleanup succeeded.

`AlertEngine.clearHistory()` is synchronized with evaluation and append. It
clears only the ledger, preserving the alert snapshot and rules, so a user log
clear cannot make the same source sample alert again. The same method is safe
for explicit local-data deletion. Sign-out retains the existing behavior of
clearing alert preferences; log-only clear leaves preferences and cooldowns intact.

The log proves that Android accepted a notification post, not that sound was
audible. Notification permission, channel importance/sound, Do Not Disturb,
device audio state, OEM policy, and upstream/background delays remain outside
the ledger's guarantee.
