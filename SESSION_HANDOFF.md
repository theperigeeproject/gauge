# Gauge — Session Handoff

Warm-up table of contents. History lives in git + the tracker, not here.

## What it is

Gauge ("ride shotgun with your daemons") is a singular-tenant work tracker for one human + one LLM daemon.
Hierarchy: `Project → Issue → Tasklist → Task`. Quarkus 3.15.1 / Java 17 / Thymeleaf 3.1.2 server-side rendering,
H2 file DB, dual auth (session for UI, Bearer for API). Fat JAR deployment.

- **Filesystem:** `/workzone/angelica/toolchain/gauge`
- **Tracker project:** `Gauge` = `00000073` (under `toolchain` / `Workstreams`); subproject `ADRs` = `00000561`
- **Group/artifact/version:** `com.sheahorn:gauge:2.1.7`, base package `com.sheahorn.gauge`

## Quick start

```bash
mvn -B clean compile                 # build
mvn -B test                          # tests — 2 known pre-existing failures, see State
./runner.sh                          # run (needs configured application.properties)
```
UI `http://127.0.0.1:8087/`, OpenAPI `/q/swagger-ui`. Docker + nginx sample in `docker/`.

## Map

| Path | What |
|---|---|
| `README.md` | authoritative user doc: config, auth, data model, statuses, API surface |
| `docs/adr/` | ADR 1–17 (+ FR docs) — read before changing architecture |
| `src/main/java/com/sheahorn/gauge/` | `domain/` `repository/` `service/` `resource/` `security/` `config/` `Main.java` |
| `src/main/resources/templates/` | 14 Thymeleaf pages, `layout.html` = shell + CSS tokens + sidebar |
| `src/main/resources/application.properties` | runtime defaults |
| `src/test/java/com/sheahorn/gauge/` | mirrors main packages; ~520 tests |
| `docker/`, `runner.sh`, `application.properties` (root) | deployment |
| `data/` | local H2 files |

Key entry points: `resource/GaugePageResource` (UI pages), `resource/*Resource` (`/api/*`),
`security/SecurityFilter` (Bearer enforcement on all non-public paths), `security/ApiKeyResolver`,
`security/ProjectAccessGuard` (restricted-key scoping), `service/ProjectAnalysisService` (bubbled priority),
`domain/IdProvider` + `IdProviderHolder` (hex-8 IDs).

## State

- Branch `edge` (off `main`). `main` has no `develop`/`master` pair — see Conventions.
- HEAD `2c5ca9d` — configurable statuses slices 1–4 (registry, admin API, dynamic analysis).
- **Uncommitted on top:** slice 5, the UI — `layout.html` (`GaugeStatus` in `<head>`),
  `issueDetail.html`, `projectDetail.html`, `projects.html`, `dashboard.html`,
  new `adminStatuses.html` + `/ui/admin/statuses` route, FR 6 doc, README, 2.1.7.
- **Slice 5 is unverified** — the Maven tool was returning HTTP 504 when it was written.
  Run `mvn -B test` before trusting it.
- Last verified full run: **565 tests, 0 failures** (at `2c5ca9d`). The two long-standing
  baseline failures (`TasklistRepositoryTest.testFindByIssueId`,
  `ApiKeyResolverTest.testResolveMasterKey`) turned out to be state pollution and are gone.
- Test DB is per-profile in-memory H2 with `drop-and-create`, so state no longer leaks
  between test classes.

## Open work (tracker `Gauge`)

- `0000186F` configurable statuses — slices 1–4 committed, slice 5 (UI) uncommitted/unverified.
- `00001877` HIGH — **`@RolesAllowed` never works for Bearer tokens**; all `/api/admin/*` 403
  for API keys, so ID migration has never been reachable. Admin checks must be done in code
  via the JAX-RS `SecurityContext` until `SecurityFilter` publishes a Quarkus `SecurityIdentity`.
- `000007C5` DOING — end-to-end security test coverage (no tasklist yet on it).
- `000014B7` — MCP tool schemas: enumerate enum values, kill agent guessing.
- `0000018E` per-project permissions, `000001BA` trashbin — parked ideas.
- `00000422` comprehensive unit test plan — DONE, 14 tasklists, ~232 test cases (use as reference).

## Gotchas learned the hard way

- **Page scripts run before `layout.html`'s bottom script.** `GaugeStatus` lives in `<head>`
  for exactly this reason. Anything a page needs during init must be in `<head>`.
- `@RolesAllowed` is dead for Bearer tokens (see `00001877`). `AdminResourceTest` still
  asserts 403 as if that were correct.
- Template edits with `fs_files_replace` can eat adjacent tags — re-read after every edit.
- Running `mvn` is the user's job in this workspace; the tool times out. Write `build.log`,
  read it back.

## Conventions / deviations

- Perigee git workflow (`master`/`develop`/`edge` + `feat|fix/{ID}`) is **not in use here**: repo has `main` only.
  Ask before restructuring branches.
- ADR files in `docs/adr/` are named `NNNN-slug.md`, not the `ADR-NNNN.md` convention. Mirror what's there.
- One ADR issue per record lives in tracker subproject `ADRs` (`00000561`) with the full text.
- Issue statuses: `TODO, DOING, DONE, INACTIVE`; tasklist adds `CANCELED`; task adds `FAILED`, `CANCELED`.
- Priority values: `HIGH, NORMAL, LOW` — `INACTIVE` is status only, never priority.
