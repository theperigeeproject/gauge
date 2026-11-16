# FR 4: Recursive Project Analysis with Bubbled Priority

## Status
Partially superseded by **ADR 20**. The field table below is gone — counts are keyed by status
code once statuses are configurable. The bubbled-priority rules are unchanged in meaning and are
now expressed through `progressLevel` + `attitudeType` instead of status names.

## Scope
`GET /api/projects/analysis` returns computed stats for every project, recursively aggregating across subproject trees.

## Output per Project

| Field | Description |
|---|---|
| `issueTodo` | Count of TODO issues (direct + all descendants) |
| `issueDoing` | Count of DOING issues (direct + all descendants) |
| `issueInactive` | Count of INACTIVE issues (direct + all descendants) |
| `issueDone` | Count of DONE issues (direct + all descendants) |
| `subprojectCount` | Total descendant subprojects |
| `taskTodo` | Count of TODO tasks (direct + all descendants) |
| `taskDoing` | Count of DOING tasks (direct + all descendants) |
| `bubbledPriority` | Highest active priority across the entire subtree |

## Bubbled Priority

- `NONE` — no issues at all
- `LOW` — highest active priority is LOW
- `NORMAL` — highest active priority is NORMAL
- `HIGH` — highest active priority is HIGH
- `DONE` — nothing active, but at least one issue is done
- `INACTIVE` — nothing active and nothing done; only parked issues remain

"Active" means `TODO` or `DOING`. `INACTIVE` issues are parked: they are excluded from priority bubbling entirely (a parked `HIGH` issue does not make a project `HIGH`). `INACTIVE` is the least important resolved state — it ranks below `DONE` and never displaces it.

Bubbled priority surfaces the most urgent thing anywhere in a project tree, enabling triage at any level of the hierarchy.
