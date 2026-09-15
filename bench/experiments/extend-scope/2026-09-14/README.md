# Scope-extension call shape, JDK 25

Removing the argument array reduced allocation in every measured workload.
Unrolled Java helpers improved full-run timing in three of four cases, but the
array-and-loop path was faster for 11 overrides in a 32-entry map. A second
standard Criterium pass confirmed that exception. Fully inline transient
operations showed no consistent advantage over the small Java helper calls.

These results support helper-based unrolling as an allocation-saving candidate,
not a universal latency improvement. The runtime and production macro were not
changed by this experiment.

## Full measurements

Local Amazon Corretto 25.0.2+10-LTS, Clojure 1.12.5, Criterium 0.4.6, macOS
aarch64, 512 MiB heap. Standard `criterium/bench`: ten-second warm-up, 60 samples
targeting one second, ordinary overhead estimation/subtraction. Every table
entry comes from a separate fresh JVM; all JVMs ran serially.

Time is **nanoseconds per complete multi-binding map update**:

| Workload | Parent entries | Bindings | Array + Java loop | Unrolled Java helpers | Inline transients |
| --- | ---: | ---: | ---: | ---: | ---: |
| Add | 8 | 11 | 738.68 | 609.48 | 610.40 |
| Add | 8 | 20 | 1148.15 | 1044.53 | 1102.70 |
| Override | 32 | 11 | 343.49 | 382.02 | 366.57 |
| Override | 32 | 20 | 566.92 | 533.42 | 570.33 |
| Override, repeated | 32 | 11 | 338.42 | 367.41 | 358.66 |

The 11-override repeat reversed the first full pass's model order, from
inline/helpers/array to array/helpers/inline. The array path remained faster:
roughly 29–39 ns versus helpers, or 20–23 ns versus inline. This result contradicts
the short-run ranking for this workload, so warm-up/protocol matters.

Helpers reduced full-run time by approximately 17.5% for adding 11 bindings,
9.0% for adding 20, and 5.9% for overriding 20. Inline was essentially tied with
helpers for adding 11 and slower for both 20-binding cases. These measurements
do not identify the specific JIT decisions responsible for the differences.

Allocation is **bytes per complete map update**, rounded to whole bytes:

| Workload | Bindings | Array + Java loop | Unrolled Java helpers | Inline transients |
| --- | ---: | ---: | ---: | ---: |
| Add to 8 | 11 | 1448 | 1384 | 1344 |
| Add to 8 | 20 | 3280 | 3104 | 3144 |
| Override in 32 | 11 | 936 | 832 | 832 |
| Override in 32 | 20 | 1384 | 1208 | 1208 |

The 11-override repeat reproduced the same allocations. The transport arrays
would occupy 104 bytes for 22 slots and 176 bytes for 40 slots on this JVM.
Observed net savings were 64–176 bytes: additional 40-byte allocation differences
appeared between some models/runs. No allocation-site profile was collected to
attribute those differences. Allocation used ThreadMXBean after timing, in
three batches of 30,000 operations.

## Bytecode

These are `invokeStatic` method sizes for the actual timed kernels, including
their common fixture-value loads and excluding shared callee bodies:

| Bindings | Array + Java loop | Unrolled Java helpers | Inline transients |
| --- | ---: | ---: | ---: |
| 11 | 502 bytes | 415 bytes | 734 bytes |
| 20 | 898 bytes | 739 bytes | 1319 bytes |

Disassembly verifies that the helper kernels call `assocTransient` once per
binding, while the inline kernels contain the SKIP checks and direct transient
interface calls. Neither unrolled variant constructs a transport array or uses
reflection. The production baseline emits 22 or 40 `RT.aset` calls. Inline SKIP
branches make the fully inline methods larger than either alternative.

## Scope and validation

The [benchmark description and commands](../README.md) specify the kernels,
fixture lifecycle and correctness checks. There are 24 short-run JVM records,
12 initial full records and three full repeat records. The short protocol used
three seconds of warm-up and 16 samples targeting 50 ms, with two forks and
reversed model order in the second fork. It generally favored unrolling, but
did not predict the full-run 11-override result.

Each workload uses one set of named Vars. The 128 rotating fixtures vary values,
not key-hash layouts. All active incoming Var hashes matched across the short
runs for each workload. Full runs additionally recorded every parent key hash;
complete fixture hashes matched between models and the repeated case.

All 39 records passed checks for positive samples, expected sample/batch counts,
matching plan/JVM/source/class fingerprints within each run, and source hashes
matching the archived snapshots. Full and repeat sources/classes also matched
each other. The only source change between short and full protocols added the
complete fixture-hash metadata to the runner; timed kernel sources are identical.

Correctness checks covered map contents, parent and retained-result immutability,
array-map promotion, nil/false, partial/all SKIP, duplicate bindings, once-only
input evaluation, and evaluation order around invalid parents and input errors.

These are isolated map-extension operations. Fixture construction, value-object
allocation, ScopedValue entry, callbacks, scheduling and concurrency are outside
timing. They do not measure Quiescent throughput or the dominant single-binding
update. The results also do not establish performance across other Var/hash
layouts or larger caller bodies.

## Recorded artifacts

- [Full comparison, including mean confidence intervals](full/comparison.md)
- [Repeated 11-override comparison](repeat/comparison.md)
- [Short-run comparison](screen/comparison.md)
- Raw EDN, logs and plans in each of those directories
- [Short-run source snapshot](screen-sources.zip) and [full/repeat source snapshot](full-sources.zip)
- [Bytecode sizes and call counts](bytecode.json), with six adjacent `.javap` files
- [JVM object-layout flags](jvm-layout.txt)
- [Validation log](validation.log)
