# 0000186F — Execution plan

How the reification of statuses actually gets built. Design is ADR 0020; raw owner input is
`docs/notes/0000186F-owner-input.md`. This file is about **order, gates and protocol**.

## Shape of the work

Seven increments. Each one leaves the app **bootable and the suite green**, and each one is a small
number of commits. The property that matters: at no point does the repo sit in a state where
"tests pass" only because a half-migrated path is unexercised.

| # | Increment | Behaviour change | Risky? |
|---|---|---|---|
| 1 | Status substrate: 4 status entities, 2 lookup entities, repositories, seeding; packaged DDL → `update` | none | no |
| 2 | Status service: caches, registry, code↔id translation, removal policies | none (unused) | no |
| 3 | Schema + migration: nullable `statusId` column, `NEEDS_MIGRATION` fallback, on-demand migration behind an admin endpoint | schema only, wire unchanged | **yes — irreversible** |
| 4 | Wire switchover: DTOs, InputParser, services speak codes | wire-compatible, internals change | yes |
| 5 | Analysis + UI: counts map, data-driven tree columns, status pills | **response shape breaks** | yes |
| 6 | Admin API: `/api/admin/statuses/{level}` CRUD, guards, both-outcome tests | new surface | no |
| 7 | Sweep: README, handoff, ADR index, version 2.3.0, `data/` verification | none | no |

Increments 1 and 2 are dead code by design — built, tested in isolation, wired to nothing. That is
what lets 3 and 4 be small.

## Why 3 and 4 are separate

Increment 3 adds a nullable `status` int column and fills it at startup, while every read path
still uses the old code string. Increment 4 flips the reads. Split because they fail differently:
if 3 is wrong, the data is wrong (and only a migration test catches it); if 4 is wrong, the
behaviour is wrong (and the existing 574 tests catch it). Doing both in one commit makes a red
test ambiguous between the two.

**Increment 3 is the one that must not be rushed.** It adds a nullable `statusId` column to four
tables, and fills it from the legacy code. It is the only increment that touches existing data.

Two things make it survivable, and both are revision 5 (ADR 0020):

- **It is not a boot-time gate.** A null `statusId` is a legal state that reads as
  `NEEDS_MIGRATION`, so an upgraded database is correct and fully visible before anything runs. The
  migration itself is an admin operation — `POST /api/admin/migrate-statuses`, or the dashboard
  button — and it reports unmatched legacy codes instead of aborting anything.
- **Nothing about it can run before the schema is finished.** Because it is on demand, it runs after
  boot completes, which removes the startup-ordering hazard entirely rather than proving it safe.

So the rehearsal is not "build a pre-migration database by hand". It is the idempotency and
reporting properties, asserted in the suite, plus the fact that the migration is not a startup bean
at all — a property a test can assert by reflection and cannot silently regress.


## Wire compatibility — read this before promising anything

The seeded codes are `TODO / DOING / DONE / INACTIVE` (and tasklist/task equivalents). So a client
sending `"status": "DOING"` keeps working through increments 3, 4 and 6 unchanged. **Increment 5
is the only breaking one**, and it breaks `GET /api/projects/analysis` by replacing the fixed
fields with a counts map.

That endpoint is consumed by the MCP tool server this tracker is driven through
(`tracker_analyze_projects`). Coordinate before 5 lands, or the assistant loses a tool mid-project.

## Protocol

**Per increment**

1. Branch state: work stays on `feat/0000186F`. No merges to `edge` mid-increment.
2. Plan the increment in its task description, ask for a nod on anything not already decided.
3. Implement, `./test.sh`, check the **test count** went up, not just `BUILD SUCCESS`.
4. `data/` must be empty afterwards. Non-empty means a test hit the file DB (00001878).
5. Commit in coherent units. One pathspec per `git add` — a space-joined list becomes a bogus path.
6. Push at the end of each increment. Nothing gets merged to `edge` until the whole chain is green.

**Ask, don't guess**

Stop and ask when: a decision is not in ADR 0020 or the source record; a test contradicts the ADR;
something needs a new dependency; or the change would touch a file the plan did not name.

Do not ask about: method names, package layout, test organisation, or anything ADR 0020 already
answered. Those are implementation detail and I will pick and state the pick.

**Verification gates — all four, or the increment is not done**

1. Full suite green, count increased, no new skips.
2. `data/` empty.
3. For any admin endpoint: a test proving **both** outcomes under an auth-enabled `@TestProfile`.
   A 403 under the default no-auth profile proves nothing (ADR 0019).
4. For anything touching a template: an actual page render. `mvn compile` does not parse Thymeleaf.

**Commit discipline**

- No amend, no reset in the toolset — a wrong message can only be corrected by a later commit.
- Nothing gets committed that leaves the suite red, including documentation increments.
- Tracker is updated at the end of each increment, not continuously.

## Dependency order, and what unblocks what

```
1 substrate ──> 2 service ──> 3 schema+migration ──> 4 wire ──> 5 analysis/UI ──> 6 admin
                                                                                     │
                                                       7 sweep <─────────────────────┘
```

- 5 depends on 3 and 4: counting needs ids to exist and codes to resolve.
- 6 depends on 2 (removal policies) and is independent of 5 — it can be pulled earlier if the
  admin surface is wanted sooner. It cannot come before 3, because it mutates status rows that
  entities then reference.
- 000001BA (trashbin) starts only after 6, and is not part of this issue.

## Known risks

| Risk | Mitigation |
|---|---|
| Migration leaves a row on a code nobody expected | The row stays on `NEEDS_MIGRATION` and the report names level, value and row count. Nothing aborts, nothing is guessed |
| `generation=update` now governs DDL for every table in production | It is additive only — cannot drop or retype. A careless entity field can still add an unwanted column, so schema changes get reviewed, not just committed |
| Nothing writes to entity tables before the schema exists | The migration is an admin operation, not a startup bean. Assumed ordering is replaced by construction |
| Template breakage invisible to `mvn compile` | A real page render is a gate, not an optional extra |
| Cache staleness after a status write | Rebuild from scratch; no invalidation logic to get wrong |
| Admin endpoint fails open (ADR 0019) | Both-outcome tests, every endpoint, no exceptions |
| Counting 5+ statuses makes the tree unreadable | Header ellipsis + horizontal scroll; accepted by the owner, flagged in ADR §12 |
| The assistant loses `tracker_analyze_projects` when 5 lands | Coordinate before 5 |

## Definition of done for the issue

- Statuses are rows in four tables; entities reference them by internal int id.
- The surface speaks codes only; no status id is reachable by a client.
- No `switch` on a status anywhere in the codebase.
- Adding a status through the admin API shows up in the tree with zero template edits.
- All four locked INACTIVE rules verified by tests expressed through progress + attitude.
- 574+ tests green, `data/` empty, version 2.3.0.
