#!/usr/bin/env bash
# Run the built uber-jar. Output is teed to run.log (gitignored).
#
# Local overrides (master key, pepper, ports, ...) are layered in from
# dev.application.properties, which is gitignored. Create it first:
#
#     cp dev.application.properties.sample dev.application.properties
#
# Build first with ./build.sh.
set -euo pipefail

QUARKUS_CONFIG_LOCATIONS=dev.application.properties \
  java -jar target/gauge-*-runner.jar | tee run.log
