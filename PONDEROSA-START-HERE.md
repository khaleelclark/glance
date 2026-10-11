# Glance: resume work and original kickoff

## Resume the existing app

Glance is already implemented at `/home/phoenix/khaleel/glance`; do not bootstrap
or rebuild its scope from scratch. Read [README](README.md), the
[documentation index](docs/README.md), [current architecture](docs/architecture.md)
and [release guide](docs/release.md), then the original specification and its
current implementation addendum. Inspect Git status, actual source and pinned
release/evidence files before changing anything. Current app version is 0.30.0
(code 35); the Studio launch fix is on main. Preserve signing continuity and
existing data. Documentation updated 11 October 2026.

The original kickoff below records the initial build request and delivery gates.
Its future tense, Wednesday arrival and proposed team assignments are historical;
use current user instructions and current guides when resuming. Unrun physical,
account and battery tests remain outstanding rather than being waived.

## Original kickoff — 27 September 2026

Use **`/home/phoenix/khaleel/glance`** as the working directory for a new Ponderosa chat. Both this file and `GLANCE-ANDROID-SPEC.md` are already in that folder. No additional Ponderosa project setup is needed for this checkout. Tell the agent to open and read the files by their exact paths; file presence alone is not a guarantee that the agent receives their contents. Do not create another nested `glance/` directory.

The short message to paste into the chat is:

> Work in `/home/phoenix/khaleel/glance`. Read `PONDEROSA-START-HERE.md` and `GLANCE-ANDROID-SPEC.md` in full, then build the app they describe. You are the lead; use Ponderosa agents for independent specialist work if available, and return the signed APK plus the verification evidence specified in the documents. Start with emulator work while I wait for my Galaxy S25 Ultra.

The detailed original build request is preserved below; it has since been used to build the existing app.

---

Build the personal, sideloaded Android application specified in `/home/phoenix/khaleel/glance/GLANCE-ANDROID-SPEC.md`. Use `/home/phoenix/khaleel/glance` as the project root. Read the spec in full before implementation. You are the responsible Ponderosa lead and integrator. Use the spec as the product baseline and record material engineering decisions in project documentation.

The app's purpose is simple: I log into my existing LibreLinkUp follower account, select Patrick, and see his latest glucose value, trend, and measurement time in an ongoing Android notification, including on the lock screen when I enable that. Existing LibreLinkUp/Gluroo-quality source readings are sufficient. Prioritize faithful display, background reliability, and a clean setup. The app runs independently on my phone; pine and Ponderosa are only the build environment.

Use a native Kotlin/Compose app in this separate project, not a PWA or a change to Ponderosa. Deliver a signed APK suitable for personal installation, with source, repeatable build instructions, upgrade/signing notes, and test evidence. No Play Store release, app backend, analytics, treatment features, or public deployment is needed.

My target phone is a Samsung Galaxy S25 Ultra, expected Wednesday. Ask for its actual Android and One UI versions after it arrives. Start now with the spec's defaults. Inventory the Android/JDK build tools and emulator acceleration on pine; if pine cannot run an emulator, build and unit-test there and use Android Studio on a suitable local computer for virtual-device testing. Use a virtual device to test notification appearance, optional Live Update behavior where supported, permission states, fake-data scenarios, and forced Doze/recovery before the phone arrives. A real follower account can be tested in the emulator if I sign in there. Do not claim emulator testing proves Samsung One UI behavior, overnight reliability, or battery impact. I will enter my LibreLinkUp credentials in the installed app; do not ask me to paste them into chat or put them in code, fixtures, logs, or CI.

You may create a small team using agent delegation features actually available in the current Ponderosa chat. Suggested roles are an Android runtime/notification specialist, a LibreLinkUp integration specialist, a UI specialist if useful, and an independent read-only reviewer. Use only available capacity. Resolve the installed capabilities rather than inventing tool/API names. Provider-native hidden helpers do not count as visible Ponderosa agents.

Give specialists bounded tasks, precise file ownership, and acceptance criteria. Use the current chat's supported coordination features to collect outcomes and exact artifact paths. Keep the lead responsible for integrating specialist work. Share durable decisions explicitly with the agents who need them; don't assume private chats or tool output are shared memory.

Use one writer per branch/worktree and a read-only reviewer. Own shared interfaces and root build files centrally before delegating independent implementations. Do not modify the Ponderosa repository, services, engine authentication, or scheduler. Record exact base/head commits, dirty state, tests, and artifact paths at handoff. Keep changes local unless I separately authorize a remote repository workflow.

First prove the difficult part: a real ongoing notification with a credible screen-off scheduling strategy. Evaluate the proposed specialUse foreground service against current Android requirements and the target device. Do not implement a continuous dataSync service that hits the six-hour background budget, assume WorkManager can poll every minute, or assume a coroutine delay alone wakes a suspended phone. Measure battery behavior and document the selected strategy. I would also like to try Android's promoted Live Update presentation if my phone supports it. Build it as an optional display mode after the standard notification works, with a user switch and a graceful fallback. Numeric status-bar icons are another conditional enhancement.

In parallel, inspect a maintained LibreLinkUp client such as GlucoDataHandler, record the exact source revision/license, and establish current protocol details. Use synthetic fixtures. Implement regional routing, selected-person identity, session reuse, terms-required handling, cooldowns, and a single-flight poller. Do not guess current client headers or endpoint behavior from memory.

Then build the end-to-end real-data path and complete the state handling: fresh, delayed, stale, offline, paused, authentication required, and unavailable connection. A successful HTTP request must never make an old measurement look new. Always show its measurement time. Keep credentials encrypted on-device, show only the selected person's data, and default to silent routine updates.

Work through the spec's phases and acceptance table. Run appropriate automated tests, source/display parity checks, lifecycle scenarios, and the real-phone overnight/24-hour tests when access is available. Get independent review of the delivered commit. Treat long-running physical tests as outstanding verification until evidence exists; do not silently waive them to declare completion.

At handoff, provide the APK, SHA-256, signer fingerprint, source commit, build/install instructions, protocol/background decision records, review findings and resolutions, and a test report with pass/fail/not-run results. Clearly distinguish a built APK from a live-account-verified app and a device-qualified release. When a remaining step requires my phone or on-device login, finish all independent work and tell me exactly what to do next.

Make ordinary implementation choices autonomously within the spec. Ask only when a missing choice would materially change the product, requires my device/account interaction, or expands the architecture beyond the direct phone-only app.

---

## Optional initial assignments

These can be copied into managed Requests after the lead establishes project paths and shared interfaces.

### Android runtime specialist

Own the background feasibility spike and `monitoring/` implementation. Prove user-started notification/service operation, lifecycle handling, pause semantics, single-flight scheduling, and a measured screen-off strategy. Deliver `docs/background-strategy.md`, implementation/test commits, and device evidence or explicit unverified cases. Coordinate renderer interfaces with UI owner. Do not add a server, inappropriate service type, or unmeasured continuous wake lock. Return exact branch/worktree/head, test results, open issues, and next action through the Request.

### LibreLinkUp specialist

Own `librelinkup/` and protocol fixtures. Inspect maintained source, pin revision/license, document current login/session/region/connection/latest-reading contract, and implement typed outcomes with redacted diagnostics. Cover stale/duplicate data, invalid auth, terms, removed person, cooldowns, malformed responses, and timestamp normalization. Use synthetic tests; live credentials are entered by the owner on-device. Deliver `docs/protocol.md`, code, fixture tests, attribution, and exact handoff state through the Request.

### UI and notification specialist

Own `ui/` and `notification/` after shared DisplayState is frozen. Build onboarding, person selection, readable main/settings screens, standard silent ongoing notification, lock-screen privacy, and all freshness/error variants. Verify large fonts, dark mode, TalkBack, and action behavior. Numeric icons and promoted Live Updates are conditional enhancements. Return screenshots with synthetic data, tests, exact handoff state, and any device-dependent limitations through the Request.

### Independent reviewer

Review the exact proposed release commit read-only against requirements R01–R10 and the acceptance table. Prioritize wrong-person/stale-value display, auth leaks, lifecycle failure, unsupported scheduling claims, retry storms, and release-signing continuity. Verify evidence rather than trusting author summaries. Report severity, affected path, reproduction or reasoning, and required correction. Mark unavailable live/device tests explicitly; do not approve them by inference.
