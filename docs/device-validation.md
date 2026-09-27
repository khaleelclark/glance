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

The APK produced so far is a bootstrap shell and does not satisfy product acceptance.

## Scenario status

| Area | Status | Evidence / next action |
|---|---|---|
| LibreLinkUp sign-in and person selection | Not run | Requires integrated adapter and owner login on-device. |
| Same-timestamp source parity | Not run | Requires owner login and comparison with official source. |
| Standard notification, privacy, permission denial | Not run | Test on API 30/33/34/35/36+ emulators after service integration. |
| Live Update | Not run | Test on API 36+ image, then target phone. |
| Forced Doze and recovery | Not run | Use `adb shell dumpsys deviceidle force-idle`, then unforce. |
| Screen-off overnight / 24-hour cadence | Not run | Requires integrated app and long device run. |
| Battery comparison | Not run | Requires matched baseline and monitoring on Galaxy S25 Ultra. |
| Samsung One UI and lock-screen behavior | Not run | Requires target phone and Android/One UI version. |
| In-place signed APK upgrade | Not run | Requires release signing and two versioned APKs. |

Emulator observations will be recorded with API level, system image, commands, elapsed gaps, and screenshots where useful. Emulator battery percentages do not qualify the phone battery target.
