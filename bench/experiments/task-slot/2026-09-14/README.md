# Separate task bookkeeping: measurements, 2026-09-14

Separating the task from the application map substantially reduced allocation
in these kernels. It did **not** produce a consistent elapsed-time improvement.
The cached application carrier is worth an integrated allocation experiment,
but the measurements do not justify claiming that splitting the task makes
Quiescent faster.

Follow-up decision: **defer the separate-task ScopedValue design**. Keep the
combined map and existing capture/restore semantics. A separate task slot would
need additional integration to preserve parentage when a captured scope is
restored through other execution mechanisms, such as a Clojure future. The
allocation savings and uncertain timing benefit do not currently justify that
complexity. The measurements remain available if this tradeoff is revisited.

The experiment uses actual JDK **25.0.2+10-LTS**, Clojure **1.12.5**, Criterium
**0.4.6**, macOS aarch64, and a fixed 512 MiB heap. See the
[workloads and commands](../README.md). No production Scoped or Quiescent code
was changed. All reported times and allocations are per **ten-child sequence**.

## Standard Criterium runs

Each cell below is **nanoseconds / allocated bytes** per sequence. These six
fresh JVM runs use standard `criterium/bench` defaults: ten-second warm-up,
60 samples targeting one second each, and normal overhead subtraction. There
is one full JVM run per model and execution shape.

The application map has eight entries. The combined map additionally contains
the task, giving nine entries: it is a PersistentHashMap. The split variants'
application map remains an eight-entry PersistentArrayMap. Both represent the
same application bindings and task identity.

| Execution shape | One map, one assoc per child | Separate task, fresh carrier | Separate task, cached app carrier |
| --- | ---: | ---: | ---: |
| Ten nested calls | 538.44 / 2240.01 | 835.26 / 1000.01 | 781.07 / 680.01 |
| Ten capture/restore entries | 532.46 / 2480.01 | 744.23 / 886.31 | 494.47 / 793.04 |

The fresh split variant saved about **55%** of allocation in the nested case
and **64%** in capture/restore, while taking about **55%** and **40%** more time,
respectively. The cached variant saved about **70%** and **68%** of allocation.
It was about **45% slower** in nested calls and **7% faster** in capture/restore
in these full runs. That last timing advantage was not reproduced by the short
runs, so it should not be treated as an established speedup.

For scale, the cached capture/restore result saved about **169 bytes per child**
relative to the map result. This measures ScopedValue/map work and capturing
Java callbacks; it excludes task objects, Clojure IFn adapters and scheduling.
It is not the allocation of a complete Quiescent closure.

The [full comparison](full/comparison.md) links the values to a consistent
table format; [raw measurements and logs](full/) preserve every sample,
allocation batch, JVM setting and source/class fingerprint.

## Exploratory repeated JVMs

The [screen comparison](screen/comparison.md) contains two fresh JVMs for each
model, shape and application-map size: **48 runs** in total, with sizes 0, 7, 8
and 32, and reversed model order in the second fork. It reports both individual
JVM means as well as their arithmetic mean. Seven application entries exercise
the combined map just below the array-map promotion boundary; eight exercise
it just above. All variants still perform only one task update per child.

Allocation savings persisted across the tested sizes. Timing did not follow
allocation monotonically. For example, the two fresh-split nested JVMs with
eight application entries measured approximately **534 ns** and **959 ns**,
versus **503 ns** and **505 ns** for the map. The cached capture/restore JVMs
measured **659 ns** and **711 ns**, whereas the longer run measured **494 ns**.

The 32-entry capture/restore screen favored both split variants for time and
allocation, but that size has not had a standard Criterium confirmation.
Screen/full differences may reflect compilation, warm-up or between-JVM
variation; no diagnostic run established a cause. The small number of forks
cannot establish a precise or universal timing improvement. Allocation counts
also varied somewhat between JVMs; raw batches are retained rather than rounded
to a theoretical object-size estimate.

## Interpretation and verification

The proposed nested shape is useful, but a separate capture/restore shape is
necessary for this comparison: nested bindings overlap in time, while these
capture/restore entries exit one binding before entering the next. Even the
nested split kernel re-establishes both context and task at every child, as an
async entry would. It does not model binding only the task and relying on a
single enclosing context binding for the whole sequence.

Neither shape launches threads. These measurements do not establish the GC,
throughput or latency effects of many concurrent Quiescent tasks. The next
integration would need to measure those effects, preserve scope capture and
task-parent semantics, and account for any new Clojure/Java interface costs.

All three models passed checks for the eight default workloads and additional
depth-one and depth-50 fixtures, including 64 application entries. Checks cover
all application keys, nil/false task bindings, retained snapshots, unchanged
parents, shared application-map identity, outer-scope restoration, and exception
propagation/restoration. Every worker repeated verification before measuring.

All **54 results** were checked against their plans for expected jobs, matching
source/class/JVM fingerprints, protocols, complete positive timing samples,
and three allocation batches. The [source archive](sources.zip) contains the
exact measured Java and Clojure runner plus build/dependency configuration.
Its four source hashes match both measurement plans. The experiment and its
compiled classes remain outside the library JAR.
