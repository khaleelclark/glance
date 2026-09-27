# Architecture decisions — 2026-09-27

- The stable Android application ID is `com.khaleel.glance`; the minimum SDK is API 30. The app runs directly on the phone, without a project server.
- `app/src/main/java/com/khaleel/glance/core/Contracts.kt` is the shared boundary for the protocol, monitoring and UI owners. The lead owns changes to it.
- The normalized reading carries the stable connection ID and source measurement timestamp. Successful HTTP contact never changes measurement age.
- Source failures have typed categories. `RATE_LIMITED` carries an absolute next-attempt time, which monitoring must persist.
- All real account entry happens on-device. No fixture or build configuration contains owner credentials.
- Initial development branches are local Git worktrees. Package owners avoid root build files and shared contracts.

The source adapter's `Session` is an in-memory model; the lead's auth store will encrypt persisted credentials and session material with an Android Keystore key. Release signing remains outside Git.
