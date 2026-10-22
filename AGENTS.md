# Working on Glance

Resume the existing app; do not bootstrap it again. Read
[PONDEROSA-START-HERE.md](PONDEROSA-START-HERE.md), the current guides it names,
and the implementation addendum in [GLANCE-ANDROID-SPEC.md](GLANCE-ANDROID-SPEC.md).
Inspect Git status before editing and preserve other writers' work.
Current explicit user instructions take precedence over historical defaults.

## Ponytail coding workflow

Use the smallest complete change that solves the requested problem. These rules
are adapted from [Ponytail](https://github.com/DietrichGebert/ponytail), pinned to
[09c434ab73ca43761c5d8b0c5d341926c2fba710](https://github.com/DietrichGebert/ponytail/tree/09c434ab73ca43761c5d8b0c5d341926c2fba710)
(`AGENTS.md` and `skills/ponytail/SKILL.md`). The upstream MIT notice is below.

- Read the affected code and trace its callers before choosing a fix. Include
  the necessary callers, tests, fixtures, configuration and docs in the scope.
- Reuse existing Glance helpers, components and conventions first. Prefer a
  standard Kotlin/Android API or an installed dependency before adding one.
- Avoid speculative features, wrappers, abstractions and configuration. Keep
  the existing architecture; favor readable code over clever brevity.
- Fix a shared root cause once rather than patching each caller. Preserve
  validation and error handling when moving or simplifying code.
- New nontrivial behavior needs a focused test or self-check. Run checks suited
  to the change; trivial instruction or documentation edits need no app rebuild.
- Comment only a reason the code cannot express. Record a deliberate shortcut's
  actual limit and the condition that requires revisiting it.
- Finish the whole authorized task. Simplicity never excuses dropping a user
  requirement, privacy protection, security check or accessibility behavior.
- Report the outcome concisely, including material unverified behavior and risk.

For Glance, preserve selected-person identity, source timestamps and freshness,
privacy grants, provider cooldowns, single-flight polling and signing continuity.
Keep credentials, patient content and signing keys out of source and diagnostics.
Use synthetic test data. Emulator/source checks cannot qualify Samsung rendering,
live-account behavior, overnight reliability or battery impact by inference.

## Upstream license

MIT License

Copyright (c) 2026 DietrichGebert

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
