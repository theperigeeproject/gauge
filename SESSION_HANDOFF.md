# Gauge — Session Handoff

Warm-up table of contents. History lives in git + the tracker, not here.

## What it is

Gauge ("ride shotgun with your daemons") is a singular-tenant work tracker for one human + one LLM daemon.
Hierarchy: `Project → Issue → Tasklist → Task`. Quarkus 3.15.1 / Java 17 / Thymeleaf 3.1.2 server-side rendering,
H2 file DB, dual auth (session for UI, Bearer for API). Fat JAR deployment.

- **Filesystem:** `/workzone/angelica/toolchain/gauge`
- **Tracker project:** `Gauge` = `00000073` (under `toolchain` / `Workstreams`); subproject `ADRs` = `00000561`
- **Group/artifact/version:** `com.sheahorn:gauge:2.1.5`, base package `com.sheahorn.gauge`

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

- Branch `main`, clean, in sync with `origin/main`, HEAD `20eaa11` (v2.1.5 — INACTIVE issue status).
- Leftover branch pointer `feat/issue-status-inactive` = same commit as `main`. Safe to delete.
- Test baseline: ~520 tests, **2 pre-existing failures** — `TasklistRepositoryTest.testFindByIssueId` (expected 2, got 3)
  and `ApiKeyResolverTest.testResolveMasterKey`. Suite is state-pollution-prone (leftover rows can flip
  `IdProvider` into UUID mode → spurious ID-length failures). Compare against this baseline, don't trust green.

## Open work (tracker `Gauge`)

- `000007C5` DOING — end-to-end security test coverage (no tasklist yet on it).
- `0000149B` — POST/PATCH description-limit mismatch + silent failure on invalid priority.
- `000014B7` — MCP tool schemas: enumerate enum values, kill agent guessing.
- `0000149C` — config toggle to allow long issue descriptions (depends on `0000149B`).
- `0000018E` per-project permissions, `000001BA` trashbin — parked ideas.
- `00000422` comprehensive unit test plan — DONE, 14 tasklists, ~232 test cases (use as reference).

## Conventions / deviations

- Perigee git workflow (`master`/`develop`/`edge` + `feat|fix/{ID}`) is **not in use here**: repo has `main` only.
  Ask before restructuring branches.
- ADR files in `docs/adr/` are named `NNNN-slug.md`, not the `ADR-NNNN.md` convention. Mirror what's there.
- One ADR issue per record lives in tracker subproject `ADRs` (`00000561`) with the full text.
- Issue statuses: `TODO, DOING, DONE, INACTIVE`; tasklist adds `CANCELED`; task adds `FAILED`, `CANCELED`.
- Priority values: `HIGH, NORMAL, LOW` — `INACTIVE` is status only, never priority.
