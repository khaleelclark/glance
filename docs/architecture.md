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
- `AppController` is the source-of-truth for selected person and `DisplayState`. It increments a generation before account/person changes and rejects late results from previous generations. A single deferred network operation coalesces timer and manual refresh; a persisted provider cooldown cannot be bypassed by restart or Refresh. Automatic transient backoff remains persisted, while explicit manual retries can bypass it with a three-second monotonic debounce.
- Freshness is computed from `Reading.measuredAt`, not response receipt. Out-of-order readings never replace current data; a duplicate preserves the first receipt; a changed same-timestamp sample may replace it as a source correction.
- The Android background and notification specialists own their packages in isolated branches. Integration into `AppController` and root manifest/build files remains lead-owned.

## Graph and monitoring controls, 2026-09-27

- The user asked to remove Pause and then Refresh from the notification. Refresh stays in the app; Settings provides Stop monitoring so an explicit foreground session can still be ended. Old generation-checked notification intents remain recognized for cards posted before an upgrade.
- The LibreLinkUp graph response supplies both the current measurement and historical samples. Glance validates, sorts, and deduplicates historical samples without letting them replace the current reading. The selected person's bounded history is encrypted in no-backup storage and erased on person change, local-data deletion, and sign-out.
- The main screen draws up to 24 hours of actual observations in a horizontally scrollable timeline. A selected dot shows its exact reading and local timestamp; adjacent-point controls reach closely spaced samples. Gaps longer than 20 minutes remain disconnected. Portrait stacks the chart below the current reading; landscape places the reading and chart in independently scrollable columns. The graph uses no inferred or demo values in release builds. Existing installations seed the graph with their last saved reading until source history arrives. Local history keeps at most 1,500 validated points to cover a full day at one-minute cadence.

## Notification presentation, 2026-09-27

- Live Update is requested by default when Android supports promotion and the user has not turned it off. A stored off choice remains respected. Android's promoted card cannot collapse or use a custom content view, so the standard fallback uses the larger collapsible glucose layout. The Settings switch exposes that tradeoff.
