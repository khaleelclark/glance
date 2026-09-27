# Architecture decisions — 2026-09-27

- The stable Android application ID is `com.khaleel.glance`; the minimum SDK is API 30. The app runs directly on the phone, without a project server.
- `app/src/main/java/com/khaleel/glance/core/Contracts.kt` is the shared boundary for the protocol, monitoring and UI owners. The lead owns changes to it.
- The normalized reading carries the stable connection ID and source measurement timestamp. Successful HTTP contact never changes measurement age.
- Source failures have typed categories. `RATE_LIMITED` carries an absolute next-attempt time, which monitoring must persist.
- All real account entry happens on-device. No fixture or build configuration contains owner credentials.
- Initial development branches are local Git worktrees. Package owners avoid root build files and shared contracts.

The source adapter's `Session` is an in-memory model; the lead's auth store will encrypt persisted credentials and session material with an Android Keystore key. Release signing remains outside Git.

## Local state and concurrency, 2026-09-27

- `SecureFileStore` writes authenticated AES-GCM ciphertext to Android's no-backup directory with `AtomicFile`. An Android Keystore key is usable without biometric prompts after first unlock. Both cloud backup and device transfer are excluded by manifest policy; corrupt/restored ciphertext sends the app back to sign-in.
- `AuthStore` encrypts reconnect credentials and the reusable session. `LocalStateStore` encrypts the selected stable connection ID, display name, and last normalized reading. Ordinary display and cadence preferences use app-private preferences.
- `AppController` is the source-of-truth for selected person and `DisplayState`. It increments a generation before account/person changes and rejects late results from previous generations. A single deferred network operation coalesces timer and manual refresh; a persisted retry deadline cannot be bypassed by restart or Refresh.
- Freshness is computed from `Reading.measuredAt`, not response receipt. Out-of-order readings never replace current data; a duplicate preserves the first receipt; a changed same-timestamp sample may replace it as a source correction.
- The Android background and notification specialists own their packages in isolated branches. Integration into `AppController` and root manifest/build files remains lead-owned.

## Graph and monitoring controls, 2026-09-27

- The user asked to remove Pause from the notification and main screen. The notification keeps Refresh; Settings provides Stop monitoring so an explicit foreground session can still be ended. The old Pause intent remains recognized for notifications posted before an upgrade.
- The LibreLinkUp graph response supplies both the current measurement and historical samples. Glance validates, sorts, and deduplicates historical samples without letting them replace the current reading. The selected person's bounded history is encrypted in no-backup storage and erased on person change, local-data deletion, and sign-out.
- The main screen draws the last 12 hours of actual observations. Gaps longer than 20 minutes remain disconnected; the graph uses no inferred or demo values in release builds. Existing installations seed the graph with their last saved reading until source history arrives.
