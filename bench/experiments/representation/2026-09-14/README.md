# Standalone representation measurements, 2026-09-14

Measured on JDK **25.0.2+10-LTS**, Clojure **1.12.5**, macOS aarch64, with a
512 MiB fixed heap. Each row is a separate JVM running standard
`criterium/bench` 0.4.6 defaults, followed by three million invocations for
thread-allocated-byte measurements. Processes ran serially. See the
[protocol and commands](../README.md).

## Original representation comparison

Cells are **nanoseconds / allocated bytes per operation**. Lookup is identical
for both Clojure update strategies, so it was measured only once.

| Operation | Clojure persistent | Clojure transient batch | VarScope prototype |
| --- | ---: | ---: | ---: |
| Lookup, 4 bindings | 5.27 / 0 | — | 4.14 / 0 |
| Lookup, 32 bindings | 10.50 / 0 | — | 11.97 / 0 |
| Extend 0 → 2 | 31.46 / 120 | 49.37 / 144 | 17.98 / 56 |
| Extend 4 → 6 | 57.56 / 184 | 104.40 / 200 | 25.75 / 88 |
| Extend 7 → 9 | 309.29 / 602.75 | 361.45 / 735.56 | 267.81 / 433.63 |
| Extend 32 → 34 | 96.08 / 569.94 | 120.70 / 459.50 | 113.42 / 457.31 |
| Nested scopes starting at 4 | 111.01 / 384 | 158.73 / 408 | 53.23 / 184 |

The prototype substantially reduced time and allocation for these small batch
constructions and the nested operation. Its lookup advantage at size four was
only about one nanosecond, and it was slower for the 32-binding lookup. The
large-map update also illustrates a tradeoff: persistent associations were
fastest, while the transient-based implementations allocated less.

The seven-to-nine case compares different promotion strategies as well as small
representations. The prototype promotes directly through a transient hash-map
builder; ordinary persistent associations first grow to eight, then promote on
the ninth distinct binding. This is not an isolated measurement of Var identity
comparison or array copying.

The rebuild control adds another distinction: it uses public `containsKey` and
`kvreduce` calls, whereas the prototype searches its owned array by identity and
copies it directly. A timing gap between them cannot be attributed to copying
alone without a more focused measurement.

The [original summary](original/summary.md), all 19 raw `.edn` results, their
verbose `.log` reports, and `plan.edn` are retained in [original/](original/).
The [original source snapshot](original-sources.zip) contains the sources,
`build.clj` and `deps.edn` used for that matrix. The harness subsequently gained
an optional rebuild model and two extra cases; the original matrix was complete
before those source changes were applied.

## Rebuild a Clojure array map in one batch

This follow-up retains ordinary Clojure maps and compares repeated `assoc` with
one final array-map construction through public APIs. These eight runs use fresh
baselines after adding the control; they are not pooled with the original rows.
Cells are again **nanoseconds / allocated bytes per operation**.

| Operation | Repeated `assoc` | Rebuild Clojure array map |
| --- | ---: | ---: |
| Extend 0 → 2 | 31.24 / 120 | 22.37 / 64 |
| Extend 4 → 6 | 57.57 / 184 | 55.68 / 96 |
| Extend 6 → 8 | 72.87 / 216 | 71.38 / 112 |
| Override 2 values within 6 | 43.18 / 192 | 49.59 / 96 |

Rebuilding from empty was faster and allocated less. For existing parents,
construction into six or eight bindings had almost the same elapsed time as
repeated `assoc`, while allocating about half as much. Two overrides within six
bindings were slower with rebuilding despite the halved allocation.

This supports trying bulk construction from empty and considering allocation as
a separate objective. It does **not** support replacing every small-map extension
with rebuilding for a general CPU-time win. A single `assoc` already makes at
most one backing-array copy, so this proposal concerns batches. Retaining the
Clojure representation is a strong baseline; the custom store's larger small-map
construction win remains interesting if that cost dominates real workloads.

All eight raw results, verbose reports and the plan are in [rebuild/](rebuild/).
The [rebuild source snapshot](rebuild-sources.zip) matches the recorded source
fingerprints. Original baseline and follow-up baseline timings were very close
for the two repeated cases, but each comparison still has only one JVM per row.

## Limits of the inference

These are one-fork measurements, not independently replicated estimates across
JVMs or machines. Criterium's within-run confidence intervals and samples are in
the raw EDN. In particular, do not overinterpret differences of a nanosecond.

The experiment excludes the carrier, macro expansion, root fallback and argument
array construction. All models receive prebuilt argument arrays through matching
Java kernels. Consequently it establishes a promising construction strategy,
not a measured improvement to the integrated fixed-arity `scoped` API. The
prototype also lacks most of the Clojure map interface; any decision to replace
the representation must account for that extra implementation and compatibility
work.

Compilation used `--release 17`. The final four-model correctness checks passed
on actual JDK 17 and JDK 25, including retained snapshots, random branched
updates, promotion, duplicate keys, skipped updates, nil/false, and caller-array
mutation. No library runtime implementation was changed for this experiment.
