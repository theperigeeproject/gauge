# ADR 18: Issue Description Length Contract

## Status
Accepted

## Context
The 4000-character cap on issue descriptions existed **only as a database column width**
(`@Column(length = 4000)`). No application-level check enforced it, so an over-long description
was not rejected — it failed at insert/update time and surfaced as **HTTP 500 with an H2 stack
trace** (`Value too long for column "DESCRIPTION CHARACTER VARYING(4000)"`) on both `POST` and
`PATCH`. To a client that only checks "did I get an exception" this looked like a swallowed
success.

A second, unrelated symptom shared the same cause: an unknown enum-ish value such as
`priority=MEDIUM` was rejected with an opaque Jackson body
(`{"objectName":"Class","attributeName":"priority","line":1,"column":13,"value":"MEDIUM"}`) that
named neither the field's valid values nor anything a caller could act on. In the query-string
paths the same problem inverted: unknown `?priority=` / `?status=` values were **silently dropped**
from the filter, so a query answered with a superset of what was asked for and looked correct.

Some legitimate bodies — test plans, ADR-style writeups — exceed 4000 characters, and the
workaround was to create short and patch the full text in afterwards. That is a design smell, not a
user error.

## Decision
**One contract, one choke point, one failure mode.**

- `IssueDescriptionPolicy` owns the threshold and reads `gauge.issue.description.allow-long`
  (default `false` → 4000; `true` → 1 000 000, the column width). Call sites never read the
  toggle themselves.
- Both write paths (`IssueService.create` and `IssueService.patch`) call `validate()` as their
  first statement, so threshold and failure mode cannot diverge.
- An over-long description raises `ValidationException("DESCRIPTION_TOO_LONG", ...)`, mapped by
  `ValidationExceptionMapper` to **400** `{"error": ..., "message": ...}`. The message names the
  actual length, the limit in force, and (when the cap is the default) how to raise it.
- `issues.description` is `VARCHAR(1 000 000)`. `IssueDescriptionColumnStartup` ALTERs existing
  databases on boot, because Hibernate `update` will not reliably widen a column. The column is
  therefore never the thing that fails a request.
- `InputParser` rejects unknown `priority` / `status` values in the **query-string** paths with a
  400 that enumerates the valid set. Values are matched case-insensitively and trimmed; nothing is
  coerced.

**Validation runs before the lookup in `patch()`.** A rejected payload never touches the database,
and a non-existent issue with an over-long description answers `400`, not `404`. Accepted
deliberately: the payload is wrong regardless of whether the target exists, and answering `400`
first avoids using validation errors as an existence oracle.

## Consequences
- The 500 with a stack trace is gone from both write paths; a rejected create leaves no row.
- Clients gain a stable machine-readable code (`DESCRIPTION_TOO_LONG`, `INVALID_PRIORITY`,
  `INVALID_STATUS`) to branch on.
- The cap is a real, reachable ceiling: with the toggle on, 1 000 001 characters is still a 400.
- `4000` is accepted and `4001` rejected; the toggle moves the threshold, it never changes the
  failure mode.
- Anything that catches `ValidationException` and maps it to 400 must stay registered. Removing
  the mapper would turn every validation failure back into a 500.
- The valid-value lists in error messages come from the enum's own `values()`, so they cannot
  drift from the real set. They are enumerated in **declaration order**, which for `IssueStatus`
  is `TODO, DOING, INACTIVE, DONE` — not display order. That is fine for an error message; tests
  must not assume display order.
- Request-body enum errors still carry a Jackson-shaped response rather than this contract's
  message shape, because the DTOs declare typed enums and rejection happens before the resource
  is entered. The status code is already correctly 400. Unifying that body is separate work.

## Provenance
The behaviour was settled in a parallel line of this repository (`toolchain/gauge`, commit
`affd973`), which was never merged. This implementation was **recreated** against the current
`pl.perigee` tree rather than cherry-picked, so the code and the history here are coherent.
