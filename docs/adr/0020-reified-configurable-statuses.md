# ADR 20: Reified, Configurable Statuses

## Status
Accepted

Issue 0000186F. Tasklist 00001A5A. Supersedes ADR 15 for the storage model; ADR 15's
semantics survive as seed data, not as enums.

Revised three times before first implementation. All three revisions are recorded rather than
edited away, because each one is a decision someone will otherwise re-propose:

1. **IDs** — status, progress-level and attitude ids became **internal ints outside `IdProvider`**
   (§2). Earlier drafts gave them hex-8 IDs, which was wrong for reasons recorded there.
2. **Schema creation** — the packaged `database.generation` becomes **`update`** and Hibernate creates
   the tables (§5). Earlier drafts had a `CREATE TABLE` bean, then a startup guard that refused to
   boot on a missing table. Both are gone.
3. **Entity naming** — the status entities are **`…StatusDef`**, not plain `…Status` (Consequences).
   An earlier draft claimed plain names were worth a sub-package; the ambiguity they create across
   three increments is worse than a suffix.

## Context

Statuses are enums today: `IssueStatus`, `TasklistStatus`, `TaskStatus` in
`pl.perigee.gauge.domain`. Three fixed sets, compiled in, referenced by `switch` statements in
`IssueService` (sorting), `ProjectAnalysisService` (counting, bubbled priority), `InputParser`
(parsing) and the Thymeleaf templates (CSS classes `pill-todo`, `--inactive`, …).

That model is at its end. It cannot express a fourth status, a renamed status, a per-level
property, or a colour without a code change and a redeploy. The next two planned features —
archive and trashbin (000001BA) — cannot be built on it at all without inventing parallel
mechanisms: there is no way to say "this state means: keep the row, hide it everywhere, do not
count it". `hidden`/`hiddenByDefault` would be a filter, not an exclusion, and a trashed project
would keep inflating its parent's counters.

So statuses become **data**: four configurable enum tables, plus two fixed lookup tables that
supply the axes.

## Decision

### 1. Statuses are rows, one table per level

Four independent tables — `project_statuses`, `issue_statuses`, `tasklist_statuses`,
`task_statuses`. Not one table with a level discriminator: the whole point is that one level can
grow properties the others do not have, and a discriminator column turns that into a
nullable-property swamp.

Every status row carries exactly:

| Column | Type | Meaning |
|---|---|---|
| `id` | `int`, DB identity — **internal only** | the referent — what entities store (§2) |
| `code` | `varchar(40)` | the human name. Unique per table. This **is** the label |
| `precedenceValue` | `int` | display / sort order, ascending. Unique per table |
| `color` | `varchar(20)` | display colour |
| `progressLevel` | `varchar(20)` → `progress_levels` | where in the workflow |
| `attitudeType` | `varchar(20)` → `attitude_types` | how it is treated |

`hiddenByDefault`, `excluded` and `builtin` were considered and dropped: the first is derivable
from `progressLevel` (§6), the second is exactly what `attitudeType` is for, and the third is
replaced by explicit referential handling on removal (§10).

There is no `label` column distinct from `code`. Code is what the surface speaks (§4), so a
second human name would be a second naming rule — the failure ADR 18 already documented once.

### 2. Reference by an internal int ID, never by code — and never across the surface

Entities store the status **id**. Renaming a status is therefore free: no row rewriting, no
in-use rename machinery, no chance of a rename racing a write.

`code` keeps no referential weight. It is a display slug and a seed identifier. Nothing in the
read or write path may join on it.

**Status, progress-level and attitude IDs are internal.** They exist between the persistence layer
and the status service, and nowhere else. They are therefore:

- **Plain auto-increment ints**, not hex-8 via `IdProvider` (ADR 5). ADR 5's format exists so that
  a human or an LLM can read and quote an ID reliably in a context window. These IDs are never
  read or quoted by anyone, so they earn nothing from it.
- **Outside `IdProvider` entirely** — not in its startup scan, never affecting the hex-8 counter.
  Mixing internal surrogate keys into a globally sequential business-ID counter would couple two
  concerns that have no reason to know about each other.
- **Absent from every external surface**: no API field, no UI element, no URL segment, no query
  parameter, no error message body. A caller never learns a status's id, so a caller can never
  depend on one. The wire identity of a status is its **code**.

**The service layer is agnostic to the ID's type.** Its contract speaks codes; ints do not appear
in a method signature that a resource, a template or a test outside the status package can see.
Swapping the surrogate (int to long, int to a composite, DB-generated to assigned) is an internal
change with no caller impact. This is the actual reason to keep them out of the contract — not
tidiness.

Consequences:
- A status's `code` can be renamed at any time, and the only thing that changes is what the API
  returns.
- Operations that must name a status — status removal's `migrate` target (§10) — name it by
  **code**, resolved inside the status service.
- No DTO carries a status id. The surface DTO carries the code (§4); the persistence entity carries
  the int. They are not the same shape and must not be conflated.

The lookup tables are the deliberate exception to "reference by id". `progress_levels` and
`attitude_types` are **fixed**, so their names are stable by definition and a status row references
them by `name` string (§3). Those tables carry an int id only because every table needs a primary
key; nothing references it. Statuses are the other case: user-editable, so they are referenced by
surrogate id and their codes are free to change.

### 3. Two fixed lookup tables, no surface API

`progress_levels` and `attitude_types`, both `name` + `ordinal`, both seeded if absent, both
cached at startup. No admin API, no surface API — these are closed enums expressed as tables so
that a status row can reference them without a Java enum in the middle.

**Progress levels** (`getProgressLevels()` → `HashMap<String, Integer>`, cached):

| name | ordinal |
|---|---|
| `NASCENT` | 0 |
| `CREATED` | 1 |
| `ONGOING` | 2 |
| `HALTED` | 3 |
| `FINISHED` | 4 |
| `FINALIZED` | 5 |

**Attitudes** (`getAttitudeTypes()` → `HashMap<String, Integer>`, cached):

| name | ordinal |
|---|---|
| `REGULAR` | 0 |
| `EXCLUDED` | 1 |
| `ARCHIVE` | 2 |
| `TRASH` | 3 |

Both are treated as fixed. A value arriving from the surface is validated by exact membership in
the cached map (`contains(input.toUpperCase())`) and stored as a plain string. No coercion, no
fallback.

### 4. The surface speaks codes; persistence speaks ids

Two DTOs, deliberately:

- **Persistence entity** (`Issue`, …) — field `status` is an `Integer` holding the status **id**
  (internal, §2). Column type `int`, nullable until migrated (§5).
- **Surface DTO** (`IssueDto`, …) — field `status` is a `String` holding the status **code**.

Translation between them happens in the status service and nowhere else. A resource or a template
that resolves a status by hand is a bug: it is the second naming rule that ADR 18 warned about,
and it will drift.

Input: code, case-insensitive, validated against the available cache. Unknown → **400
`INVALID_STATUS`** naming the valid codes for that level — same shape and same reasoning as ADR
18's description contract. Never silently dropped, never coerced, never a 500.

Null status on input is rejected. The column may be null (see §5), the API may not.

### 5. Migration of existing rows

Each entity gains a nullable `status` **int** column (the id) **alongside** the existing `status`
code value, which is renamed to `statusCode` and becomes legacy. Two separate columns, not a
rename-in-place — the id column must be able to start empty on an existing database:

- `statusCode` is nullable and is read **only** by the migration. Nothing else touches it.
- `status` is nullable in the schema — required, or the first startup after deploy cannot boot —
  but the API never accepts null.

At startup, for every entity row with `status IS NULL` and a non-null `statusCode`, the migration
resolves `statusCode` → the seeded status's id for that level and writes it.

**An unmatched code is an exception, and startup aborts.** Not a warning, not a parking
placeholder. The application seeds its own defaults, so every code in a database that was running
this application is one of them; an unmatched value means someone wrote to the table by hand and
the data is not what the code believes it is. The exception names the level, the offending value
and the row count.

This makes the migration a hard gate on boot, which is why the migration is to be tested
exhaustively — every level, every seeded code, mixed null/non-null, and the failure path.

Projects are the special case: they have no status today at all, so there is no code to migrate.
Existing project rows get the seeded `OPENED` status. Nulls are tolerated in the column; the API
rejects them.

**Schema is created by Hibernate: the packaged `database.generation` becomes `update`.** The six
new tables (four `status` tables plus both lookups) are declared only by their entities and get created on
first boot, exactly like every other table in the application. No `CREATE TABLE` bean, no hand-written
DDL, one description of the schema.

This reverses two earlier proposals, and the reversal is deliberate:

- **a `CREATE TABLE IF NOT EXISTS` bean** (the `FavoritesTableStartup` pattern) — rejected, because
  six hand-written tables would be a second description of the schema, kept in sync by eye;
- **a startup guard that refuses to boot when a table is missing** — rejected as unnecessary once
  Hibernate creates the tables itself. It could never fire in a correctly deployed instance, and a
  check that cannot fire is dead code.

Consequences that must not be lost:

- `update` is **additive, never destructive and never renaming**. It creates missing tables and adds
  missing columns. It will not narrow a column, drop one, or rename one.
- Therefore the §5 migration still needs explicit SQL for the one thing `update` cannot do: the old
  `status` code column has to become `statusCode` before the new `status` int column can occupy that
  name. `IssueDescriptionColumnStartup` is the precedent for raw DDL in a startup bean. The exact
  ordering against Hibernate's own schema update has to be settled empirically in that increment —
  not assumed.
- Test isolation is unaffected: `src/test/resources/application.properties` sets
  `drop-and-create` and outranks the packaged file, so tests keep recreating the schema.

### 6. Semantics are carried by progressLevel + attitudeType, never by code

The four locked INACTIVE rules from ADR 15 must survive a world where the status names are
editable. They are therefore expressed through the two axes only. **No code may branch on a
status code or name.**

| Concept | Definition |
|---|---|
| terminal | `progressLevel` ∈ {`FINISHED`, `FINALIZED`} |
| parked | `attitudeType` = `EXCLUDED` |
| active | not terminal and not parked |
| hidden in listings by default | terminal — the current `DONE` rule: filtered out of the issue list, **still counted** in analysis |

Bubbled priority (`BubbledPriority` stays a derived enum — it is a computed signal, not a status):

1. no issues at all → `NONE`
2. any active issue → highest active priority
3. no active, some terminal → `DONE`
4. no active, no terminal, only parked → `INACTIVE`

Parked statuses never bubble a priority and rank last. Ordering everywhere is
`precedenceValue` ascending. `INACTIVE` lands at `HALTED`/`EXCLUDED`, which reproduces today's
behaviour exactly.

### 7. Flags

| Property | Default | Effect |
|---|---|---|
| `gauge.status.extended-progress-levels` | `false` | when false, statuses at progress level `NASCENT` or `FINALIZED` are not accepted on status create/patch. Reads unaffected |
| `gauge.status.archive-enabled` | `true` | when false, statuses with attitude `ARCHIVE` cannot be retrieved, created or patched |
| `gauge.status.trash-enabled` | `false` | as above, for `TRASH` |

A disabled flag makes those statuses **unavailable** (§9) — unusable for authoring and absent from
the available cache. It does not retroactively hide entity rows; that is what §8 does, and it does
it unconditionally.

The property names are final: they carry the `gauge.` prefix and the `status.` group, following the
existing namespace convention (`gauge.issue.description.allow-long`, `gauge.ids.start-offset`).

### 8. ARCHIVE and TRASH are invisible to reads

Statuses with attitude `ARCHIVE` or `TRASH` are omitted from **all** entity reads — lists, gets,
search, analysis, tree counters. They keep their rows; the rows simply never surface.

The predicate lives in **one place**, as close to the persistence boundary as possible without
marrying H2 (a repository/service boundary filter, not a hand-written JPQL fragment repeated per
query). One predicate, applied everywhere; a second copy is how this breaks.

This is also the reason the two flags exist: a status nobody may write but which already holds
rows is a trap, so `enable.archive` defaults **true** (nothing is blocked today) and
`enable.trash` defaults **false** (trash is not built yet).

### 9. Status cache

Per level, build **two** caches:

- **all** — every status row
- **available** — the **flag-enabled** subset (§7): `ARCHIVE` only while `archive-enabled`,
  `TRASH` only while `trash-enabled`, `NASCENT`/`FINALIZED` only while
  `extended-progress-levels`.

`available` is defined by the flags **alone**, and that is deliberate. §7 says a flag "allows
*using* (retrieving, creating, patching)" statuses of that attitude — so excluding `ARCHIVE` and
`TRASH` from `available` unconditionally would make both flags pointless: an admin could never
create or select what the flag exists to enable. Keeping archived and trashed **entity rows** out
of listings is a different question, answered in §8.

Both caches are rebuilt **from scratch** on every status create, update and delete. No incremental
patching, no invalidation logic — the sets are tiny and a rebuild is trivially correct.

**A rebuild inside a transaction is not enough on its own.** The caches live on an
application-scoped bean and therefore survive a rollback, so a failed transaction would leave them
describing rows that no longer exist. Every write therefore also registers a transaction
synchronization that **drops** both caches if the transaction does not commit; the next read
rebuilds from the database. Rebuilding eagerly keeps same-transaction reads correct; invalidating
on rollback keeps everything after it honest.

### 10. Removal requires an explicit orphan policy

Deleting a status is a decision about the rows pointing at it, so it cannot be a bare `DELETE`.
Every removal is preceded by a full scan of the referencing entity table, then:

| Policy | Behaviour |
|---|---|
| `prevent` | refuse if at least one row references the status → `409 STATUS_IN_USE` |
| `remove` | cascade-delete the referencing rows, then the status |
| `migrate` | rewrite referencing rows to the supplied target status, then delete |

`migrate` requires the target up front, **named by code** (§2 — the wire never carries status ids),
and the target must exist in the **same level table** and not be the status being removed.

No policy supplied + references found → `409`. No policy + zero references → the delete proceeds,
because there is nothing to decide.

Invariant: every status table must retain at least one available status. An empty table makes
creating that entity level impossible, and the failure would appear far from its cause.

### 11. Admin surface

One resource with a level path segment — `/api/admin/statuses/{level}`, `level` ∈
`project | issue | tasklist | task`. Four separate resources would be four copies of the same
CRUD, and the four entities differ in *data*, not in *operations*. The level segment also keeps
the level-to-table mapping in one switch instead of in routing.

Every admin method checks `isAdmin()` as its **first statement** (ADR 19). `@RolesAllowed` is
banned and fails open, so each endpoint needs a test proving both outcomes.

### 12. Analysis breaks its shape

`ProjectAnalysisResult` carries fixed fields today (`issueTodo`, `issueDoing`, `issueInactive`,
`issueDone`, `taskTodo`, `taskDoing`). With arbitrary statuses those names cannot survive.

Counts become a map keyed by status **code** (the surface speaks codes, §4), per level, plus
`subprojectCount` and `bubbledPriority`. The tree renders its counter columns **from the status
definitions in `precedenceValue` order**, so a newly created status appears automatically with a
zero, and no template edit is ever needed. If a new status cannot show up in the tree, the
feature is half-built.

No compatibility fields. ADR 17's field table is superseded; its bubbled-priority rules are not.

### 13. Seed data

| Level | code | progressLevel | attitude | precedence |
|---|---|---|---|---|
| project | `OPENED` | `ONGOING` | `REGULAR` | 0 |
| project | `CLOSED` | `ONGOING` | `REGULAR` | 1 |
| issue | `TODO` | `CREATED` | `REGULAR` | 0 |
| issue | `DOING` | `ONGOING` | `REGULAR` | 1 |
| issue | `DONE` | `FINISHED` | `REGULAR` | 2 |
| issue | `INACTIVE` | `HALTED` | `EXCLUDED` | 3 |
| tasklist | `TODO` | `CREATED` | `REGULAR` | 0 |
| tasklist | `DOING` | `ONGOING` | `REGULAR` | 1 |
| tasklist | `DONE` | `FINISHED` | `REGULAR` | 2 |
| tasklist | `CANCELED` | `FINALIZED` | `REGULAR` | 3 |
| task | `TODO` | `CREATED` | `REGULAR` | 0 |
| task | `DOING` | `ONGOING` | `REGULAR` | 1 |
| task | `DONE` | `FINISHED` | `REGULAR` | 2 |
| task | `FAILED` | `HALTED` | `REGULAR` | 3 |
| task | `CANCELED` | `FINALIZED` | `REGULAR` | 4 |

`CANCELED` and `FINALIZED` are terminal, so they inherit DONE's hidden-by-default rule. `FAILED`
is `HALTED` but `REGULAR` — a failed task is a blocker, not a parked one; it keeps bubbling.

Colours come from today's palette so nothing changes visually on day one. The default status for
a newly created entity is the **lowest `precedenceValue` among available statuses** of its level —
which the seeds make `OPENED` for projects and `TODO` for everything else.

Seeding is idempotent by `code` per table, uses `@Startup` + `@PostConstruct` +
`QuarkusTransaction.run` (not `@Observes StartupEvent`, per the 0000055D lesson), and never
rewrites an existing row's editable properties.

## Consequences

- **ADR 15 is superseded for storage.** Its per-level status sets and its "all entities start in
  TODO" rule survive as seed rows and as the precedence ordering. Its four INACTIVE rules survive
  as §6 definitions. Nothing in it should be read as a schema statement any more.
- **ADR 17's field table is superseded.** Its bubbled-priority resolution is unchanged in meaning
  and now expressed through progress/attitude.
- **The `…Status` enums are deleted, not renamed.** The entities are `…StatusDef` and live beside
  them; `ProjectStatusDef` is new. Seed codes are string constants in the seeder.
- **`switch` statements on status die.** `IssueService.statusOrder`, the counting switch in
  `ProjectAnalysisService`, `InputParser.issueStatus` and the `pill-*` CSS classes all go away,
  replaced by `precedenceValue`, the counts map, cache lookup, and data-driven pill styling. This
  is the "almost no place is untouched" part.
- **Two DTOs per entity.** The surface DTO is not the entity. Jackson no longer serialises an
  entity directly for status-bearing types.
- **A second, quieter identity model exists.** Business entities keep hex-8 `IdProvider` ids
  (ADR 5); statuses and the two lookup tables use internal ints that never surface. ADR 5 has been
  given a scope note recording the split. The rule to remember: if an id can be read by a caller, it
  is hex-8; if it cannot, it is an int and it stays inside.
- **`IdProvider`'s startup scan is unchanged.** An earlier draft of this ADR would have added the
  four status tables to the scan. With int ids that is unnecessary — the scan still covers exactly
  the four business tables, and no counter interaction exists.
- **Rename is free, deletion is not.** Renaming a status touches one row. Removing one can touch
  every row in a table, hence §10.
- **Startup can refuse to boot on unmatched data.** An unmatched `statusCode` is fatal (§5):
  continuing would mean silently picking a status the code does not actually understand. A missing
  table is *not* fatal, because Hibernate creates it.
- **Status entities carry a `Def` suffix: `IssueStatusDef`, `ProjectStatusDef`, `TasklistStatusDef`,
  `TaskStatusDef`** (plus `ProgressLevelDef` and `AttitudeTypeDef` for the lookups, and an unsuffixed
  `StatusBase` as the mapped superclass). The reason is the opposite of the earlier draft's: plain
  names would collide with the same-named enums for three increments, forcing fully-qualified
  references and aliasing wherever both appear — and that ambiguity is a bug farm, which is worse
  than a suffix. `Def` is short for *definition*, which is also the accurate reading: these rows
  define what a status is.
- **The suffix dies with the enums.** When the enums are deleted in increment 4 nothing forces a
  rename, but nothing forbids it either: dropping `Def` at that point is a mechanical rename, and if
  it is ever done it happens **after** the enums are gone, never before.
- **Templates are not covered by `mvn compile`.** The tree's counter columns are generated from
  status data, so a broken template is only visible on a real page hit. Verification needs the app
  running, not just a green build.
- **Version:** 2.2.1 → **2.3.0** when the increment lands.
- **Verification gate:** the full suite green **and** `data/` empty afterwards. The emptiness is
  the only proof that the 00001878 config leak has not returned.

## Deferred, deliberately

- **A per-status "step trigger" axis.** Considered and dropped — it would push the model toward
  BPMN-grade workflow precision, which this tracker does not want. Progress and attitude stay the
  only two axes. If it is ever needed it is a new column and a new ADR, not a reinterpretation.
- **Archive / trash UI.** The substrate is §8; the views, restore and purge are 000001BA and are
  not built here. The frontend stays blind to which attitude means what — exclusion is applied in
  one place, not per page.
- **Per-project permissions** (0000018E) remain untouched.
- **No `builtin` flag.** Protection is referential and explicit; if a future requirement needs
  "this status may never be deleted even when unused", that is a new column and a new ADR.
