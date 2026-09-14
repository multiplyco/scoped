# JVM development benchmarks

Use this suite during the Java experiment to spot large regressions and get a
rough direction quickly. It runs all 31 public-API workloads from the
[isolated runtime suite](RUNTIME.md) in **one shared JVM**, with **one second of
warm-up per case**, **eight samples targeting 25 ms each**, and **two allocation
batches of 10,000 calls**. Allow roughly a minute per backend on a development
machine; the initial local run completed the measurements in 45 seconds.
Startup, Java compilation, JIT activity and machine load affect elapsed time;
this is a short sampling budget, not a hard timeout.

Proposed follow-ups are tracked in [JVM experiment candidates](EXPERIMENTS.md).

```sh
bb bench:dev
bb bench:dev:thread-local
```

These tasks compile Java first and accept one optional EDN options map. Local
benchmarks load the Java implementation from `target/scoped-runtime.jar`, so
they exercise the same multi-release class selection as the published JAR.
Without Babashka, compile once after Java source changes, then run:

```sh
clojure -T:build compile-java
clojure -M:bench-dev
clojure -J-Djdk.util.jar.version=17 -M:bench-dev
```

Compilation requires JDK 25+; the resulting runtime supports JDK 17+.
Each invocation measures the selected JVM backend only. On JDK 25+ the default
is ScopedValue; `jdk.util.jar.version=17` selects the base ThreadLocal class.
This JDK setting affects all multi-release JARs in the process; it changes class
selection, not the actual JVM version. Compatibility is tested separately on
actual JDK 17 with `bb test:clj:jdk17`. Run the backends
serially and keep separate result files when comparing them. CLJ is the
performance target; CLJS does not participate in this suite.

## Development loop

Save a reference before changing the implementation:

```sh
bb bench:dev '{:label "before experiment" :output "target/bench/dev/before.edn"}'
```

After making changes, measure and compare in the same command:

```sh
bb bench:dev '{:baseline "target/bench/dev/before.edn" :output "target/bench/dev/after.edn"}'
```

The comparison prints candidate/reference time ratios and allocation changes,
sorted with the largest slowdowns first. `CHECK` marks ratios of at least 1.5x
for investigation; it does not fail the command or establish a regression by
itself. Allocations are bytes allocated per complete invocation, not retained
heap. Unsupported allocation counters are reported as unavailable.

Results include raw Criterium samples, diagnostics, source/suite hashes, JVM
settings and case order. An EDN file and adjacent `.edn.md` report are saved
after each case. The default is `target/bench/dev/latest.edn`; repeated runs
replace that development result. A failed measurement leaves a partial report.
Use named paths to retain references. A baseline cannot also be the output,
and output files from other benchmark protocols are never overwritten.

Compare saved results without measuring again:

```sh
clojure -M:bench-dev '{:compare ["target/bench/dev/before.edn" "target/bench/dev/after.edn"]}'
```

Comparisons require complete development runs with the same backend, case order,
measurement settings, suite hashes and relevant JVM/OS environment. Production
source hashes may differ. Do not compare absolute timings with the isolated or
historical benchmark protocols.

## Focused runs

All cases run by default, including reads and defaults, nil/false, 0/1/2/9/10
binding construction, entry/exit, skipped bindings, nesting, capture/restore,
100-read bodies and 128 changing input frames with parent sizes up to 32.
Select existing cases or groups for a smaller development loop:

```sh
bb bench:dev '{:groups [:ask :build] :output "target/bench/dev/reads-builds.edn"}'
bb bench:dev '{:cases [:build/nine :build/ten :workload/changing-scopes]}'
clojure -M:bench-dev '{:list? true}'
clojure -M:bench-dev '{:verify-only? true}'
```

`:reverse? true` reverses the case order to help investigate order sensitivity.
Use that same order for both reference and candidate. Nested `:criterium`
options override short defaults; `:allocation-iterations` and
`:allocation-samples` are also configurable. `:forks` and `:backends` belong to
the isolated runner and are rejected here.

## Allocation profiling

For allocation types and stacks, run one kernel in a fresh JVM with JFR:

```sh
bb compile:java
JAVA_HOME=/path/to/jdk-25 clojure -M:bench-profile \
  '{:case :entry/scoping-captured :output "target/bench/allocation/captured"}'
```

This writes `.edn` and `.jfr` files, refuses to overwrite existing results, and
reports thread-allocated bytes before, during and after recording. It defaults
to 5 seconds of warm-up, 1 second to prime the sampler and a 3-second measurement
window. JFR summaries contain only the measured thread's events in that window.
Use sample stacks to identify allocations, not as exact per-class allocation
counts. See the [JDK 25 findings and recording details](experiments/lookup-allocation/README.md).

## Interpretation

Every result is marked `:quality :exploratory`. Kernels, fixture validation,
result sinks, scope-restoration checks and allocation measurement are reused
from the full suite. Correctness verification runs before timing in this same
JVM, including all 128 changing frames when that workload is selected. Fixtures
are built once; read contexts surround the whole measurement, so those cases
do not charge scope entry on every read. No invocation overhead is subtracted.

The shorter warm-up and shared JVM allow call-site profiles, inlining and run
order to influence results. Tiny operations near the return-control floor are
especially noisy, and allocation counters include a small harness cost. The
short-run confidence intervals printed by Criterium do not capture these
sources of variation. Diagnostics retain compilation, GC and drift signals;
they do not remove slow samples.

Use this suite to catch dramatic cost or allocation growth and choose what to
investigate next. Repeat suspicious results in fresh JVMs under comparable
machine load. Confirm small differences and implementation decisions with
focused cases in `:bench-runtime`; its longer defaults remain unchanged.

Runner checks, including comparison compatibility and partial-result handling:

```sh
clojure -M:bench-test
```
