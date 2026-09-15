#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
: "${JAVA_HOME:?Set JAVA_HOME to JDK 25}"
# Recent Clojure CLI versions prefer JAVA_CMD/PATH over JAVA_HOME.
export JAVA_CMD="$JAVA_HOME/bin/java"
clojure -T:build compile-bifurcan
exec clojure -M:bench-bifurcan "${1:-{}}"
