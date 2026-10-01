# Glance 0.19.0 watch verification

Target reported by owner: Galaxy Watch7 / One UI Watch 8.0 / Wear OS 6.
This report is a work-in-progress checklist until exact release evidence exists.

| Check | Result / evidence |
|---|---|
| Phone projection and sharing-store tests | Pass at `809aa06`: 151 phone unit tests, zero failures/errors/skips; includes 13 new watch projection/grant cases |
| Watch cache/format/publication tests | Pending watch module integration |
| Both debug APKs compile | Phone debug APK passes at `809aa06`; watch and final integrated build pending |
| Unit/lint/clean signed builds | Pending final integrated source |
| Phone same-key upgrade | Pending |
| Native Wear OS app on API 36 | Pending |
| Text complication service and preview | Pending |
| Real paired phone/watch Data Layer | Pending |
| Revocation, late revisions, Stop/person/sign-out | Pending |
| Notification opt-in, disabled permission, silent update/expiry | Pending |
| Galaxy Watch7 face / One UI Watch rendering | Not run; owner device required |
| Off-wrist / ambient / disconnected physical timing | Not run; owner device required |
| Watch overnight / battery impact | Not run; physical measurement required |

Synthetic emulator evidence does not establish physical Samsung behavior.
