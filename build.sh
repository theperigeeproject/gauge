#!/usr/bin/env bash
# Build the uber-jar. Output is teed to build.log (gitignored).
set -euo pipefail

mvn -B clean package | tee build.log
