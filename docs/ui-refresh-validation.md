# Phone UI refresh in 0.20.0

Historical evidence for the version/source identified below. Commands, UI names
and results describe that checkpoint; use the [documentation index](README.md)
for current setup and verification status. Later changes do not extend these receipts.

The owner approved a calmer custom presentation with the same functionality:
reading/graph first, neutral backgrounds, restrained accents, compact controls,
shorter descriptions and expandable help. No new runtime architecture or source
behavior is introduced.

## Implementation

GlanceDesign provides the phone's type/shape defaults and custom-shaped accessible
Material button wrappers. ThemeAccent uses neutral light/dark surfaces while
retaining color presets, custom wheel/brightness/hex and contrast correction.
The reading and graph are flat sections. A FlowRow allows value/unit/trend to
wrap when text is enlarged. Freshness, source time, plain-language trend and
connection errors still use the existing formatter without altered rules.

Settings is a list of expandable sections. Display opens initially. Units and
cadence have labeled selectable options; every former setting still calls the
same host callback. Setting switches have one toggleable parent and a passive
child switch. Longer descriptions appear in separate expandable About/Setup
controls which never save settings. Lock-screen/widget/watch consequences
remain in short visible summaries when each section is open. Actual Live Update
and watch sync details remain available. Alert thresholds, repeat wheels,
Android category links, permission/battery links, account controls and destructive
confirmation are retained. Android Back closes Settings. No persisted preference
keys, defaults or migration change.

The chart limit/viewport/selection math and pointer handler are unchanged. Only
its containers and visible copy change. Notification layouts, privacy storage,
alerts, source adapter, credentials, monitoring, phone/car widgets and watch
runtime are outside this delta. Phone/watch versions are coordinated at version code 25,
version name 0.20.0; existing 0.19 watch installs remain compatible.

## Reproduction and evidence

Run `./gradlew :app:assembleDebug`, install the debug APK on a dedicated API 36
emulator, then run `python3 scripts/validate_ui_refresh.py emulator-5556`.
DebugUiActivity uses production GlanceRoot with synthetic local state. Its
counter-only callbacks never contact LibreLinkUp or start monitoring. It and
its manifest entry exist only under app/src/debug. A fixed DEMO badge labels
all fixture screens, including Settings and sign-in. No real user screenshots
are included in repository evidence.

`docs/evidence/api36-ui-0.20.0.json` records the 14 UI checks. They exercise
fresh value/time/graph, compact action dispatch, color controls, privacy
summary/help, toggle checked semantics and exactly one callback, all settings
sections with a representative control from each, sign-out confirmation/cancel, Android Back and retained fixture unit,
stale trend suppression, offline text, sign-in inputs, dark theme, 1.5x font,
and landscape. These are UI/fixture checks, not real settings persistence or
account integration. Screenshots cover main light/dark, Settings, stale,
sign-in, large-font main/Settings and landscape. Manual image inspection
complements node-presence checks; node presence alone does not prove typography
comfort or real TalkBack mapping.

The existing debug chart instrumentation is run separately to recheck its 13
synthetic gesture/limit/color checks after the container change; the new result
is `docs/evidence/api36-chart-0.20.0.json`. It still uses its original default
Material palette, not the new production app theme. New-theme rendering is
covered by the GlanceRoot screenshots. Neither fixture runs an account/network,
physical watch or Samsung notification shell.

Final clean unit tests/lint, signatures, APK hashes and exact source commit are
recorded in ignored release manifests. Signed upgrade acceptance and launch
are recorded separately; credential/settings retention is not exercised.
Independent source review is supplied with the final release validation archive.

Physical Galaxy S25 Ultra appearance, real TalkBack, screen-off/battery behavior,
live-account parity, person/account switching and physical watch/Data Layer
remain unverified. Install over the current phone app and check the main screen,
expandable settings and your preferred text size. The watch needs no update.
