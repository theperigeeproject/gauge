# Gauge

Ride shotgun with your daemons.

Gauge is a work tracker for a team of two — silicon driver and carbon navigator. The design follows the core cooperation dynamics principle: Human chooses the course, LLM does the job. The tracker is profiled primarily for LLM use, with a simple, human-friendly UI establishing communication ground between each of teammates.

Gauge provides just enough structure to organize a workstream or project of any scope (hence the name). The project is driven by the belief that simplicity and communication are far more efficient than constraints and bureaucracy. Ockham's razor always beats whips and chains, regardless of participants and their substrate.

Settle comfortably in the passenger's seat. Let your AI companion steer, and hold the roadmap.

## Quick Start

### 1. Build

```bash
./build.sh
```

Runs `mvn -B clean package`; output is teed to `build.log`. Requires Java 17 and Maven.

### 2. Configure

You must configure security before using Gauge. The app **refuses to start** while the master API key or the pepper is still `change-me`.

Create your local override file from the sample:

```bash
cp dev.application.properties.sample dev.application.properties
```

`dev.application.properties` is gitignored and is layered over `src/main/resources/application.properties` at runtime, so real keys never enter the repository.

| Default | Value |
|---|---|
| Interface | `127.0.0.1` |
| Port | `8087` |
| Admin credentials | `admin` / `admin` |
| Master API key | `change-me` |
| Pepper | `change-me` |

Consider changing default credentials if you expose Gauge to the open network. You can create additional users and API keys in the application.

### 3. Run

```bash
./run.sh
```

`run.sh` boots the jar with `QUARKUS_CONFIG_LOCATIONS=dev.application.properties`, so your local file overrides the packaged defaults. Output is teed to `run.log`. Build first with `./build.sh`.

### 4. Use

The app listens on `http://127.0.0.1:8087` by default. UI is available at `/`. OpenAPI specs are available at `/q/swagger-ui`.

## Configuration

All settings live in `src/main/resources/application.properties`. Runtime overrides go in the gitignored `dev.application.properties` (see `dev.application.properties.sample`) and are picked up by `run.sh`. The minimal viable set:

| Property | Description |
|---|---|
| `gauge.api-key.access` | Master Bearer token for API access |
| `gauge.api-key.pepper` | Pepper for hashing user-created API keys |
| `gauge.auth.enabled` | Toggle for the SecurityFilter (default `true`). Set to `false` to disable Bearer token enforcement entirely. |
| `gauge.auth.admin.username` / `.password` | Seeded admin user for the browser UI |
| `gauge.ids.start-offset` | Floor for the hex-8 ID sequence (default `0`). Decimal or `0x`-prefixed hex. |
| `quarkus.http.port` | HTTP listen port |
| `quarkus.hibernate-orm.database.generation` | Hibernate DDL value |

If you want the app to listen on all interfaces, set host to `0.0.0.0`. The packaged default DDL is `none`, which will halt startup against an empty database — `update` is the safest choice for a local run, and `dev.application.properties.sample` already sets it.

### ID start offset

Entity IDs are sequential hex-8 (`00000001`, `00000002`, …). `gauge.ids.start-offset` raises where the sequence begins, so a fresh database can start at, say, `00004000`:

```properties
gauge.ids.start-offset=0x4000
```

The offset is a **floor, not a cap** — the first ID handed out is `max(highest ID already in the database + 1, start-offset)`. Existing rows always win, because reusing an ID already present would collide with a primary key. Consequences: restarting with the same offset changes nothing, and raising it above the current highest ID only takes effect on an empty (or lower-numbered) database. Values above `0xFFFFFFFF` are rejected at startup.

## Tests

```bash
./test.sh
```

Runs `mvn -B clean test`; output is teed to `test.log`. The suite boots the whole application under the `test` profile, using the in-memory H2 configured in `src/test/resources/application.properties` — it must never touch `./data/gauge`.

## Authentication

Two parallel auth mechanisms:

- **Browser UI** — form login with session cookies. The admin user is seeded on first boot.
- **API** — Bearer token in the `Authorization` header. Accepts the master token (`gauge.api-key.access`) or user-created API keys (managed via the browser UI under Account).

API endpoints also accept the session cookie, so a browser session works for both UI and API calls.

### API Key Restrictions

User-created API keys can be **restricted to a single project** (and its descendants) with an access level:

- **READ** — can only view the project and its children. Cannot create, modify, or delete anything.
- **WRITE** — full CRUD access within the project tree. Cannot create root projects (must create under the scoped project).

Restricted keys see only their scoped project tree in all endpoints — including list, search, and analysis results. Unrestricted keys (no project set) have full access to everything. The master key is always unrestricted.

The SecurityFilter enforces Bearer token authentication on **all paths** except `login.html`, `login-failed.html`, and `logout`. Every other endpoint — API, UI pages, root — requires a valid token or session.

## Data Model

Gauge uses a straightforward data model for expressing workstreams:

```
Project  →  Issue  →  Tasklist  →  Task
```

Each level adds a different kind of structure rather than repeating the same pattern:

- **Projects** form a *tree structure* of subprojects. Tree structure is an organising agent, allowing adjustment of granularity until the distribution reaches satisfying levels.
- **Issues** are a flat *set* within a project. No tags, no cross-project links. Fuzzy relationships live in titles and descriptions, not in the schema. This is where the human and the LLM negotiate meaning. Issues act as a coupling "clutch" between subprojects and tasks — real-world requirements are rarely trivial enough to fit in a closed system. Gauge chooses communication over squeezing needs into structures.
- **Tasklists** express *independent sub-workstreams* within an issue.
- **Tasks** are *ordered steps* within a tasklist. A task is considered an atomic operation from its tasklist's point of view.
- **Tasklists can decompose a task** — A task can refer to a whole tasklist within the same issue. The tasklist becomes the breakdown of that task. This is used primarily for creating execution tasklists, scheduling executions of other tasklists.

| Level | Coupling | Relation | Meaning |
|---|---|---|---|
| Project | Tree | Containing | Explicit hierarchy, no depth limits |
| Issue | Undefined | Semantic | Fuzzy, unknown, inexpressible in simple terms, negotiated by human and LLM |
| Tasklist | Horizontal | Neighbouring | Independent sub-workstreams |
| Task | Vertical | Following | Ordered, atomic steps |

Please note that a task can simply state it refers to a project, creating a semantic loopback in the structure. This hack, however, is useful only when refactoring implementation plans is costly, and only occurs after enough bad planning decisions are made.

### Statuses

| Level | Statuses |
|---|---|
| Issue | `TODO`, `DOING`, `DONE`, `INACTIVE` |
| Tasklist | `TODO`, `DOING`, `DONE`, `CANCELED` |
| Task | `TODO`, `DOING`, `DONE`, `FAILED`, `CANCELED` |

Every entity starts in `TODO`. Transitions are unrestricted — there is no state machine.

`INACTIVE` (issues only) means **parked**: not being worked on, and not finished. It is the *least important* status — it ranks below `DONE` and never displaces it. Parked issues never bubble a priority (a parked `HIGH` issue does not make its project `HIGH`), and `INACTIVE` surfaces in project analysis only when nothing is active and nothing is done. Ordering is `TODO, DOING, DONE, INACTIVE` everywhere — active work first, finished next, parked last — and parked issues are shown by default in the project issue list (unlike `DONE`).

## API

All `/api/*` endpoints require a Bearer token. The API mirrors the data model:

### Projects (`/api/projects`)

CRUD, search (`?q=`), list by parent (`?parentId=`), flat list (`?flat=true`), list by IDs (`?ids=`), reparent, ancestors, analysis. Cascade delete requires `?cascade=true`. Projects can be locked against deletion with `removalLock`.

### Issues (`/api/issues`, `/api/projects/{id}/issues`)

CRUD, search, sort (`?sort=NAME|PRIORITY_STATUS_NAME|...`), filter by priority and status (`?priority=HIGH&status=TODO,INACTIVE`), move to another project, cascade delete. Statuses: `TODO`, `DOING`, `DONE`, `INACTIVE` — see [Statuses](#statuses).

### Tasklists (`/api/tasklists`, `/api/issues/{id}/tasklists`)

CRUD, search, status update, link/unlink decomposed task, cascade delete.

### Tasks (`/api/tasks`, `/api/tasklists/{id}/tasks`)

CRUD, search, status update, reorder within tasklist.

### Users (`/api/users`)

List, create, get current user (`/me`), change password, delete. Admin-only for user management.

### API Keys (`/api/apikeys`)

Create (returns raw key once — store it), list, delete. Keys are hashed with SHA-256 + pepper.

### Favorites (`/api/favorites`)

Per-user project bookmarks. Add, remove, list, reset.

### Admin (`/api/admin`)

ID migration from UUID to sequential hex-8. One-time, destructive, guarded by a JVM flag.

## Docker

An example docker setup is provided in `docker/`. This example contains an nginx handling HTTPS, required for open network exposure.

```bash
cd docker
./setup.sh
```

Builds the jar, generates self-signed TLS certificates, and starts Gauge behind nginx at `https://localhost`.

## Design Philosophy

Gauge is a **singular-tenant** application. It assumes exactly two consumers: one human and one LLM daemon. It is not designed for teams, multitenancy, or agentic swarms. This constraint simplifies the entire stack — no tenant isolation, no row-level security, no scaling concerns.

The coupling model at each level is deliberate. Projects are rigid trees. Issues are deliberately flat — fuzzy cross-cutting concerns live in naming conventions and search, not in the schema. Tasklists are parallel by default. Tasks are sequential. Each level adds a different kind of structure rather than repeating the same pattern.

## License

MIT — do whatever you want, just keep the copyright notice. See [LICENSE](LICENSE).
