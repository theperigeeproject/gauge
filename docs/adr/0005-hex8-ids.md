# ADR 5: Hex-8 Sequential ID System for Business Entities

## Status
Accepted

## Context
Gauge is used by both a human and an LLM daemon (ADR 3). The LLM reads, writes, and references entity IDs in its context window. The ID format directly impacts the LLM's ability to work with them accurately.

## Decision
Business entities (projects, issues, tasklists, tasks) use **globally unique, sequential 8-digit hexadecimal IDs** (e.g., `00000001`, `00000562`). Users and API keys remain on UUIDs.

## Rationale
- **8 hex digits** provide optimal semantic distance for LLMs: they look obviously like identifiers, LLMs are trained on distinguishing details in such values, and they're short enough for LLMs to operate on with ease
- UUIDs are too long — they waste context window and are harder for LLMs to distinguish at a glance
- Plain integers carry non-ID semantics (numerals, ordering) that confuse LLM interpretation
- Global sequential counter prevents subtle collisions like mixing entities with similar IDs across tables

## Consequences
- `IdProvider` scans all 4 managed tables on startup to find the highest existing hex-8 ID and resumes the counter
- `gauge.ids.start-offset` (issue 000018CA) sets a **floor** for the sequence: the first ID handed out is `max(dbMax + 1, offset)`. The offset can never lower the counter — continuity beats configuration, because handing out an ID already in the database would be a primary-key collision. Default `0` reproduces the original behaviour; values above `0xFFFFFFFF` are rejected at startup
- If any non-hex-8 ID is found, the provider falls back to UUID mode — migration required
- `IdMigrationService` exists as a one-time tool to convert UUIDs to hex-8, guarded by a JVM flag (`angelitrack.migration.i-know-what-i-am-doing`)
- `IdProviderHolder` bridges CDI into static entity factory methods (`Project.create()`, etc.)
- Counter overflow at `0xFFFFFFFF` triggers UUID fallback — effectively never for singular tenant
- User and ApiKey entities remain UUID — they are not referenced by LLMs in the same way
