# Device validation log

Status: work in progress; no live follower account or Galaxy S25 Ultra has been tested yet.

## Build host and toolchain, 2026-09-27

| Item | Result |
|---|---|
| Host | Ubuntu 26.04.1, Linux 7.0.0-30-generic, AMD Ryzen 5 5500, 12 logical CPUs, 30 GiB RAM |
| JDK | OpenJDK 17.0.20.1 |
| Android CLI | Command-line tools 22.0 |
| Gradle / AGP | Gradle 9.5.0 / Android Gradle Plugin 9.3.0 |
| Compile / target / min SDK | 37 / 37 / 30 |
| Android SDK root | `/home/phoenix/Android/Sdk` |
| Emulator acceleration | KVM version 12 reported usable under `sg kvm` after host group membership change |
| Disk | 1.1 TiB available at start |
| Debug assemble | **Pass** — `./gradlew :app:assembleDebug`, 2026-09-27, 38 tasks, 2m49s |
| Unit tests | **Pass** — `./gradlew :app:testDebugUnitTest`, 2026-09-27, includes freshness and wrong-person tests |
| API 36 emulator | Google APIs x86_64, Pixel 9 Pro XL profile, KVM accelerated; `Glance_API36` AVD booted (`sys.boot_completed=1`) |
| Bootstrap APK install/launch | **Pass** — `adb install -r app/build/outputs/apk/debug/app-debug.apk`, `adb shell am start -n com.khaleel.glance/.MainActivity`; process remained running; [shell screenshot](evidence/api36-bootstrap-shell.png) |
| Synthetic notification on API 36 | **Pass for renderer only** — debug-only activity posted one silent `glucose_monitoring` card with stable ID 1001, `ONGOING_EVENT|ONLY_ALERT_ONCE`, no sound/vibration, absolute source time, Refresh/Pause actions, and a redacted public version. [Fresh shade screenshot](evidence/api36-notification-fresh-demo.png); `dumpsys notification --noredact` also showed distinct delayed and stale titles. |
| API 36 permission denial | **Pass for platform suppression only** — after `pm revoke com.khaleel.glance android.permission.POST_NOTIFICATIONS`, the debug activity could run but no Glance card appeared in `dumpsys notification`; permission was restored afterward. Setup UI response remains not run. |
| Debug fixture separation | **Pass** — `DebugNotificationActivity` is present only in the debug merged manifest, absent from the release merged manifest. |
| Integrated foreground service on API 36 | **Pass for lifecycle only** — debug-only `service-start` action launched `.monitoring.MonitoringService`; `dumpsys activity services` reported `isForeground=true`, type `0x40000000` (`specialUse`), notification ID 1001 and one silent card. With no account, card correctly said sign-in required; no network polling was claimed. Debug-only `service-pause` stopped the service and removed the card. |
| Synthetic polling under forced Doze, API 36 | **Short smoke pass only** — explicit debug-only `service-demo` signed in with synthetic credentials and selected `DEMO · Patrick Example`; foreground notification showed 112 mg/dL. With screen off, `dumpsys deviceidle force-idle` reported `mState=IDLE`. Persisted debug-only poll timestamps were 06:01:57.148, 06:02:58.546, 06:03:56.454, and 06:04:59.308 UTC: gaps 61.398, 57.908, and 62.854 seconds. The last three starts occurred in forced idle. After `unforce`, `demo-clear` stopped the service, removed the notification, and deleted synthetic account data. This short emulator sample does not establish natural overnight, Samsung, network, or battery behavior. |
| Synthetic reboot recovery, API 36 | **Pass for emulator only** — after `service-demo` persisted monitoring intent, `adb reboot` booted the AVD. Logcat recorded `BootReceiver` delivery and a `BOOT_COMPLETED` foreground-service start exemption at 06:10:49 UTC. `dumpsys` then showed `MonitoringService` foreground with type `0x40000000` and one DEMO notification. Synthetic poll timestamps at 06:10:49.392 and 06:11:49.091 UTC confirm restart and another poll. `demo-clear` stopped the service and removed the card afterward. This does not verify phone reboot behavior or force-stop recovery. |
| Compose sign-in screen on API 36 | **Pass for launch and visual check** — integrated `MainActivity` rendered the follower-account onboarding screen with empty fields; [screenshot](evidence/api36-sign-in.png). No credentials were entered. |
| Integrated main/Pause UI on API 36 | **Pass with synthetic source** — [running screen](evidence/api36-main-demo.png) displayed a DEMO-labeled 112 mg/dL reading, source time, trend, and monitoring status. Tapping Pause stopped the service, removed its notification, and changed the app to [paused status](evidence/api36-paused-demo.png) with Resume available. |
| Notification small icon | **Pass for standard fallback** — the monochrome Glance drop icon rendered in the status bar and shade on API 36; [shade screenshot](evidence/api36-notification-fresh-demo.png). A numeric status-bar icon remains unqualified and is not used. |
| Integrated build and lint | **Pass** — `ANDROID_HOME=/home/phoenix/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintDebug :app:lintRelease --no-daemon` on source `0bb12bf`; 60 synthetic unit tests, 0 failures/errors, and lint passed with warnings. |
| Prior signed personal test APK | **Historical build/signature pass** — clean source `bd60e83f0b2ba61f9b35ed535370914a6f765166` produced `release/glance-0.3.0-personal.apk` (versionCode 3). Its 61 unit tests and debug/release lint passed. APK SHA-256 `d0bd498d724c587ba26211619e3322ace1202188b9fd0829063ee7208a5cd0ef`; signer certificate SHA-256 `837148EAF9B9DEFAB6617ED0EB2F62FA49E04A14F216C4CFD719B8D71778977E`. The 0.4.0 build supersedes it. |
| Prior signed personal test APK | **Historical build/signature pass** — clean source `3c14b7b9235a8ca4d67f5b9431e9e09394817f26` produced `release/glance-0.4.0-personal.apk` (versionCode 4). Its 64 unit tests and debug/release lint passed. APK SHA-256 `28623a6f181104b0ae4d7e6f46c13f40349a2c3ae703dd5853737610b91229dd`; signer certificate SHA-256 `837148EAF9B9DEFAB6617ED0EB2F62FA49E04A14F216C4CFD719B8D71778977E`. The 0.5.0 build supersedes it. |
| Prior signed personal test APK | **Historical build/signature pass** — clean source `59f88026211a2d0e0debcc8581414a753d5e313c` produced `release/glance-0.5.0-personal.apk` (versionCode 5). Its 64 unit tests and debug/release lint passed. APK SHA-256 `4404aaf4c46f06804e4eb903e784451bef595b681c9b31b4543d6aa8db0605b0`; signer certificate SHA-256 `837148EAF9B9DEFAB6617ED0EB2F62FA49E04A14F216C4CFD719B8D71778977E`. The 0.6.0 build supersedes it. |
| Prior signed personal test APK | **Historical build/signature pass** — clean source `250681a253b8c32c86c9fe7748dcde8a2fef2271` produced `release/glance-0.6.0-personal.apk` (versionCode 6). The release command cleaned/rebuilt, ran 68 unit tests (0 failures/errors/skips), ran debug/release lint (0 errors), then signed and verified. APK SHA-256 `220e82b424ee32f03fb6b4428293c3ba25ac3dda25886cfcba60295e9313d704`; signer certificate SHA-256 `837148EAF9B9DEFAB6617ED0EB2F62FA49E04A14F216C4CFD719B8D71778977E`. The 0.7.0 build supersedes it. |
| Prior signed personal test APK | **Historical build/signature pass** — clean source `63949690f3d4d71e47f0f4de7b2a10758e23a0bc` produced `release/glance-0.7.0-personal.apk` (versionCode 7). The release command cleaned/rebuilt, ran 68 unit tests (0 failures/errors/skips), ran debug/release lint (0 errors, 77 warnings), then signed and verified. APK SHA-256 `1fd883a02d32051e5b00824b3ad47c60eb4cb47d2d4e13bf3a501af5359d0bb1`; signer certificate SHA-256 `837148EAF9B9DEFAB6617ED0EB2F62FA49E04A14F216C4CFD719B8D71778977E`. The 0.8.0 build supersedes it. |
| Current signed personal test APK | **Pass for build/signature** — clean source `dbe86d332bc8d548053068ec8147e08967428a13` produced `release/glance-0.8.0-personal.apk` (versionCode 8). The release command cleaned/rebuilt, ran 70 unit tests (0 failures/errors/skips), ran debug/release lint (0 errors, 77 warnings), then signed and verified. APK SHA-256 `45571a2c8549eb00d3eff1ae16144eb2aabefe7800bec7027624ec9c02048a8b`; signer certificate SHA-256 `837148EAF9B9DEFAB6617ED0EB2F62FA49E04A14F216C4CFD719B8D71778977E`. The private signing files remain off-repository and still need an operator-controlled backup. |
| Same-signer in-place upgrade, API 36 | **Pass for install and package data continuity** — removed the prior debug-signed synthetic emulator app, installed signed 0.1.0/versionCode 1, added a synthetic marker under its private files directory, then `adb install -r release/glance-0.2.0-personal.apk` succeeded and versionCode 2 retained the marker. No live account/settings migration was exercised. |
| 0.2.0 → 0.3.0 in-place upgrade, API 36 | **Pass for install and package data continuity** — installed signed 0.2.0/versionCode 2, added a new synthetic private-file marker, then `adb install -r release/glance-0.3.0-personal.apk` succeeded; versionCode 3 retained the marker. Actual encrypted account/settings migration remains untested. |
| 0.3.0 → 0.7.0 in-place upgrade, API 36 | **Pass for signed install and package data continuity** — the original `Glance_API36` AVD had signed 0.3.0/versionCode 3. `adb install -r release/glance-0.7.0-personal.apk` succeeded; versionCode became 7 while appId 10222 and the credential/device-encrypted data inodes stayed the same. `MainActivity` launched without an AndroidRuntime crash. No live encrypted account migration was exercised. |
| 0.7.0 → 0.8.0 in-place upgrade, API 36 | **Pass for signed install and package data continuity** — `adb install -r release/glance-0.8.0-personal.apk` succeeded on the original signed AVD; versionCode became 8 while appId 10222 and credential/device-encrypted data inodes remained unchanged. `MainActivity` launched. No live encrypted account migration was exercised. |
| Dismissal handling, API 36 | **Partial synthetic pass** — updated debug service posted one foreground card. An explicit synthetic dismissal broadcast for its active generation persisted suppression; the new renderer checks this before posting. A real user swipe did not produce a confirmed dismissal event in this emulator run, so delete-intent delivery and Samsung behavior remain unverified. |
| Encrypted-storage failure state | **Pass in unit test** — an injected patient-store write failure after a successful source response returned `STORAGE`, kept the last saved reading, changed connection state to `LOCAL_ERROR`, and blocked automatic retries instead of leaving `FETCHING`. |
| 0.4.0 dismissal and storage correction | **Source and unit verification only** — a sticky service restart now stops a previously dismissed monitoring session before `startForeground`; activity and boot recovery also avoid automatic restart after dismissal. New unit tests cover failed sign-in, session recovery, and person-selection writes. A real notification swipe and low-memory restart remain unverified. |
| 0.5.0 value in status chip | **Source and unit verification only** — promoted notifications now put the fresh glucose value in short critical text; delayed/stale states instead show `OLD`/`STALE`, and the lock-screen public version carries empty chip text. Android may shorten or omit text; actual unlocked/locked rendering and privacy on the target phone remain unverified. |
| 0.6.0 graph and controls | **Source and unit verification only** — the source parser validates, sorts, and deduplicates provider graph samples; controller tests confirm bounded per-person history, and the app draws a 12-hour chart with gaps. Pause is absent from the new notification and main screen; Refresh remains, with Stop in Settings. Live graph ordering, visual layout, and encrypted upgrade migration still need emulator/phone checks. |
| 0.7.0 collapsible notification, API 36 | **Pass for synthetic debug visual check** — separate fresh `Glance_Notification_Test_API36` AVD preserved the previously signed build on the original AVD. The synthetic foreground service showed [compact](evidence/api36-notification-0.7-compact.png) and [expanded](evidence/api36-notification-0.7-expanded.png) cards with a large value/arrow, smaller measurement time and trend, and a small Refresh control; an Android-provided expand/collapse affordance worked. [Dark mode at 130% font scale](evidence/api36-notification-0.7-dark-largefont.png) remained readable after adapting text to notification styles. Tapping the compact Refresh control advanced the synthetic source poll counter from 1 to 2. `dumpsys notification --noredact` showed one ID 1001 card with both custom content views, a redacted public version, and `ONGOING_EVENT|ONLY_ALERT_ONCE|FOREGROUND_SERVICE`. The debug fixture was cleared afterward. Lock-screen presentation, promoted chip mode, and Samsung One UI remain unverified. |
| 0.8.0 24-hour graph and orientation, API 36 | **Pass for synthetic debug visual check** — the debug-only source returned 24 hours of labeled synthetic history with an intentional missing interval. The [portrait chart with a selected point](evidence/api36-graph-0.8-portrait-selected.png) displayed 132 mg/dL at 3:54 PM after a dot tap; the [landscape view](evidence/api36-graph-0.8-landscape.png) placed the current reading beside the graph. The chart scrolled horizontally; the right landscape pane scrolled vertically without moving the left pane. The selected 6:56 PM point remained selected after landscape-to-portrait rotation. The [Settings switch](evidence/api36-live-default-0.8.png) was on by default. Unit tests cover inclusion of readings older than 12 hours and retention of 1,441 one-minute samples over a full day. The demo fixture was cleared after validation. Actual promoted chip behavior, live account data, and Samsung rendering remain unverified. |
| 0.9.0 compact reading and graph scrubbing, API 36 | **Pass for synthetic debug visual check** — the [standard expanded card](evidence/api36-notification-0.9-expanded.png) showed a large 112 mg/dL reading, arrow, (+0) change, and measurement time with no Refresh control. A drag on the [portrait graph](evidence/api36-graph-0.9-scrubbed.png) moved selection from 112 mg/dL at 7:17 PM to a real 123 mg/dL point at 3:22 PM; the selected point and its exact time were visible after release. The graph's selection callback updates during pointer movement. A promoted Live Update was not seen on this AVD; Android controls its text size and promotion. Live readings and Samsung layout remain unverified. |
| 0.10.0 readability and color, API 36 | **Pass for synthetic debug visual check** — the [main screen](evidence/api36-main-0.10-ocean.png) shows a larger aligned arrow and Ocean color; the [24-hour graph](evidence/api36-graph-0.10-spaced.png) has wider time spacing, labeled two-hour marks, and a line with only the selected dot. The [standard notification](evidence/api36-notification-0.10-expanded.png) has larger bold numerals. Entering `FF8800` in the [custom hex field](evidence/api36-settings-0.10-custom-color.png) saved `#FF8800` to local settings and recolored the screen. The promoted Live Update font remains controlled by Android; the source supplies bold text, but this AVD did not show a promoted card. Live account and Samsung visual checks remain unverified. |
| 0.11.1 color wheel | **Source and unit verification only** — Settings adds a touchable hue/saturation wheel, brightness slider, and a live swatch synchronized with the editable hex field. Round-trip tests cover presets, neutral colors, primaries, and dark colors. The build host could not start its API 36 AVD because the current user lacks `/dev/kvm` access, so actual touch behavior and visual layout still need an Android Studio emulator check. |
| 0.12.0 standard-card emphasis | **Source and unit verification only** — the compact and expanded regular notification layouts use a heavier condensed face for the glucose number and a heavy arrow; the regular system-template fallback also bolds its fresh reading. The API 36 AVD still could not start without `/dev/kvm` access, so appearance in the shade needs an Android Studio emulator check. |
| 0.13.0 threshold alerts, API 36 | **Unit and synthetic visual check** — focused tests cover Low, Urgent Low, High, Urgent High precedence, per-category cooldown, repeated source samples, process recreation, disabled channels, person boundaries, stale/special values, and clearing an old alert after a normal reading. The debug-only [Urgent High fixture](evidence/api36-alert-0.13-urgent-high.png) posted a separate alerting card above the silent ongoing card. `dumpsys notification` reported urgent channels at importance 4 with the system alarm sound and ordinary channels at importance 3 with the system notification sound. The host accessed KVM through `sg kvm`; audible playback, channel sound selection, heads-up timing, lock-screen privacy, and real LibreLinkUp delivery remain unverified. |
| 0.13.0 main reading layout, API 36 | **Pass for synthetic visual check** — the [portrait](evidence/api36-main-0.13-portrait.png) and [landscape](evidence/api36-main-0.13-landscape.png) cards place the glucose number, unit, and arrow on one baseline, with a plain-language trend description below. The [portrait at 1.5× font size](evidence/api36-main-0.13-largefont.png) kept that row on one line. Unit tests cover all eight source trend categories and stale readings suppress the arrow. Live source values, very large accessibility fonts, and Samsung rendering remain unverified. |
| 0.14.0 alert wheels and categories, API 36 | **Pass for synthetic UI check** — all four alert channels were registered on app launch. Android initially placed unfired channels under **Show unused categories**; expanding it exposed [Low, Urgent Low, High, and Urgent High](evidence/api36-alert-categories-0.14.png), each separately configurable. Glance's **Sound & priority** button opened the [High category page](evidence/api36-alert-category-high-0.14.png), including Android's Sound and pop-up controls. The [scrolling threshold and cooldown wheels](evidence/api36-alert-wheels-0.14-mgdl.png) changed High from 240 to 241 mg/dL and its repeat time from 5 to 6 minutes; switching to mmol/L preserved the underlying boundary and stepping the wheel changed 13.4 to 13.5 mmol/L. The [dark theme](evidence/api36-alert-wheels-0.14-dark.png) kept the wheel readable. Audible sound choice and Samsung's category screen remain unverified. |

The integrated APK contains the real source adapter, encrypted state, Compose screens, and foreground service. It does not satisfy product acceptance until a live follower account and the target phone are verified.

## Scenario status

| Area | Status | Evidence / next action |
|---|---|---|
| LibreLinkUp sign-in and person selection | Not run live | Synthetic HTTP fixtures pass; owner must enter account on-device. |
| Same-timestamp source parity | Not run | Requires owner login and comparison with official source. |
| Standard notification, privacy, permission denial | Partial | API 36 synthetic renderer, FGS lifecycle, collapsible layout in light/dark mode, and permission suppression checked; visual lock-screen privacy and permission setup UI remain not run. |
| Live Update | Not run | Test on API 36+ image, then target phone. |
| Forced Doze and recovery | Partial | Three debug-only synthetic poll starts remained within 63 seconds in forced idle on API 36. Natural screen-off/phone and network recovery are still unverified. |
| Reboot | Partial | API 36 emulator resumed debug-only synthetic monitoring via BOOT_COMPLETED; target phone and failure fallback remain unverified. |
| Screen-off overnight / 24-hour cadence | Not run | Requires integrated app and long device run. |
| Battery comparison | Not run | Requires matched baseline and monitoring on Galaxy S25 Ultra. |
| Samsung One UI and lock-screen behavior | Not run | Requires target phone and Android/One UI version. |
| In-place signed APK upgrade | Partial | API 36 accepted 0.1.0 → 0.2.0 → 0.3.0 with the same certificate and retained synthetic private-file markers. Saved account/settings migration and phone behavior remain unverified. |

Emulator observations will be recorded with API level, system image, commands, elapsed gaps, and screenshots where useful. Emulator battery percentages do not qualify the phone battery target.

## 0.15.0 Samsung compatibility and numeric icon

Owner-reported target configuration: Galaxy S25 Ultra, Android 16 / One UI
8.5. This reports the installed version, not a successful device test.

The API 36.0 Google APIs x86_64 AVD was tested using KVM via `sg kvm`.
See [the focused test report](notification-0.15-validation.md) for the numeric
icon, privacy, Clear all and Live Update API checks. Actual promotion is
**not verified**: this system image reports promotion disallowed and does not
provide the dedicated promotion settings activity. The regular card remains
available. Samsung Now bar appearance, real source timing and physical-device
battery/background qualification remain outstanding.

## 0.16.0 reference notification layout

See [the focused layout report](notification-0.16-validation.md). The regular
card uses an arrow/reading/unit row, a lighter numeric status icon, and an
actual-source history thumbnail. Source time/status remain available when
expanded. Existing Live Update and privacy choices are retained. Samsung
placement and outer styling are not qualified by the AOSP emulator.

## 0.17.0 phone/car widget

See [car-widget.md](car-widget.md) for the platform decision and setup. The
API 36.0 Google APIs x86_64 AVD uses a debug-only Android AppWidgetHost, not
Android Auto or a Samsung device. Source fixtures are labeled DEMO and absent
from release classes/manifests. Phone/car sharing requires its own opt-in.

The focused release evidence records source formatting, hidden/stopped/wrong-
person/old states, light/dark themes, mg/dL and mmol/L, 180×110 through 360×260 dp
sizes, and 1.0×/1.5× font scale. Larger sizes show the actual-source four-hour
graph; compact sizes retain the reading, unit, available delta and source time. Real-provider
checks observe existing controller updates, privacy/unit changes, Stop and
account clearing. The signed upgrade and final build/review results are recorded
in the release manifest and evidence bundle rather than inferred from fixtures.

Actual Android Auto widget discovery/placement, sideload restrictions, Samsung
scaling, real account parity, overnight scheduling and physical battery use
remain **not run**. Google's next-generation Android Auto widget rollout must
be available before this mobile widget can be selected in the car.

## 0.18.1 monitoring-card retention

See [notification-0.18-validation.md](notification-0.18-validation.md) for the
owner-requested retention choice and posting diagnostics. The API 36 synthetic
foreground session passed real Clear-all and sideways-swipe tests, retention-off
dismissal, old-generation rejection and Stop. A dismissed card is restored as
a regular card with no new promotion request. This emulator cannot qualify
promotion or Samsung Now bar display. The owner reports Android promotion on
0.17.0, with no visible chip and both Clear/swipe removing the card; testing the
0.18.1 recovery behavior on that phone remains outstanding. Five new publication
tests cover Stop/sign-out/person-change ordering, overlapping notify/cancel,
final session validation and pending promotion invalidation. Three storage tests
cover the retention choice's failed-save handling and persistence. Notification
and alert overlap tests ensure a lock-screen privacy change waits for an
in-flight publication and clears it before accepting the hidden choice.
Six storage tests cover lock-screen opt-in/revocation, read and write failure,
deletion fallback, successful recreation and explicitly reported total failure.
The first upgrade to the checked privacy store requires a fresh lock-screen
opt-in; unrelated preferences are preserved. Real simultaneous disk-write and
deletion failure across process death still requires physical verification.

## 0.19.1 DNS failure and manual refresh recovery

See [refresh-recovery-validation.md](refresh-recovery-validation.md) for the
focused automated and synthetic API 36 checks. Manual retries can bypass local
transient backoff while provider rate limits remain enforced. Actual resolver
changes and the owner's Galaxy phone are not covered by these synthetic tests.

## 0.21.0 UI overhaul

Synthetic API36 validation: 20 UI checks and 16 native chart checks passed on
the production implementation with debug-only fixtures. The chart checks include
6/12-hour presets, no two-finger timeline scrolling, pinch bounds, observation
selection, fixed scale, threshold colors/defaults/units and accessible zoom. UI
checks exercise direct Settings controls and fixed tabs, notification style
choices, callback counts, immediate wheel changes before page exit, destructive
cancellation, stale/offline states, dark, 1.5x font, landscape and source units.
Twelve DEMO screenshots are recorded. Manual inspection found the original
landscape plot too tall despite node checks passing; the final compact layout
and a check for its visible time choices replace that evidence.

These tests use synthetic state/counter callbacks and do not establish real
Settings persistence, credential retention, account/person switching, TalkBack,
Samsung rendering/gestures, screen-off/battery reliability or physical watch
behavior. Signed upgrade acceptance/launch is recorded separately in the release
upgrade JSON; it does not exercise credentials or settings retention.
