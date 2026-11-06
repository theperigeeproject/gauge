# ADR 9: Self-Contained Fat JAR Deployment

## Status
Accepted

## Context
Gauge is a singular-tenant application (ADR 3) that should be trivial to build and deploy. No external app server, no container orchestration, no complex deployment pipeline.

## Decision
Build and ship as a **single fat JAR** via Quarkus's `quarkus-maven-plugin`. Deployment is a one-line shell script.

## Consequences
- `mvn package` produces a self-contained executable `target/gauge-<version>-runner.jar`
- Three scripts, matching the sibling Perigee projects: `build.sh` (`mvn -B clean package`), `test.sh` (`mvn -B clean test`), `run.sh` (`java -jar target/gauge-*-runner.jar`). All three tee output to `build.log` / `test.log` / `run.log`, which are gitignored
- `run.sh` layers local overrides from `dev.application.properties` (gitignored, created from `dev.application.properties.sample`) via `QUARKUS_CONFIG_LOCATIONS`. No configuration with real keys is tracked in git — the packaged `src/main/resources/application.properties` holds only `change-me` placeholders, which `MasterKeyGuard` refuses to boot on
- H2 database file lives alongside the JAR in `./data/`
- No Docker required (though a `docker/` directory exists for optional containerization)
- Quarkus fast-start and low memory footprint make it suitable for modest hardware
- One-click build, one-click run — fits the "ride shotgun with your daemons" ethos
