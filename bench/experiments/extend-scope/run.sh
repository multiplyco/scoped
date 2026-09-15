#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 25}"
classes=../../../target/bench/extend-scope/classes
mkdir -p "$classes"
classpath=$(clojure -Spath)
"$JAVA_HOME/bin/javac" --release 25 -cp "$classpath" -d "$classes" \
  ../../../src/co/multiply/scoped/MapUpdates.java \
  ../../../src-java/jdk25/co/multiply/scoped/ScopedRuntime.java \
  java/co/multiply/scoped/experiment/ExtendKernel.java
"$JAVA_HOME/bin/java" -cp "$classpath" clojure.main -e \
  "(binding [*compile-path* \"$classes\"] (compile 'co.multiply.scoped-extend-kernels) (compile 'co.multiply.scoped-extend-bench))"
exec "$JAVA_HOME/bin/java" -Xms512m -Xmx512m -cp "$classpath" clojure.main \
  -m co.multiply.scoped-extend-bench "${1:-{}}"
