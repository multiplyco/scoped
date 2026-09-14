# Persistent/transient batch crossover

This follow-up varies the incoming batch size while retaining Clojure maps. It
compares ordinary persistent associations with one transient editing session per
batch. The measured loops are the same adapters used by the original
[representation experiment](README.md).

Results: [JDK 25 crossover measurements, 2026-09-14](2026-09-14-batch/README.md).

The production runtime is not changed. No timed code classifies additions versus
overrides or counts the final distinct keys. Workload classification happens
only while constructing fixtures. The implementation can cheaply know incoming
arity and inspect the parent map's size/type; its keys would require inspection
to determine how many are additions, overrides or duplicates.

## Workloads

The default grid contains parents of 0, 4, 8 and 32 entries and incoming batches
of 2, 4, 8 and 16 bindings. It includes all additions, all overrides, and an
interleaved equal mixture where there are enough parent entries. Overrides use
distinct existing keys and actually change their values. Their positions rotate
across frames, rather than always hitting the first few slots. Additions use
distinct absent keys. All parents are constructed by the same persistent-map
builder, independently of the strategy under measurement.

Four additional cases use a parent of 32 and a batch of eight: half skipped, all
skipped, repeated updates to two new keys, and all values unchanged. These test
how incoming arity can overstate the amount of useful work. The main grid and
sensitivity cases total 40 workloads / 80 strategy combinations.

Each worker rotates 128 prebuilt input frames. Keys are Vars; values include nil,
false and object references. Parent and argument-array construction is outside
the measured operation. Every returned map escapes through Criterium's result
sink. A shared cursor, array selection, and Java call path are included.

## Protocols

Every combination and fork gets a fresh JVM. Workers run serially, alternating
strategy order across cases and reversing it across repeated forks. The parent
checks both strategies before launching workers; each worker checks only its
selected strategy to avoid mixing strategy type profiles in the measured JVM.

- **`:screen`** (default): `criterium/benchmark*`, three-second warm-up, 12 samples
  targeting 50 ms each, bootstrap size 300, overhead subtraction disabled. This
  is an exploratory estimate, not standard `criterium/bench`.
- **`:full`**: standard `criterium/bench` with unmodified 0.4.6 defaults, including
  ten-second warm-up, 60 samples targeting one second each, and normal overhead
  estimation/subtraction.

Both protocols also record thread-allocated bytes over three batches of 100,000
calls after timing. Results retain samples, confidence intervals, parent class,
result count, source and compiled-class hashes, JVM settings, and timestamps.
Each worker has a verbose log. `summary.md` updates after each completed row.

Compare strategies **within a protocol and workload**. Do not pool screen and
full timings, or compare their absolute times as if overhead handling and warm-up
were identical. Short runs can miss optimizations reached by longer warm-up;
use full runs to confirm a proposed cutoff.

## Commands

Compile once with JDK 25+, then measure on an actual JDK 25 installation:

```sh
JAVA_HOME=/path/to/jdk-25 clojure -T:build compile-representation
JAVA_HOME=/path/to/jdk-25 clojure -M:bench-batch \
  '{:output "target/bench/representation/batch-screen"}'
```

Allow roughly ten minutes for the default screen, depending on machine load and
Criterium calibration. Select focused cases and repeated forks explicitly:

```sh
JAVA_HOME=/path/to/jdk-25 clojure -M:bench-batch \
  '{:protocol :full :forks 2
    :specs [{:parent-size 0 :batch-size 4 :workload :add}
            {:parent-size 32 :batch-size 8 :workload :override}]
    :output "target/bench/representation/batch-confirmation"}'
```

Supported workloads are `:add`, `:override`, `:mixed`, `:half-skip`, `:all-skip`,
`:duplicate` and `:same-value`. Parent sizes can be zero or powers of two through
128; batch sizes can be 2 through 64, subject to having enough distinct parent
keys for the requested overrides. A single binding is excluded because the
existing transient adapter intentionally uses persistent `assoc` for that case.

Existing records are never overwritten. Add `:resume? true` with the identical
sources, classes, JVM, protocol, cases and fork count to continue an interrupted
run. Use `{:verify-only? true}` to check fixtures without timing. Verification
compares every key/value and result count with a persistent oracle and checks
parent immutability and caller-array ownership.

The Java fixtures are in
[BatchBench.java](java/co/multiply/scoped/experiment/BatchBench.java); the runner is
[scoped_batch_bench.clj](clj/co/multiply/scoped_batch_bench.clj). Both remain outside
the library JAR.
