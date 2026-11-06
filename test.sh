#!/usr/bin/env bash
# Run the test suite. Output is teed to test.log (gitignored).
#
# No build required first; Maven compiles as needed.
set -euo pipefail

mvn -B clean test | tee test.log
