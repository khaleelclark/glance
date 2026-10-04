# Glance 0.27 independent review record

The independent reviewer inspected exact clean production
227b9436cb61cb86d425059a5bc0817c2cdda410 against released 0.26 c84ef6d,
pinning every source read with git show. git diff --check passed. No HIGH/MEDIUM
blocker was found. The reviewer did not edit, build, test, lint, run a device,
use credentials, sign or push.

Numeric alarm icons, quiet updates preserving trigger text, privacy/source/unit/
setting gates, delete tokens, lock ordering and ongoing/enhanced icon parity
were accepted in the bounded source review. One LOW remained: a system-owned
numeric alarm could survive normal process death beyond the five-minute freshness
boundary until a later render removed the unscoped card.

Production correction ce47d069a30b87a638772507f7df42fe9cc5adf1 uses Android's
system-owned notification timeout for a numeric alert, calculated from the source
measurement's freshness deadline. New samples renew the deadline even when their
formatted value stays the same; duplicate samples cannot extend it. A notification
updated to a glyph no longer carries a numeric value. The timeout removes the
alert card, retaining the event in encrypted Alarm Log; the ongoing reading card
is unaffected. No new application scheduler or exact-alarm permission is added.
Shared ReadingPolicy.FRESH_WINDOW retains the existing five-minute threshold.

Root's corrected candidate passes 222 JVM tests and 19 native icon checks.
All three actual process-death/freshness-boundary checks also pass: an ordinary
alarm survives SIGKILL, then the system timeout removes it without a manual
reopen/render. The exact candidate/fixture and observation timing are recorded
in api36-alert-expiry-0.27.0.json. Independent correction closure remains pending; final clean release build/signing and push are pending.
Native evidence is not signed-release runtime qualification. Live-account/Samsung,
TalkBack, overnight/battery, physical-watch and audible qualification remain
outstanding.
