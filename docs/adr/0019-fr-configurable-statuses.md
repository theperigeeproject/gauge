# FR 6: Configurable Statuses

Supersedes the fixed enum sets in [FR 2](0015-fr-statuses.md). Statuses are runtime data,
editable by admins, with a UI.

## Model

A status is a row in `status_definitions`:

| Field | Meaning |
|---|---|
| `code` | What lands in `issues.status` / `tasklists.status` / `tasks.status`. `A-Z 0-9 _`, 2–32 chars, unique per level. |
| `label` | Display text. |
| `level` | `ISSUE`, `TASKLIST`, `TASK` — three independent sets. |
| `category` | `ACTIVE`, `FINISHED`, `PARKED`. **This is the behavioural contract.** |
| `rank` | Sort order. Lower first; ties break by code. |
| `color` | `#rrggbb`, used for pills and counters. |
| `showByDefault` | Whether the status is ticked in the project issue list filter. |
| `builtin` | Seeded statuses are protected from deletion. |

### Why the category exists

A bare list of strings would silently break project analysis. Analysis needs to know three
things that a code name cannot express once it is user-defined:

- **ACTIVE** — being worked on. Bubbles a priority; counts as unfinished.
- **FINISHED** — done. Satisfies a subtree; ranks above PARKED.
- **PARKED** — terminal but not done (parked, canceled, failed). Never bubbles a priority,
  never displaces FINISHED.

Every rule from FR 2 and issue 00001432 is expressed through the category, so renaming or
adding a status cannot change behaviour by accident.

## Rules

- Every entity is created in the built-in `TODO` status. `TODO` cannot be renamed, deleted,
  or moved out of `ACTIVE` — otherwise nothing could be created.
- A level must always keep at least one `ACTIVE` status.
- Renaming a code rewrites all existing rows at that level in the same transaction.
- Deleting a status that is in use returns `409 STATUS_IN_USE` unless `?reassignTo=<code>`
  is supplied, in which case affected rows are rewritten to the target first.
- Unknown codes on read are treated as `PARKED` (ignored, never counted as finished) and
  logged — a deleted status can never resurrect a priority.
- Unknown codes on write are rejected with `400 INVALID_STATUS` naming the valid set.

## Surfaces

| Endpoint | Access | Purpose |
|---|---|---|
| `GET /api/statuses` | any authenticated caller | all three levels, keyed by level |
| `GET /api/statuses?level=ISSUE` | any authenticated caller | one level, ordered by rank |
| `POST /api/admin/statuses` | admin | create |
| `PATCH /api/admin/statuses/{id}` | admin | update; `code` renames and rewrites rows |
| `DELETE /api/admin/statuses/{id}?reassignTo=` | admin | delete, optionally reassigning |
| `GET /ui/admin/statuses` | admin | management page |

The read endpoint exists so UI and LLM clients never have to guess which codes are legal.

## Seeded defaults

Colours reproduce the pre-feature palette, so nothing changes visually on upgrade.

| Level | Statuses |
|---|---|
| ISSUE | `TODO`(ACTIVE) `DOING`(ACTIVE) `DONE`(FINISHED, hidden by default) `INACTIVE`(PARKED) |
| TASKLIST | `TODO` `DOING` `DONE` `CANCELED`(PARKED) |
| TASK | `TODO` `DOING` `DONE` `FAILED`(PARKED) `CANCELED`(PARKED) |

## Consequences

- `Issue`, `Tasklist`, `Task` expose `status()` as a `String`; the enums survive only as
  seed data in `StatusDefaults`.
- `ProjectAnalysisResult` reports counts as `issueCounts` / `taskCounts` maps keyed by code.
  The old flat fields (`issueTodo`, …) remain as a compatibility shim filled from the
  built-in codes.
- UI reads colours, labels and ordering from the registry; no template hardcodes a pill class.
- The project tree shows one issue column per configured ISSUE status, and task columns for
  ACTIVE task statuses only (showing every task status would make the table unusable).
- Adding many statuses widens the tree table. That is the price of configurability; there is
  no column-count cap.
