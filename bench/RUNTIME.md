# JVM runtime benchmarks

The public-API suite measures reads, construction, scope entry, nesting and
capture. The current protocol uses **one case per fresh JVM**, with **five forks
per case/backend**, **20 seconds of warm-up**, and **60 samples targeting one
second each**. Criterium 0.4.6 and Clojure 1.12.5 are pinned by the benchmark alias;
workers inherit the fixed 512 MiB heap. Run workers serially.

The implementation experiments are saved in Git stash
`6fbf3f8a8deee6fbf2d464cb4c0de0e3ae975ad5`. Active production sources match
`0b3dd159b88cf3072b59f19285dc2f0adb5aaf72`, the original Clojure implementation.
The stash includes the Java runtime, CLJ/CLJS split, build changes and experimental
tests. Benchmark code, historical results and benchmark-only dependency aliases
remain in the working tree. Applying the stash later may require merging the
benchmark aliases in `deps.edn` and the README benchmark section.

## Run a baseline

Use JDK 25+ to measure both backends on the same JVM:

```sh
clojure -M:bench-runtime '{:backends [:scoped-value :thread-local] :label "original Clojure baseline" :output "bench/baselines/2026-09-11-isolated/summary.edn"}'
```

The complete 31-case matrix runs 310 fresh JVMs and takes roughly seven hours.
Each backend is selected in its worker JVM before the library loads. With no
`:backends` option, the runner selects the current JVM's default backend (or
ThreadLocal when `co.multiply.scoped.force-fallback=true`). The original Clojure
baseline does not require Java compilation. When restoring the Java experiment,
compile its runtime before benchmarking it.

The output directory contains:

- `summary.edn`: settings, source hashes, the deterministic job schedule, completed
  results and aggregates; checkpointed atomically after each fork.
- `summary.edn.md`: a table of fork medians, ranges, allocations and diagnostic flags.
- `forks/`: complete raw Criterium records, environment and diagnostics for each fork.
- `logs/`: per-worker progress/error logs, plus separate verification logs at the root.

To resume, rerun the same command with `:resume? true`. Completed worker files
are validated and reused. A changed source, dependency/suite hash, relevant
runtime environment, schedule or measurement setting prevents resumption.
An existing output requires explicit resumption; worker files from another
manifest are never silently reused. To start over, choose a new directory.

```sh
clojure -M:bench-runtime '{:backends [:scoped-value :thread-local] :label "original Clojure baseline" :resume? true :output "bench/baselines/2026-09-11-isolated/summary.edn"}'
```

Keep the machine on external power and avoid competing benchmarks. The runner
prints progress every 30 seconds while a worker is active. It preserves all
completed forks after a failure; inspect the named worker log before resuming.

## Isolation and correctness

Full correctness verification runs in a separate JVM for each backend. Measured
workers construct fixtures but invoke only their selected kernel. They do not
run the suite's verification pass. Returned measurements are checked after
sampling, and scope restoration is checked before the worker saves its result.
The changing workload's 128 frames are all checked in the verification process.

Case order is shuffled reproducibly for each fork round with `:seed 20260911`.
Backend order alternates within the schedule. Fresh processes prevent one
case's warmed function/map profiles from becoming another case's starting point.
The seed and complete plan are recorded; a later candidate should use the same
schedule, settings, machine and JDK as its baseline.

The benchmark runner itself has focused tests for scheduling, result validation,
aggregation, worker isolation and options:

```sh
clojure -M:bench-test
clojure -M:bench-runtime '{:verify-only? true}'
clojure -J-Dco.multiply.scoped.force-fallback=true -M:bench-runtime '{:verify-only? true}'
clojure -M:bench-runtime '{:list? true}'
```

The library's JVM-specific tests were excluded by its original Kaocha namespace
pattern. For baseline validation both original namespaces are loaded explicitly;
no production or test source changes are needed.

## Workloads

| Group | What one measured call does |
| --- | --- |
| `control` | Reads and returns a runtime array value to expose the harness floor |
| `current` | Captures with no active scope, an empty scope, or a populated scope |
| `ask` | Scoped hit with/without default, 32-binding map, root/unbound fallback, explicit nil and false |
| `build` | Constructs a map through `assoc-scope` with 0, 1, 2, 9 or 10 bindings |
| `entry` | Enters prebuilt or newly built scopes, capturing bodies, inherited/absent skips |
| `nested` | Enters two scopes, overrides a value and checks restoration |
| `capture` | Captures a scope and restores it from an empty context |
| `workload/read-100` | Enters a scope and performs 100 reads into a preallocated array |
| `workload/changing-scopes` | Cycles 128 frames, varying parent sizes (0/4/8/16/32), objects/nil/false/skip, defaults and nested bindings |

The original nine/ten boundary separates unrolled transient association from a
vector loop. Fixture maps, frames and result arrays are allocated before timing.
For isolated scoped reads, the scope surrounds the whole Criterium measurement,
including warm-up; entry is not charged on each read. The changing workload
includes frame selection, scope entry/construction, reads, stores and restoration.

Values are nanoseconds **per complete invocation**. The 100-read and nested
workloads are not divided by their operation counts. Loop optimizations may
combine repeated reads, so the 100-read result is not an independent lookup
latency. The changing case helps assess behavior beyond an invariant binding.
All results reach Criterium's result sink; invocation overhead is not subtracted.

## Diagnostics and interpretation

Each fork retains all raw samples, within-fork bootstrap intervals, actual
warm-up time/execution count and allocation samples. The observer records
compilation time, classes loaded and GC counts/time immediately before and
after sample collection. It runs outside the timed invocation loop. The records
also include per-call samples, first/last-ten means, their ratio, and the ratio
between measurement and the **whole warm-up average**. That warm-up average is
not a measurement of the end of warm-up.

Flags identify more than 5% first/last-window drift, more than 20% warm-up/measurement
shift, and compilation or GC during sampling. They are investigation signals,
not reasons to discard a slow fork. No samples or forks are removed. Compilation
activity is JVM-wide and does not prove the kernel recompiled. Capture a separate
HotSpot compilation/deoptimization log if a suspicious case needs attribution.

Aggregates report the median and full range of fork means. They do not pool
samples from different JVMs into a misleadingly narrow confidence interval.
A spread above 20% is retained as `:wide-fork-spread?` in the aggregate. Compare
both the fork distribution and diagnostic flags before attributing differences
to the implementation. Tiny differences near the return-control floor need
particular caution. Identity hashes of real Var keys can change hash-map layout
between forks.

Allocation counters use `ThreadMXBean` on the measurement thread, in three
batches of 100,000 calls after timing. These count allocated bytes, not retained
heap; unsupported counters are reported as unavailable. Escape analysis can
eliminate allocations. GC inside an allocating workload remains part of its
measured cost.

## Narrowing, smoke checks and comparisons

Select cases or groups without weakening the timing protocol:

```sh
clojure -M:bench-runtime '{:cases [:ask/root-default :workload/read-100] :backends [:scoped-value :thread-local] :output "target/bench/focused/summary.edn"}'
clojure -M:bench-runtime '{:groups [:ask] :output "target/bench/reads/summary.edn"}'
```

Explicit smaller settings are available for testing the runner. Results with
fewer than five forks, ten seconds of warm-up, or sixty one-second samples are
marked `:quality :exploratory`. They are not baseline-quality measurements.

```sh
clojure -M:bench-runtime '{:cases [:ask/hit] :forks 1 :output "target/bench/smoke/summary.edn" :criterium {:samples 4 :warmup-jit-period 100000000 :target-execution-time 10000000 :bootstrap-size 100}}'
```

Compare completed runs with matching protocols, suites, settings and environments:

```sh
clojure -M:bench-runtime '{:compare ["bench/baselines/2026-09-11-isolated/summary.edn" "target/bench/candidate/summary.edn"]}'
```

The comparison uses ratios of fork medians and rejects incompatible runs.
Direct linking is a separate experiment: use `-J-Dclojure.compiler.direct-linking=true`
consistently and keep those results apart from ordinary compilation.

The older [Clojure baseline](baselines/README.md), [Java experiment](experiments/java/README.md)
and [Clojure-read experiment](experiments/clojure-reads/README.md) used short,
shared-JVM measurements. Their exact suite is archived as
[legacy-runtime.clj](experiments/legacy-runtime.clj); copy it back to its original
`bench/co/multiply/scoped_runtime_bench.clj` location in an isolated historical
snapshot to reproduce those runs. Do not compare their absolute timings directly
with this protocol. The [construction-only benchmark](README.md) remains a
separate historical algorithm comparison.
