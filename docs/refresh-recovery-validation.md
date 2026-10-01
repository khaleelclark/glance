# 0.19.1 manual refresh after connection failure

The October 1 report showed a historical reading and “Waiting before trying
again” after a phone DNS failure had been resolved. Both the controller and
service treated Glance's own exponential retry delay as a provider cooldown,
so Refresh could be rejected for up to 15 minutes plus jitter.

## Changed behavior

- Automatic network/server retries retain their persisted exponential backoff.
- Refresh may bypass that transient delay. Requests still share one in-flight
  operation, and manual taps have a three-second monotonic debounce.
- During a request the button says “Refreshing…” and becomes available again
  after completion. Debounced taps get a brief explanation.
- Provider 429/Retry-After cooldowns remain mandatory for manual and automatic
  requests. The main screen explains the provider limit and its retry time.
- The retry reason and deadline are committed together to preferences. Existing
  deadlines without a reason are conservatively treated as provider cooldowns
  until they expire; this avoids discarding a real 429 during an upgrade.
- A failed session recovery is counted once and retains the provider/network
  distinction. Historical measurement timestamps and person boundaries are
  unchanged.

This changes the original baseline's blanket ban on bypassing transient
backoff, as requested by the owner. It does not change automatic cadence,
provider cooldowns, credentials, notification channels, or watch transport.

## Verification

The phone's 160 JVM tests pass, including fixed-DNS retry during backoff,
repeated failures at the maximum automatic delay, three-second debounce with a
changed wall clock, single-flight manual/automatic polling, restart persistence,
server failure recovery, and network/provider limits during session recovery.
UI tests distinguish a connection retry from a timed provider cooldown. The
existing 15 watch tests run again in the clean signed release build.

Run the focused synthetic Android checks on a dedicated debug emulator:

```sh
ANDROID_HOME=/home/phoenix/Android/Sdk ./gradlew :app:assembleDebug
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
python3 scripts/validate_refresh_recovery.py emulator-5556
```

The driver is restricted to an emulator and a debuggable package. It uses a
DEMO-labeled synthetic account, a typed NETWORK/SERVER/429 debug source, the
actual Compose Refresh button, the service's generation-checked action, and
real Android preference persistence. It checks recovery before transient
backoff expiry, preservation of a last reading, provider blocking across
process recreation plus explicit Resume, legacy deadline migration, and the
visible provider explanation. It writes only source-free counts/statuses and
booleans to [the native evidence](evidence/api36-refresh-0.19.1.json) after all
seven checks pass. The debug source/fixture is absent from release builds.

The signed phone/watch manifests under `release/` identify the exact source
commit, APK checksums, matching certificate, clean unit-test and lint results.
The phone upgrade uses the same application ID and durable personal signer;
install it over the existing app. The watch runtime is unchanged, so the
previous same-signer watch APK can remain installed.

## Limits

The native fixture simulates a DNS/network error category; it does not change
Android's DNS resolver or contact a live follower account. The owner's Galaxy
S25 Ultra DNS recovery and physical-device behavior remain unverified for this
patch. Existing screen-off, battery, Samsung presentation, and paired-watch
qualification limits still apply. Reproduce the resolved-DNS case with this
upgrade and report the new connection message if it fails.
