# Glance 0.28 independent source review

Historical evidence for the version/source identified below. Commands, UI names
and results describe that checkpoint; use the [documentation index](README.md)
for current setup and verification status. Later changes do not extend these receipts.

Preliminary UI source review pinned
`ef53ce48f8b8d58acecec66277e2081870e26e39` over released
`a49a939f79c0079a4e2b9e16288e69d482849ac2`: no HIGH/MEDIUM regression
and no concrete LOW. Reading-only semantic colors, freshness/source gates,
50sp/56sp wrapping hero, seven readable fixed-black accents, preset persistence/
fallback and radio/state-description semantics were source-coherent.

Final integrated review pinned
`1e56dc77246593f91fb14b0abda3cd49bcf88af6` over prior-reviewed ef53ce4.
All production reads used git show; `git diff --check ef53ce4..1e56dc7` passed.
No HIGH/MEDIUM regression and no new concrete LOW.

History badge colors derive solely from each stored AlertKind. Low/Urgent Low use
red/burgundy; High/Urgent High use yellow/amber. Original source value, conversion,
trend, source time, posting time, person and accessible category remain intact.
Settings colors do not change alert rules/evaluation/delivery/privacy/source data.

Only Lilac is manifest-default enabled; every launcher alias targets the still
exported MainActivity. Explicit notification intents remain stable. Core presets
and fallback agree with Compose. API33+ switches atomically; API30-32 enables the
replacement first. Reapplication is a no-op, partial/multiple-alias state can be
repaired, and recoverable RuntimeExceptions return FAILED without crashing startup.
Application startup/settings refresh cover save/reset/sign-out/recreation and
background-created processes. Removing the fixed API31 splash drawable allows the
selected launching alias icon, retaining black windows/backgrounds and light bars.
No glucose/privacy/notification/source behavior is coupled to alias changes.

The reviewer performed source-only work: no edit/build/test/lint/emulator/install/
signing/credential/push actions. Runtime and release checks are root-owned and
recorded in refinement-028-validation.md and generated release manifests.
After the final reviewed pin, only debug probe persistence flushing, validation
driver and documentation/evidence changes occurred; production code is unchanged.
API30-32, Samsung launcher/cache behavior and physical/live qualification are
explicitly unverified.
