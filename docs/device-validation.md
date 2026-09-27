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
| Current signed personal test APK | **Pass for build/signature only** — clean source `bd60e83f0b2ba61f9b35ed535370914a6f765166` produced `release/glance-0.3.0-personal.apk` (versionCode 3). The signing command cleaned/rebuilt, ran 61 unit tests (0 failures/errors/skips), ran debug/release lint (0 errors), then signed and verified. APK SHA-256 `d0bd498d724c587ba26211619e3322ace1202188b9fd0829063ee7208a5cd0ef`; signer certificate SHA-256 `837148EAF9B9DEFAB6617ED0EB2F62FA49E04A14F216C4CFD719B8D71778977E`. Prior 0.2.0 APK is superseded. The private signing files remain off-repository and still need an operator-controlled backup. |
| Same-signer in-place upgrade, API 36 | **Pass for install and package data continuity** — removed the prior debug-signed synthetic emulator app, installed signed 0.1.0/versionCode 1, added a synthetic marker under its private files directory, then `adb install -r release/glance-0.2.0-personal.apk` succeeded and versionCode 2 retained the marker. No live account/settings migration was exercised. |
| 0.2.0 → 0.3.0 in-place upgrade, API 36 | **Pass for install and package data continuity** — installed signed 0.2.0/versionCode 2, added a new synthetic private-file marker, then `adb install -r release/glance-0.3.0-personal.apk` succeeded; versionCode 3 retained the marker. Actual encrypted account/settings migration remains untested. |
| Dismissal handling, API 36 | **Partial synthetic pass** — updated debug service posted one foreground card. An explicit synthetic dismissal broadcast for its active generation persisted suppression; the new renderer checks this before posting. A real user swipe did not produce a confirmed dismissal event in this emulator run, so delete-intent delivery and Samsung behavior remain unverified. |
| Encrypted-storage failure state | **Pass in unit test** — an injected patient-store write failure after a successful source response returned `STORAGE`, kept the last saved reading, changed connection state to `LOCAL_ERROR`, and blocked automatic retries instead of leaving `FETCHING`. |

The integrated APK contains the real source adapter, encrypted state, Compose screens, and foreground service. It does not satisfy product acceptance until a live follower account and the target phone are verified.

## Scenario status

| Area | Status | Evidence / next action |
|---|---|---|
| LibreLinkUp sign-in and person selection | Not run live | Synthetic HTTP fixtures pass; owner must enter account on-device. |
| Same-timestamp source parity | Not run | Requires owner login and comparison with official source. |
| Standard notification, privacy, permission denial | Partial | API 36 synthetic renderer, FGS lifecycle, and permission suppression checked; visual lock-screen privacy and permission setup UI remain not run. |
| Live Update | Not run | Test on API 36+ image, then target phone. |
| Forced Doze and recovery | Partial | Three debug-only synthetic poll starts remained within 63 seconds in forced idle on API 36. Natural screen-off/phone and network recovery are still unverified. |
| Reboot | Partial | API 36 emulator resumed debug-only synthetic monitoring via BOOT_COMPLETED; target phone and failure fallback remain unverified. |
| Screen-off overnight / 24-hour cadence | Not run | Requires integrated app and long device run. |
| Battery comparison | Not run | Requires matched baseline and monitoring on Galaxy S25 Ultra. |
| Samsung One UI and lock-screen behavior | Not run | Requires target phone and Android/One UI version. |
| In-place signed APK upgrade | Partial | API 36 accepted 0.1.0 → 0.2.0 → 0.3.0 with the same certificate and retained synthetic private-file markers. Saved account/settings migration and phone behavior remain unverified. |

Emulator observations will be recorded with API level, system image, commands, elapsed gaps, and screenshots where useful. Emulator battery percentages do not qualify the phone battery target.
