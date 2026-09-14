# Direct Clojure lookup and scope-entry allocations

Measured 2026-09-14 on Amazon Corretto 25.0.2+10-LTS, macOS/aarch64,
Clojure 1.12.5, ScopedValue, 512 MiB heap. These are exploratory timings and
allocation diagnostics. The EDN records retain settings, source hashes and
environment details.

## Lookup change

`ScopedRuntime.lookup` now uses `ILookup.valAt(variable, ABSENT)` for Clojure
maps. It retains `Map.getOrDefault` for ordinary Java maps. The prior path used
`Map.getOrDefault` for all maps, which performs a second search when `get`
returns null. The Clojure lookup distinguishes a missing key from explicit nil
in one search. Var dereferencing, thread-binding fallback and lazy defaults
retain their previous behavior.

One before/after pair used the development suite's eight `:ask` cases, each with
3 seconds of warm-up and 16 samples targeting 50 ms. Each version ran in a fresh
JVM; cases within each version shared that JVM. No benchmarks ran concurrently.

| Case | Before ns/call | After ns/call |
| --- | ---: | ---: |
| Hit | 5.24 | 5.56 |
| Hit with default | 5.84 | 5.94 |
| Hit in 32-binding map | 11.83 | 11.94 |
| Root fallback | 19.72 | 11.53 |
| Root fallback with default | 19.56 | 11.78 |
| Unbound with default | 20.13 | 12.50 |
| Explicit nil | 20.17 | 8.89 |
| Explicit false | 15.92 | 9.20 |

All cases remained effectively allocation-free. Missing/nil results support the
intended direction, but these are not replicated isolated-fork estimates. The
false case also changed despite requiring only one map search before; its
baseline samples were variable. Do not attribute every timing change solely to
the number of searches, or interpret the narrow within-run confidence intervals
as between-JVM reproducibility.

Raw results: [before](2026-09-14/lookup-before.edn),
[after](2026-09-14/lookup-after.edn).

The packaged correctness matrix passed on actual JDK 17 (23 tests / 281
assertions), JDK 25 ScopedValue and JDK 25 with the base ThreadLocal carrier
(24 tests / 286 assertions each). Additional JVM checks cover array maps, hash
maps and Java HashMaps with nil, false, root and ordinary thread-bound values.

## Allocation profiles

The new `:bench-profile` runner reuses the public-API kernels, with one selected
kernel per fresh JVM. Each run warms up for 5 seconds, measures thread-allocated
bytes over five batches of one million calls, starts JFR, primes its sampler
for 1 second, and records a 3-second measurement window. It measures allocation
again after recording stops. Results below agree before/during/after JFR within
about 0.002 bytes/call of harness overhead.

| Kernel | Bytes/call | Allocations identified in the kernel |
| --- | ---: | --- |
| Control return | ~0 | No sampled kernel allocations |
| Enter prebuilt scope, constant body | 56 | ScopedValue Carrier (32 B), Snapshot (24 B) |
| Enter prebuilt scope, captured local | 56 | Carrier (32 B), Snapshot (24 B) |
| Enter prebuilt scope and read | 56 | Carrier (32 B), Snapshot (24 B) |
| Construct one binding, capture two locals, enter and read | 152 | Above 56 B, map/array (56 B), Clojure closure (24 B), Java adapter (16 B) |

JFR refill events provide the actual sizes and allocation stacks of observed
objects. The object sizes and source paths account for the measured per-call
byte totals. No callback allocations were observed in the three prebuilt cases;
their byte totals support elimination of those objects in these workloads.
Both callback objects were observed in the combined construction/capture case.
This makes callback optimization a focused follow-up for that case, not an
assumed saving on all scope entries.

Sample weights and TLAB-refill counts are not exact allocation counts. Fixed
allocation sequences can strongly bias which object triggers a refill; do not
use their class proportions to infer bytes per invocation. The thread byte
counter provides totals, while JFR identifies types, sizes and stacks. Only
events from the measured thread and measurement time window enter the EDN
summary. Priming excludes initial sample weights that can include allocations
from before the recording started.

The earlier shared-JVM development suite had different allocation totals for
some of these kernels. Compilation profiles and surrounding call sites matter;
the fresh-JVM diagnostics do not establish universal object elimination.

Recorded summaries:

- [Control](2026-09-14/control-return.edn)
- [Prebuilt scope](2026-09-14/entry-with-scope.edn)
- [Captured local](2026-09-14/entry-with-scope-captured.edn)
- [Read in prebuilt scope](2026-09-14/entry-with-scope-read.edn)
- [Construction and captured body](2026-09-14/entry-scoping-captured.edn)

The corresponding raw JFR files remain under
`target/bench/allocation-2026-09-14/settled/`. Binary recordings are not checked
into the repository. The preceding unprimed trial in the parent directory is
excluded from the results above.

## Reproduce a profile

Build with JDK 25+, then select the actual measurement JDK:

```sh
bb compile:java
JAVA_HOME=/path/to/jdk-25 clojure -M:bench-profile \
  '{:case :entry/scoping-captured :output "target/bench/allocation/captured-new"}'
```

Each invocation writes `.edn` and `.jfr` files and refuses to overwrite them.
Use a new output prefix for repeats. `:warmup-seconds` and `:record-seconds`
override the default durations. Run cases serially. To inspect a recording with
the measurement JDK's [JFR tool](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jfr.html):

```sh
/path/to/jdk-25/bin/jfr print --events jdk.ObjectAllocationInNewTLAB \
  --stack-depth 20 target/bench/allocation/captured-new.jfr
```

Raw recordings include the sampler-priming interval; use the `:recorded` start
and end timestamps in the EDN summary when inspecting that interval precisely.

## Remaining representation experiments

For small scopes, `PersistentArrayMap` already copies a flat array on a persistent
update. A transient copies into editable storage and copies back when finalized;
its benefit depends on how many updates amortize that setup and on the parent
map's representation. Compare persistent updates against transients across
parent sizes and new/overridden/skipped bindings before changing the threshold.

A copied Java HashMap also needs a table and entry nodes, so being a Java
collection alone does not imply lower cost. A specialized immutable array of
Var/value pairs is a separate possibility, using the narrower key semantics.
Another alternative is a chain of scope frames, each holding its own small
array and a parent pointer. That trades cheaper extension for deeper lookup
and requires careful treatment of captured scopes and the public map API.
