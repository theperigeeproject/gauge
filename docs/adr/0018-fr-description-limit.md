# FR 5: Issue Description Length Contract

## Scope
Issue descriptions have a single, enforced length limit. Both write paths — create and update —
apply the same threshold and fail the same way. A rejected write is never reported as success.

## Contract
| Property | Default | Effect |
|---|---|---|
| `gauge.issue.description.allow-long` | `false` | `false`: 4000-character cap. `true`: cap lifted to the database width, 1 000 000 characters. |

- Exceeding the limit returns **HTTP 400** with `{"error": "DESCRIPTION_TOO_LONG", "message": "<actual length>, limit <n>"}`.
  The message always names the limit in force.
- The same body shape and status code is used for `POST /api/projects/{id}/issues` and `PATCH /api/issues/{id}`.
- `4001` characters is rejected, `4000` is accepted. The toggle moves the threshold, it does not
  change the failure mode.

## Rationale
The cap protects the database from accidental bulk dumps (a pasted transcript in a description), while
some legitimate bodies — test plans, ADR-style writeups — exceed 4000 characters. A per-instance toggle
beats raising the limit for everyone, and beats the "create short, then patch the full text in" workaround.

## Implementation constraints
- Validation lives in `IssueService` (`create` and `patch`) through `IssueDescriptionPolicy`. There is
  exactly one choke point, so the two paths cannot diverge.
- `ValidationException` → `ValidationExceptionMapper` → 400. No database constraint and no Jackson
  error may ever surface as the user-visible failure, and neither may produce a 500.
- The `issues.description` column is `VARCHAR(1 000 000)`. `IssueDescriptionColumnStartup` widens
  databases created before this record existed; the column is never the thing that fails a request.
- Enum-ish request values (`priority`, `status`) are parsed by `InputParser`, which rejects an unknown
  value with a 400 naming the valid set. No coercion.

## Consequences
- `POST` no longer returns a 500 with a stack trace on a long description, and never reports success
  without creating a row.
- Clients gain a stable, machine-readable error code to branch on.
- The 1 000 000 hard limit is a real, reachable ceiling: with the toggle on, a 1 000 001-character
  description is still rejected with a 400.
