# Glance 0.30 independent source review

Presentation review pinned
`bea5cdfade06fca212ab1860cdb6b455490a56fd` over released
`465d9cd23f50209f7da44e34a2693da6ba7bf221`. All production reads used git show;
diff checking passed and no HIGH/MEDIUM regression or new concrete LOW was found.

The richer green remains behind the unchanged fresh semantic-band gate, with
neutral old/absent/invalid states. Dark text has about 8:1 green contrast and
>=7:1 for all bands. The 176dp minimum, larger fonts, wrapping, growth and
independent portrait/landscape scrolling are source-coherent. Source values,
time, thresholds, freshness and accessible range/trend meaning are unchanged.
History tests prove timestamp rather than value deduplication and no artificial
gap filling; they do not establish why the owner's device has missing minutes.

The separate protocol investigation found a concrete pre-existing import bug:
minimal graph rows were rejected for missing current-only fields, and valid
backfill beyond 300 rows was truncated. Root's corrective production commit is
`660775fc4fdfc62588792e3d3786ce535a3247c7`. The bounded review found no
HIGH/MEDIUM regression and one LOW: adapter deduplication could discard a full
provider display value if a minimal same-time/same-canonical row followed it
within the first response. The store merge could not recover metadata already
discarded by the adapter. Root now shares the metadata-preservation rule with
adapter deduplication. New regression checks cover both row orders, exact
provider mmol/L display precision, missing trends and canonical corrections.
The correction is pinned at `3aefcbd34167459f4ffa9d7227d94f10d4b29a55`.
All 236 phone tests, debug assembly and zero debug lint errors pass root-owned
checks. The bounded correction review remains tracked; the finding is not yet claimed
independently closed. The UI review above does not cover the history changes.
See protocol.md and reading-card-030-validation.md for the remaining boundaries.

The reviewer performed no edits, build/test/lint, emulator/install, signing,
credential or push actions. Runtime and release qualification remain root-owned.
