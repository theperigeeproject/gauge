# FR 2: Status Lifecycles for Entities

> **Superseded by [FR 6: Configurable Statuses](0019-fr-configurable-statuses.md).**
> Statuses are now runtime data, configurable per level by an admin. The semantics below
> still describe the *seeded defaults* and remain the contract for what a status in each
> category must mean. The fixed enum sets are gone.

## Scope
Each entity level has an appropriate status enum reflecting its role in the model.

## Statuses

### IssueStatus
`TODO → DOING → DONE, INACTIVE`
- Simple linear progression. Issues represent concerns; they're either pending, active, resolved, or parked.
- `INACTIVE` — parked. The issue is not being worked on and is not finished. It demands no attention: it never bubbles a priority, and it is the least important status — it ranks below `DONE` and never displaces it.
- Status ordering (sorting, filtering, analysis columns) is `TODO, DOING, DONE, INACTIVE` — active work first, finished next, parked last.

### TasklistStatus
`TODO → DOING → DONE, CANCELED`
- Adds `CANCELED` — tasklists represent workstreams that can become irrelevant without the issue being done.

### TaskStatus
`TODO → DOING → DONE, FAILED, CANCELED`
- Adds `FAILED` — tasks are atomic operations that can fail independently. A failed task doesn't cancel the tasklist; it signals a blocker.

## Details
- All entities start in `TODO` on creation
- Status transitions are unrestricted (no state machine enforcement)
- Status is mutable via dedicated endpoints (`PATCH /{entity}/{id}/status`)
