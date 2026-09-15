# Bifurcan scope storage

Compare Bifurcan `Map` 0.2.0-rc1 with Clojure 1.12.5 maps for Scoped's storage
operations. The dependency is isolated to `:bench-bifurcan` in root `deps.edn`.
This experiment does not change the runtime representation.

Results: [local JDK 25 scan and standard Criterium confirmation, 2026-09-14](2026-09-14/README.md).

## Workloads

Single-binding overrides are the primary case: Quiescent updates its task Var
while retaining the surrounding application context. Parent sizes 1, 4, 8, 9,
16 and 32 cover small maps and the Clojure array-map/hash-map boundary. Existing
lookups cover the same sizes; misses cover 8 and 32. One addition to an
eight-entry map measures promotion to a hash map.

Secondary cases perform batches of 2 or 8 bindings on parents of 1, 8 or 32
entries, alternating overrides and additions. Small parents may receive repeated
overrides of the same key. Construction from empty adds eight distinct bindings.
The `chain` case creates ten successive child maps, changing the same task Var
at every level and retaining every intermediate snapshot. Its results are per
**ten-update chain**, not per child. It has no carrier, callbacks or recursion.

Both implementations are called directly from Java:

- Clojure: `ILookup.valAt(key, absent)`, persistent `assoc` for one update,
  `asTransient`/`assoc`/`persistent` for batches.
- Bifurcan: `Map.get(key, absent)`, persistent `Map.put` for one update,
  `linear`/`put`/`forked` for batches. Calls use the library's last-write-wins
  merge operator directly, avoiding the default two-argument `IMap.put` wrapper.
  Each builder is discarded after `forked`; published snapshots are never linear.

There is no Java/Clojure map adapter, `Optional`, Clojure Var invocation, or
collection conversion in the timed path. Both models share the same benchmark
dispatch and result-retention overhead. Batch inputs, values and parents are
prebuilt, so measurements exclude argument transport and task-object allocation.
Batches use loops on both sides; they do not reproduce Scoped's unrolled helpers.

Fixtures rotate through 128 parents made from 256 real, interned Var keys. The
updated/read key's position rotates, rather than always being the first array-map
entry. Parent values include nil and false. Parents are constructed through
persistent updates, so the Clojure parents use array maps through eight entries.
Var identity hashes are assigned before model-specific work and recorded in every
result. They can vary between fresh JVMs; the summary counts paired cases with
different layouts. The broad scan rotates keys, and important cases should be
repeated to assess variation. Returned maps escape
into bounded, preallocated arrays; chain snapshots do as well.

Verification runs before timing. It checks every fixture against a Clojure map,
plus 500 branching updates from retained ancestors per model, including nil,
false, missing keys, `SKIP`, duplicate keys, empty/all-skipped batches, promotion,
and independence from subsequent changes to the input arrays. All retained
ancestors are checked after the descendants have been created.

## Running

```sh
JAVA_HOME=/path/to/jdk-25 bash bench/experiments/bifurcan/run.sh \
  '{:verify-only? true}'

JAVA_HOME=/path/to/jdk-25 bash bench/experiments/bifurcan/run.sh \
  '{:output "target/bench/bifurcan/screen"}'
```

The script sets `JAVA_CMD` explicitly, compiles the isolated Java kernels using
`clojure -T:build compile-bifurcan`, then runs `clojure -M:bench-bifurcan`.
The runner requires actual JDK 25 and uses a 512 MiB heap. Compilation targets
Java 17 bytecode; no Scoped runtime classes are on the experiment's classpath.

The default scan covers 25 cases × two models, serially in 50 fresh JVMs (about
four minutes in the recorded local run). It
uses Criterium 0.4.6 with three seconds of warm-up, 16 samples targeting 50 ms,
300 bootstrap samples, and no overhead subtraction. This is exploratory; use
`:protocol :full` for standard `criterium/bench` defaults and ordinary overhead
estimation/subtraction. To select a small confirmation:

```clojure
{:protocol :full :forks 1
 :specs [{:parent-size 1 :operation :override}
         {:parent-size 8 :operation :override}
         {:parent-size 32 :operation :override}]
 :output "target/bench/bifurcan/full"}
```

`:forks 2` reverses model order in the second pass. Order also alternates between
cases. `:selected-models ["bifurcan" "clojure"]` reverses the first case's order.
Resume requires `:resume? true` with exactly matching settings, source/class and
dependency hashes, cases, model order and JVM environment. Results are never
overwritten. Use separate directories for separate protocols or selections.

Raw results include Criterium samples, source/class hashes, the Bifurcan JAR hash,
runtime versions, flags and parent implementation classes. Allocation uses
ThreadMXBean after timing, over three batches of 30,000 calls. The measurement
harness has a small fixed per-batch allocation, about 0.005 B per call here.

This comparison measures the storage operations that support `scoping`, `ask`
and `assoc-scope`. It excludes carrier binding, Var-root fallback, Clojure macro
expansion, scheduling, task allocation and real application work. Allocation
counts can motivate a GC experiment but do not establish concurrent throughput
or pause-time improvements.

Sources: [Java kernels](java/co/multiply/scoped/experiment/BifurcanBench.java),
[runner](clj/co/multiply/scoped_bifurcan_bench.clj),
[Bifurcan map documentation](https://lacuna.io/docs/bifurcan/io/lacuna/bifurcan/Map.html).
