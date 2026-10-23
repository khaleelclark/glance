# Ponytail audit fixes — 22 October 2026

Unreleased source changes based on `042852a1768ae86a7c14ecb07cceb6d8605dc47c`.
This PR does not replace the delivered 0.32 APKs, change the installed app,
change notification grouping, or add automatic Live Update re-promotion.

## Findings and fixes

| Finding | Resolution and focused coverage |
|---|---|
| Failed settings writes could abort Stop/Start or escape retry handling | Checked settings writes and controller-owned accepted intent/retry state. Failed Start stays off; Stop invalidates late work and stops in-process even on save failure; retry deadlines remain enforced in-process. Failing-storage controller tests cover Start, Stop and provider cooldown. |
| System Stop was marked handled before durable disable | `ExplicitSystemStop` hides output before recording success and leaves recovery pending on failure. Two ordering tests cover success/failure; a settings-cache regression requires a fresh commit on repeated Stop. |
| Manual Refresh just before an automatic slot caused two sequential calls | Running-session UI refresh uses the service coordinator. Completed manual attempts move the next automatic slot one full interval later. A fake-clock scheduler test covers the near-due case. |
| Provider cooldown was bypassed by sign-in, connection discovery and person changes | Every controller provider entry checks the active provider window. Person changes and repeated sign-in preserve it; explicit sign-out clears settings. Request-count regressions cover these paths; an overlapping successful poll also retains a newer connection-list cooldown. |
| A delayed connection list could cross account boundaries | Controller captures generation/account before session recovery and checks again after I/O. Activity cancels superseded account/list jobs and token-gates completions, including failures. Controller deferred-response tests cover late account results; a cooldown-blocked account switch still clears the prior account/display before returning. |
| Transient failure count leaked across identities | Reset the exponent at successful account/person boundaries and sign-out; keep provider cooldown independent. Fake-clock tests cover escalation and identity reset. |
| An old same-session delete intent could undo explicit restore | Persist dismissal plus a monotonic presentation epoch; include it in delete-intent identity/extras. Restore and final publication share the publication lock; callbacks require current session and epoch. Tests cover stale/current callbacks and both overlap orders. |
| Dismiss/restore saves ignored failure | Failed restore retains prior accepted epoch/suppression. Failed dismissal conservatively suppresses promotion in-process and reports the save failure. Preference-cache mutation tests cover both. |
| Alert evaluation used an obsolete eligible snapshot | Verify the current runtime, freshness, identity, numeric and special value, then evaluate/deliver the current display. Tests reject stopped/stale/corrected snapshots without posting or changing history/cooldowns. |
| Alert preference cleanup could crash sign-out | Catch cleanup failure, finish the sign-out UI transition and report retained alert settings/state alongside other cleanup failures. |
| Malformed authenticated history rows were silently dropped | Reject the entire history load and block append until explicit clear, using the existing load-failure path. Pure decode tests cover a mixed valid/null row and the size bound. |
| A different valid release key could replace an output | Check each signed APK against the established public certificate before replacing its output. Three synthetic signing-script tests include preservation of an existing release when a different certificate is returned. No signing key was accessed. |
| Unused UI machinery | Remove the unreferenced color wheel, color-picker conversions and graph transform method; retain preset selection and existing graph interactions. Repository references were checked. |

## Verification

Final source checks ran on 23 October 2026 against production/test source
`4c311fd143db4b6dd7b7aa76691f491a56976f9d`. Documentation changes after this pin do not change tested code.
All tests use synthetic data; no live provider request is needed.

| Check | Result |
|---|---|
| Phone JVM tests | 255 passed, zero failures/errors/skips |
| Watch JVM tests | 15 passed, zero failures/errors/skips |
| Signing-script tests | 3 passed; synthetic tool runner/files, no key access |
| Phone debug/release lint | Zero errors; 96 debug / 58 release warnings |
| Watch debug/release lint | Zero errors/warnings |
| Phone/watch debug assembly | Passed |
| Phone/watch unsigned release assembly | Passed |
| Changed documentation local links / diff whitespace | Passed |

Commands:

```sh
./gradlew :app:testDebugUnitTest :wear:testDebugUnitTest \
  :app:lintDebug :app:lintRelease :wear:lintDebug :wear:lintRelease \
  :app:assembleDebug :wear:assembleDebug :app:assembleRelease :wear:assembleRelease
python3 -m unittest discover -s scripts -p 'test_sign_release.py'
git diff --check
```

The notification specialist independently reviewed integration at `b820372` and
found no concrete notification/alert defect. Later source commits only change
the controller and its tests. Account/storage/signing review is still pending;
this PR remains a draft until that tracked result is integrated.

## Coverage boundaries and remaining risk

The JVM history test checks all-or-nothing decode policy, not Android `JSONObject`
enum/timestamp parsing, Keystore I/O or the encrypted-store clear/unblock sequence.
Activity cancellation/token checks and service-start/receiver wiring are compiled
and reviewed; no new instrumented UI or Android runtime test was run for this PR.
The sign-out alert-preference failure callback is reviewed/compiled, with no new
instrumented failure-injection test.

A failed durable Stop cannot guarantee that a later process will reload false;
the current process stops safely and reports this limitation. Likewise, a failed
dismissal save can be forgotten after process recreation. A provider cooldown
whose save fails remains enforced in-process; storage errors are surfaced.

No emulator, physical phone/watch, live account, audible delivery, Samsung icon
or promotion, overnight cadence or battery check qualifies this PR. No new
personal-signed APK is delivered, and main is not merged by this work.
