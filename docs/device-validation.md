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
| Compose sign-in screen on API 36 | **Pass for launch and visual check** — integrated `MainActivity` rendered the follower-account onboarding screen with empty fields; [screenshot](evidence/api36-sign-in.png). No credentials were entered. |
| Integrated build and lint | **Pass** — `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease`; 0 lint errors, 19 warnings in each variant. Release APK is unsigned at this stage. |

The integrated APK contains the real source adapter, encrypted state, Compose screens, and foreground service. It does not satisfy product acceptance until a live follower account and the target phone are verified.

## Scenario status

| Area | Status | Evidence / next action |
|---|---|---|
| LibreLinkUp sign-in and person selection | Not run live | Synthetic HTTP fixtures pass; owner must enter account on-device. |
| Same-timestamp source parity | Not run | Requires owner login and comparison with official source. |
| Standard notification, privacy, permission denial | Partial | API 36 synthetic renderer, FGS lifecycle, and permission suppression checked; visual lock-screen privacy and permission setup UI remain not run. |
| Live Update | Not run | Test on API 36+ image, then target phone. |
| Forced Doze and recovery | Not run | Use `adb shell dumpsys deviceidle force-idle`, then unforce. |
| Screen-off overnight / 24-hour cadence | Not run | Requires integrated app and long device run. |
| Battery comparison | Not run | Requires matched baseline and monitoring on Galaxy S25 Ultra. |
| Samsung One UI and lock-screen behavior | Not run | Requires target phone and Android/One UI version. |
| In-place signed APK upgrade | Not run | Requires release signing and two versioned APKs. |

Emulator observations will be recorded with API level, system image, commands, elapsed gaps, and screenshots where useful. Emulator battery percentages do not qualify the phone battery target.
