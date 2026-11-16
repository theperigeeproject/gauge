# 0000186F — Owner's source input, verbatim

Raw record of what the owner said while the design of ADR 0020 was being settled. **This file is
the source; ADR 0020 is the interpretation.** Where the two differ, this file wins and the ADR is
wrong.

Kept because an ADR paraphrases, and paraphrase is where decisions get quietly softened. Nothing
here is edited for correctness — statements later retracted are kept and marked, because the
retraction is itself a decision. Agent questions are included whenever an answer is a bare "yes",
otherwise the answer is undecodable.

Session: 2026-11. Issue 0000186F, tasklist 00001A5A.

---

## Input 1 — wire format, naming, properties

> store id; accept code only. display code only. (code is string). these are supposed to be
> configurable enums.
>
> Issue gets String field status, and push/pull of an issue translates code into ID.
>
> naming: String "name", or code, whatever. just use a plain string and keep a service layer
> enforcing database access.
>
> properties: just id, code, precedencevalue, (whats label?), color, progressLevel (enum, your
> category), attitudeType (enum, orthogonal to progressLevel). drop hiddenbydefault, excluded,
> builtin. gonna talk about two enums later.
>
> migration of existing rows: if name/code matches the enum, all entities, at startup. old column
> needs to be nullable and used only when migrating from now on.

Notes:
- "push/pull of an issue translates code into ID" — the service layer is the only translator.
- `label` was questioned by the owner and later answered: **code = label** (input 2, item 3).
- The `String status` in this input was **superseded** by input 5 (ids are internal ints) plus
  input 2 (two DTOs). What survives: the field is a plain `String` **on the surface**.

## Input 2 — corrections and the two axes

> corrections:
> status per (entity type = 4 different tables) has fields: "id, code, precedenceValue, color,
> progressLevel, attitudeType".
>
> DB stores entites (e.g. issue) with ID. surface accepts and returns "issue" with string field
> "status". (this requires two dtos, one matching the surfae, one matching persistence).

Answers to four agent questions, restated so the bare "yes" stays decodable:

- **Q: should the old `status` column be renamed `statusCode`, with the id in a new `status`
  column?** → "1. statusCode, yes." *(later corrected — see input 3)*
- **Q: unmatched code at startup — WARN and leave null, or abort?** → "2. unmatched code at
  startup should not happen in practice. throw an exception. we seed the app with default values
  and nothing should break. migration should be tested exhaustively."
- **Q: keep a separate `label` column, or is code the label?** → "3. code = label."
- **Q: is DONE-hidden-by-default derived from progress level?** → "4. progress and attitude i am
  discuss below."

> progress level ~~ a workflow step, would be a high-level kanban column.
> progress level is a fixed enum with values: "NASCENT / CREATED / ONGOING / HALTED / FINISHED /
> FINALIZED" and an explicit, fixed ordinal (int).
>
> best if progress level was a separate table, seeded if it doesnt exist. no surface api for this
> table. reading from this table: getProgressLevels, returns a hashmap of <level, ordinal>, cached
> two times at startup.
>
> when accepting a progress level at the surface: plain String, ensure level exists
> (progressLevels.contains(input.toUpperCase())), store as plain String.
>
> an app prop switch "enable.extended.progress.levels", false by default. (when switched off,
> return normally but do not accept new progress levels: NASCENT, FINALIZED).
>
> - statuses which are FINISHED or FINALIZED should be treated just like status DONE is now (aka
>   not shown by default).

> attitudeType = a fixed enum (just like progress level - separate table, seeded at startup, name +
> ordinal, cached at startup). values: REGULAR, EXCLUDED, ARCHIVE, TRASH
>
> an app prop switch for "enable.archive", true by default, which allows using (retriving,
> creating, patching) statuses with attitude ARCHIVE.
>
> an app prop switch for "enable.trash", false by default, which allows using (retriving,
> creating, patching) statuses with attitude TRASH.
>
> on startup, two copies of a cache for statuses should be created: one for all statuses
> registered, another one for currently available statuses. those caches should be updated (rebuild
> from scratch, worthless optimization) every time a status is created, modified or removed.
>
> removing statuses: require orphan migration policy. such policy can be either "remove",
> "prevent", "migrate". either way, a full table scan before removal is needed.
> - remove = entries with given status are cascade removed.
> - prevent = blocks status removal if at least one entry with status is found.
> - migrate = migrate the status to a different one. a different, existing status needs to be
>   chosen given before the scan.

## Input 3 — two columns, projects, ARCHIVE/TRASH visibility, the mapping

> hmm.
> - status is not renamed to statusCode, those are 2 separate columns.
> 1. yes 2. yes 3. yes.
> 4. projects are somewhat special. i vote they should have statuses OPENED and CLOSED (all those
> are ONGOING, REGULAR). migration to OPENED. default value OPENED. all nulls (or migration will
> break). just dont accept nulls in API.
>
> on the attitudes: EXCLUDED behaves like INACTIVE now. ARCHIVE and TRASH are - for now - omitted
> in all reads on the DB layer. (maybe service layer, but as close as possible without marrying a
> db)
>
> just to be sure: current statuses map onto new, seeded:
> - TODO (CREATED, REGULAR)
> - DOING (ONGOING, REGULAR)
> - DONE (FINISHED, REGULAR)
> - INACTIVE (HALTED, WAITING)

The "1. yes 2. yes 3. yes" answers restated:

1. **Progress ordinals** NASCENT 0, CREATED 1, ONGOING 2, HALTED 3, FINISHED 4, FINALIZED 5 → yes.
2. **Analysis counts keyed by status code** (surface speaks codes) → yes.
3. **`precedenceValue` int, unique per table, ascending for sort and tree columns** → yes.

**`WAITING` is a typo** — corrected in input 4. Do not implement it.

## Input 4 — the WAITING retraction

> wait, this is my typo. INACTIVE = HALTED, EXCLUDED. treat it like WAITING never happened. i was
> debating using such attitude, or adding an axis of "step trigger", but this stays implicit for
> now. we are not bpmn level precise. its option B i guess.

"Option B" was the agent's proposal: attitude set = `REGULAR, EXCLUDED, ARCHIVE, TRASH`, with
INACTIVE mapping to `EXCLUDED`.

Recorded as **deferred, not rejected**: a "step trigger" axis was considered and dropped for now.
The owner's reason is "we are not bpmn level precise" — this tracker does not want workflow-engine
precision. If it ever returns, it is a new column and a new ADR.

## Input 5 — ID scope

> so, statuses', progress levels' and attitudes' IDs exist only between the backend and the service
> layer. (this deserves an adr if there aint one already).
>
> they should be excluded from the scope of the id provider; they can use plain ints.
>
> interaction with the service layer should be agnostic to types of those IDs.

The ADR question is answered: **ADR 0005 needed a scope note** (hex-8 is for identity a caller
reads and quotes), and the split is recorded in ADR 0020 §2. No new ADR was written, because two
existing ones already own the subject.

---

## Decisions the owner made that are easy to lose

Collected here because each one is a sentence in the inputs above and a paragraph in the ADR:

1. **Statuses are configurable enums** — data, not Java enums.
2. **Four separate tables**, one per entity type. Not one table with a level discriminator.
3. **Entities store the id; the surface speaks the code.** Two DTOs per entity.
4. **`code` is the label.** There is no second human name.
5. **Properties are exactly** `id, code, precedenceValue, color, progressLevel, attitudeType`.
   `hiddenByDefault`, `excluded` and `builtin` are **dropped by name**, not renamed.
6. **Two fixed lookup tables**, seeded if absent, no surface API, cached, read as
   `<name, ordinal>` maps.
7. **`precedenceValue`** is the only ordering mechanism. No status-name branching anywhere.
8. **`enable.extended.progress.levels` defaults false** and blocks *accepting* NASCENT/FINALIZED.
9. **`enable.archive` defaults true; `enable.trash` defaults false.** Both gate retrieve/create/
   patch of statuses carrying that attitude.
10. **Two status caches** (all, available), rebuilt from scratch on every status write.
11. **Removal always takes an orphan policy**: `remove` / `prevent` / `migrate`, full scan first,
    and `migrate` needs its target chosen *before* the scan.
12. **ARCHIVE and TRASH are omitted from all reads** at the DB layer, or as close to it as possible
    without binding to H2.
13. **FINISHED and FINALIZED are treated exactly as DONE is today** — not shown by default.
14. **EXCLUDED behaves as INACTIVE does today.**
15. **Projects get OPENED and CLOSED**, both ONGOING/REGULAR; existing rows migrate to OPENED; the
    column tolerates null, the API does not.
16. **Old code column stays nullable and is used only for migration.**
17. **Unmatched code at startup throws.** No WARN, no parking value.
18. **Status/lookup ids are plain ints, out of `IdProvider`'s scope, never on the wire**, and the
    service contract is agnostic to their type.
19. **No "step trigger" axis.** Explicitly not BPMN-precise.
