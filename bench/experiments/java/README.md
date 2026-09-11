# Aggressive Java runtime experiment

The Java prototype is implemented and measured, but it is not an across-the-board
improvement over the Clojure baseline. CLJ is the performance target. CLJS needs
compatible public behavior and shared correctness tests; its implementation and
storage can diverge completely if that benefits the JVM implementation.

The follow-up experiment uses [Clojure reads with the Java carrier](../clojure-reads/README.md).
The results on this page describe the preceding Java-read version. Apply the
[Java-read patch](../clojure-reads/java-reads.patch) to restore that version
before applying the argument-array prototype patch below.

These runtime experiments have since been stashed for a clean baseline. See the
[current benchmark protocol](../../RUNTIME.md) for the stash identifier and the
new isolated-fork measurements. This page retains the earlier exploratory results.

## Findings

- With ScopedValue, ten-binding construction falls from 473–488 ns to 411–412 ns
  and from 824 to 688 bytes per call. Constructing and entering that scope falls
  from 558–579 ns to 462–465 ns. The nested override case improves from about
  171 ns to 119 ns, with unchanged allocation.
- These construction gains do not carry over consistently to the forced
  ThreadLocal runs. Nine-binding construction is slower there, despite using
  the same construction code and never consulting the carrier. That difference
  cannot be attributed directly to ThreadLocal access; compilation profiles and
  per-process map layout remain possible influences.
- Several reads regress on both backends, especially defaults and explicit
  nil/false. The ThreadLocal captured-body case increases from 29–33 ns to
  53–54 ns, with unchanged allocation. Moving the callback boundary back into
  the Clojure macro is a focused follow-up experiment, not an established fix.
- The fixed-arity methods reduce allocation versus the initial argument-array
  prototype at nine and ten bindings. They improve ScopedValue timings in that
  comparison but do not establish a timing win on ThreadLocal.
- Run-order sensitivity remains substantial: the Java ThreadLocal 100-read
  workload measures 1.06 and 3.09 microseconds in separate forks. The results
  support selective changes and further isolation, rather than shipping the
  entire Java rewrite as a demonstrated performance improvement.

## Implementation

The prototype moves carrier storage and selection, scope lookup, root fallback,
unbound detection, scope entry/exit, sentinel checks and map extension into Java.
The public `co.multiply.scoped` namespace has separate CLJ and CLJS entry files.
Clojure macros still resolve Vars at compile time, preserve lazy defaults and
create lexical bodies. The shared `.cljc` implementation emits either Java calls
or the existing CLJS operations.

The JVM uses a static final backend selected once. Java 25+ loads a backend with
a static final `ScopedValue`; the fallback owns a `ThreadLocal`. Backend loading
uses reflection once during initialization. Lookup and entry have no reflective
dispatch. Entry accepts a Clojure `IFn`; the Java ScopedValue backend adapts it to
`CallableOp`. This intentionally tests the cost of moving the callback boundary
as well as the storage into Java.

One through ten bindings have dedicated Java methods, with the transient assocs
unrolled just as in the original Clojure macro expansion for two through nine.
Larger scopes use an object array filled directly by the macro, then a Java
transient map loop. No intermediate Clojure vector is built. The initial prototype
used arrays from three bindings upward; its forward runs are retained separately
for [ScopedValue](array/2026-09-11-scoped-value-forward.edn) and
[ThreadLocal](array/2026-09-11-thread-local-forward.edn).
The [array prototype patch](array/prototype.patch), applied to this fixed-arity
version, restores the exact production source hashes measured in those runs.
The skip sentinel is a Java static final object. Both
platforms use an opaque missing-value marker so an ordinary keyword cannot be
mistaken for absence.

The fixed-arity comparison still has one meaningful difference: the Clojure
macro expands in the caller, whereas Java puts the sequence in a static method.
Inlining and argument handling can therefore affect the outcome even when the
association sequence is the same. Timings alone do not establish which machine
instructions the JIT chose.

## Correctness and build checks

- 22 JVM tests / 269 assertions pass with both backends on Java 26. The test
  runner now includes the previously excluded JVM-specific test namespace.
- The same tests pass from the packaged JAR on Java 26 and Java 21. Java 21
  selects the fallback without attempting to load the Java 25 class.
- 17 shared CLJS tests pass in headless Chrome.
- Coverage includes nil/false/unbound values, lazy defaults, evaluation order,
  skipped/duplicate bindings, captured scopes, dynamic Clojure bindings,
  exception/Error cleanup and virtual-thread isolation/restoration.
- The JAR contains both CLJ/CLJS entry files. Base class files target Java 9
  (version 53); the ScopedValue class targets Java 25 (version 69). Java 9 itself
  was not available for runtime testing.
- Local-dependency preparation was exercised through `clojure -X:deps prep :force true`.

Source builds require JDK 25+, via `clojure -T:build compile-java`. The `bb`
development/test tasks do this automatically; `clojure -T:build jar` rebuilds
and packages both Java targets. Published binaries can still run on older JVMs
through the fallback.

## Measurement

The [30-case runtime suite](../../RUNTIME.md) is unchanged, including its hash:
`41001f26b58e773f786546e4da05a3b6dab3aeecb12a06a1d2c708ac35f2651c`.
The reference is the [Clojure baseline](../../baselines/README.md). Both use
Clojure 1.12.5, OpenJDK 26.0.2+10-FR, macOS/aarch64 and a 512 MiB heap, with
ordinary compilation. Each case gets three seconds of warm-up and 20 samples
targeting 50 ms. No invocation overhead is subtracted.

The candidate runs are fresh JVMs in this order: ScopedValue forward,
ThreadLocal forward, ThreadLocal reverse, ScopedValue reverse. No benchmark
JVMs run concurrently. Results and source hashes are preserved in:

- [ScopedValue forward](2026-09-11-scoped-value-forward.edn)
- [ScopedValue reverse](2026-09-11-scoped-value-reverse.edn)
- [ThreadLocal forward](2026-09-11-thread-local-forward.edn)
- [ThreadLocal reverse](2026-09-11-thread-local-reverse.edn)

Reproduce with the usual `:bench-runtime` options, for example:

```sh
clojure -T:build compile-java
clojure -M:bench-runtime '{:output "target/bench/java.edn"}'
clojure -J-Dco.multiply.scoped.force-fallback=true -M:bench-runtime '{:reverse? true :output "target/bench/java-thread-local-reverse.edn"}'
clojure -M:bench-runtime '{:compare ["bench/baselines/2026-09-11-scoped-value-forward.edn" "target/bench/java.edn"]}'
```

### scoped-value

| Case | Clojure ns/call | Java ns/call | Java/Clojure | Clojure B/call | Java B/call |
| --- | ---: | ---: | ---: | ---: | ---: |
| `control/return` | 2.2–5.6 | 1.9–5.8 | 0.85–1.04 | 0 | 0 |
| `current/unbound` | 4.7–10.0 | 3.5–9.1 | 0.74–0.91 | 0 | 0 |
| `current/empty` | 5.7–7.0 | 4.9–6.0 | 0.87 | 0 | 0 |
| `current/bound` | 6.5–6.7 | 6.0–6.1 | 0.91–0.92 | 0 | 0 |
| `ask/hit` | 9.4–14.7 | 14.2–14.5 | 0.96–1.55 | 0 | 0 |
| `ask/hit-default` | 8.9–15.4 | 13.8–14.4 | 0.90–1.62 | 0 | 0 |
| `ask/hit-large` | 13.1–13.9 | 12.3–15.0 | 0.89–1.15 | 0 | 0 |
| `ask/root` | 17.8–19.6 | 18.2–20.5 | 0.93–1.15 | 0 | 0 |
| `ask/root-default` | 13.9–17.0 | 18.4–28.0 | 1.33–1.65 | 0 | 0 |
| `ask/unbound-default` | 14.3–17.6 | 19.0–20.9 | 1.19–1.33 | 0 | 0 |
| `ask/nil` | 14.8–15.4 | 19.9 | 1.29–1.35 | 0 | 0 |
| `ask/false` | 8.9 | 14.2–14.3 | 1.60–1.61 | 0 | 0 |
| `build/zero` | 5.3 | 5.2–5.6 | 0.99–1.08 | 0 | 0 |
| `build/one` | 12.3–12.9 | 11.9 | 0.92–0.97 | 56 | 56 |
| `build/two` | 82.9–83.3 | 79.9–81.4 | 0.96–0.98 | 168 | 168 |
| `build/nine` | 410.4–455.6 | 386.8–389.1 | 0.85–0.94 | 728 | 688 |
| `build/ten` | 473.4–488.1 | 411.1–411.8 | 0.84–0.87 | 824 | 688 |
| `entry/with-scope` | 15.4–17.2 | 14.6–16.4 | 0.95–0.96 | 56–72 | 56–72 |
| `entry/with-scope-captured` | 16.0–17.9 | 15.2–16.9 | 0.94–0.95 | 56–80 | 56–80 |
| `entry/with-scope-read` | 31.4–35.5 | 34.5–36.0 | 0.97–1.15 | 72 | 72 |
| `entry/scoping-zero` | 54.5–59.6 | 69.9–70.7 | 1.17–1.30 | 72 | 72 |
| `entry/scoping-one` | 46.6–54.4 | 48.4–52.6 | 0.89–1.13 | 128 | 128 |
| `entry/scoping-captured` | 45.7–59.0 | 46.3–51.4 | 0.79–1.12 | 136 | 136 |
| `entry/scoping-two` | 81.6–86.4 | 86.4–87.2 | 1.00–1.07 | 240 | 240 |
| `entry/scoping-ten` | 558.1–579.4 | 461.9–464.8 | 0.80–0.83 | 896 | 760 |
| `entry/skip-inherited` | 46.8–49.0 | 45.4–46.4 | 0.95–0.97 | 72 | 72 |
| `entry/skip-absent` | 38.3–48.5 | 40.9 | 0.84–1.07 | 80 | 80 |
| `nested/override` | 171.3–171.6 | 118.5–119.1 | 0.69 | 264 | 264 |
| `capture/restore` | 58.9–59.0 | 56.1–57.1 | 0.95–0.97 | 152 | 152 |
| `workload/read-100` | 1113.3–1153.7 | 1113.3–1144.4 | 0.99–1.00 | 112–136 | 112–136 |

### thread-local

| Case | Clojure ns/call | Java ns/call | Java/Clojure | Clojure B/call | Java B/call |
| --- | ---: | ---: | ---: | ---: | ---: |
| `control/return` | 2.1–5.6 | 2.1–5.6 | 0.99–1.00 | 0 | 0 |
| `current/unbound` | 3.6–6.6 | 3.7–6.6 | 1.01 | 0 | 0 |
| `current/empty` | 5.5–6.9 | 5.5–6.5 | 0.93–0.99 | 0 | 0 |
| `current/bound` | 6.5–6.6 | 6.6 | 1.00–1.01 | 0 | 0 |
| `ask/hit` | 9.3–15.8 | 9.7–15.1 | 0.61–1.61 | 0 | 0 |
| `ask/hit-default` | 8.7–15.4 | 14.9–15.2 | 0.96–1.74 | 0 | 0 |
| `ask/hit-large` | 13.9–14.4 | 13.7–15.1 | 0.99–1.05 | 0 | 0 |
| `ask/root` | 13.8–14.6 | 9.8–18.0 | 0.71–1.24 | 0 | 0 |
| `ask/root-default` | 13.5–13.8 | 17.6–17.7 | 1.28–1.30 | 0 | 0 |
| `ask/unbound-default` | 13.8–14.4 | 18.2–18.5 | 1.27–1.34 | 0 | 0 |
| `ask/nil` | 15.3–17.8 | 20.7–20.9 | 1.17–1.35 | 0 | 0 |
| `ask/false` | 8.8–10.8 | 14.9–15.2 | 1.41–1.69 | 0 | 0 |
| `build/zero` | 5.2–5.3 | 5.3 | 0.99–1.01 | 0 | 0 |
| `build/one` | 12.5–20.2 | 12.1–12.2 | 0.60–0.98 | 56 | 56 |
| `build/two` | 82.6–82.7 | 82.9–83.0 | 1.00–1.01 | 168 | 168 |
| `build/nine` | 412.9–423.3 | 506.1–554.7 | 1.23–1.31 | 728 | 728 |
| `build/ten` | 523.4–616.3 | 539.6–583.8 | 0.95–1.03 | 824–936 | 728 |
| `entry/with-scope` | 9.8–9.9 | 9.6–9.7 | 0.98–1.00 | 0 | 0 |
| `entry/with-scope-captured` | 10.1 | 10.1–10.6 | 1.01–1.05 | 0 | 0 |
| `entry/with-scope-read` | 13.7–16.1 | 19.4 | 1.20–1.42 | 0 | 0 |
| `entry/scoping-zero` | 14.5–17.2 | 20.5–22.2 | 1.29–1.41 | 0 | 0 |
| `entry/scoping-one` | 28.7–31.2 | 29.1–29.4 | 0.94–1.02 | 56 | 56 |
| `entry/scoping-captured` | 28.6–32.5 | 52.5–54.2 | 1.61–1.90 | 56 | 56 |
| `entry/scoping-two` | 66.4–70.4 | 72.6–74.8 | 1.03–1.13 | 168 | 168 |
| `entry/scoping-ten` | 571.0–608.3 | 559.9–640.0 | 0.98–1.05 | 824–936 | 728 |
| `entry/skip-inherited` | 17.4–20.8 | 20.2–20.5 | 0.97–1.18 | 0 | 0 |
| `entry/skip-absent` | 20.4–26.3 | 21.2–24.2 | 0.81–1.18 | 0 | 0 |
| `nested/override` | 66.2–74.8 | 63.7–72.8 | 0.85–1.10 | 112 | 112 |
| `capture/restore` | 22.3–22.5 | 22.6–25.2 | 1.01–1.13 | 0 | 0 |
| `workload/read-100` | 915.9–1123.2 | 1057.9–3086.9 | 0.94–3.37 | 56 | 56 |

### Argument array versus fixed arities

Forward runs only; the array prototype has one fork per backend. These arrays
carry the arguments to Java; both versions still store scopes in persistent maps.

| Backend / case | Array ns/call | Fixed ns/call | Array B/call | Fixed B/call |
| --- | ---: | ---: | ---: | ---: |
| scoped-value / `build/nine` | 493.3 | 386.8 | 816 | 688 |
| scoped-value / `build/ten` | 546.8 | 411.8 | 824 | 688 |
| scoped-value / `entry/scoping-ten` | 566.2 | 461.9 | 896 | 760 |
| thread-local / `build/nine` | 528.7 | 554.7 | 808 | 728 |
| thread-local / `build/ten` | 556.9 | 583.8 | 928 | 728 |
| thread-local / `entry/scoping-ten` | 576.0 | 640.0 | 928 | 728 |

Ranges span the two JVM means, not confidence intervals. Time ratios compare
matching case orders (Java / Clojure); less than 1 means faster. Small timings
are sensitive to compilation and call-site profiles, and real Var identity
hashes can change map shape and allocation between processes. Compare the
per-run samples before attributing a small difference to the implementation.

## Read-path diagnosis

A subsequent pair of ScopedValue runs enabled HotSpot `LogCompilation` and
selected only the eight `ask` cases. These used shorter sampling for inspecting
compilation decisions; their timings do not replace the four full runs above.
The original CLJ sources were checked against commit `0b3dd15`. The Java source
hashes were unchanged from the measured prototype.

Both implementations use `Map.getOrDefault`. The defaulted Clojure `ask`
previously expanded the lookup and fallback into each caller. The Java version
routes them through a shared `lookup(Var)`, then tests its result against
`UNBOUND` in the caller. A scoped hit therefore has an additional sentinel
comparison in the source, though its final machine-code cost is not isolated.

The diagnostic C2 logs show that `lookup` and the carrier access are inlined.
They also show different decisions further down the read path:

- In the original defaulted root-read caller (compilation 4105), `Var.deref`
  is inlined. In the corresponding Java caller (4122), it is rejected with
  `already compiled into a big method`.
- The later Java defaulted read callers inherit a two-type map receiver profile
  from `lookup`: both `PersistentArrayMap` and `PersistentHashMap`. Their
  Clojure counterparts have a single map target at their own call site.
- Some map operations fail to inline in both versions. An inlining rejection
  by itself therefore does not establish the source of a measured regression.

This supports loss of call-site specialization and changed inlining decisions
as an explanation, especially for defaulted reads. It does not assign a cost
to each change or establish the cause of every regression. HotSpot's
[inlining policy](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/opto/bytecodeInfo.cpp)
includes limits based on the size of already compiled methods.

To repeat the diagnostic, run this on each source version with separate output
paths (compile Java first for the prototype):

```sh
clojure -J-XX:+UnlockDiagnosticVMOptions -J-XX:+LogCompilation -J-XX:LogFile=target/read-jit.xml -M:bench-runtime '{:groups [:ask] :output "target/read-diagnostic.edn" :criterium {:samples 6 :warmup-jit-period 1000000000 :target-execution-time 20000000 :bootstrap-size 100}}'
```

The [Clojure-read comparison](../clojure-reads/README.md) follows up this
hypothesis while retaining the Java carrier. No runtime changes were made
during the diagnostic itself.

## Next storage experiment

A JVM-specific immutable flat array of Var/value pairs is a plausible candidate
for small scopes. The current Clojure 1.12.5 implementation already uses
`PersistentArrayMap` for up to eight entries when growing from empty. Its
transient form batches updates, but copies into a working array and copies again
when made persistent. It transitions to a hash map when that capacity is
exceeded. A dedicated representation could avoid transient bookkeeping and the
final copy, specialize Var lookup, and retain flat storage at a different size.
These are opportunities to measure, not measured gains.
See the [Clojure 1.12.5 source](https://github.com/clojure/clojure/blob/clojure-1.12.5/src/jvm/clojure/lang/PersistentArrayMap.java).

The decisive size is the total inherited scope, not just the number of new
bindings. Flat arrays copy and scan linearly. A hash trie shares most of the
parent when extending a large scope, so a flat-array/hash-trie hybrid is worth
comparing with both pure alternatives. Each published array must remain
immutable so capture and restoration keep their current semantics.

Hold the carrier and entry code fixed for this comparison. Extend the benchmark
matrix to parent sizes 0, 4, 8, 16, 32 and 64, with one, two and eight bindings
per extension; cover additions, overrides, skips, early/late hits and misses,
and one-read versus read-heavy bodies. Measure allocations as well as time.
The existing suite does not cover enough inherited sizes to select a storage
threshold.

`current-scope`, `assoc-scope` and `with-scope` currently expose maps. Preserve
that contract with a map-compatible representation or measure any conversion
cost at those boundaries. CLJS can retain its existing persistent maps; shared
tests should check the public map behavior, nil/false/skip, duplicate bindings,
evaluation order, nesting and capture without requiring identical internals.
