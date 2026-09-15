# Bifurcan versus Clojure maps — local JDK 25, 2026-09-14

These measurements favor keeping Clojure maps for the Quiescent workload:
repeatedly overriding one task binding with otherwise stable context. Bifurcan
is a credible alternative for some other operation mixes, especially growth
across Clojure's eight-entry array-map boundary, but it costs more time and
allocation in the dominant single-update cases measured here.

## Standard Criterium confirmation

Times and bytes are per operation. The child-chain row is for all ten updates,
with every intermediate snapshot retained. Lookup allocation is effectively
zero; the approximately 0.005 B/call harness allocation rounds to 0.01 here.

| Operation | Clojure ns | Bifurcan ns | Clojure B | Bifurcan B |
| --- | ---: | ---: | ---: | ---: |
| Override one, parent 1 | 14.90 | 27.70 | 56.01 | 144.01 |
| Override one, parent 8 | 18.59 | 34.11 | 112.01 | 237.94 |
| Override one, parent 32 | 44.05 | 65.63 | 283.19 | 521.26 |
| Ten child updates, parent 8 | 146.96 | 284.21 | 1120.01 | 2379.38 |
| Existing lookup, parent 16 | 16.59 | 14.04 | 0.01 | 0.01 |
| Four overrides + four additions, parent 8 | 553.56 | 293.77 | 889.19 | 579.69 |
| Add ninth entry | 285.69 | 52.68 | 493.75 | 305.13 |

For one-binding overrides, Bifurcan took **1.49–1.86× as long** and allocated
**1.84–2.57× as much** across the three confirmed sizes. The ten-child chain on
an eight-entry parent took **1.93× as long**, with **2.12× the allocation**.
At eight entries, the difference is approximately 126 extra bytes per update,
or 126 MB per million updates. That is allocated volume, not retained memory
or a measurement of GC pauses/application throughput.

Bifurcan's strongest confirmed result was adding the ninth entry: **5.42× faster**
and about **38% less allocation**. The mixed batch growing from eight to twelve
entries was **1.88× faster** with about **35% less allocation**. An existing
lookup at size sixteen was about **15% faster**, a difference of 2.55 ns.
These benefits matter for a different operation mix; growth past eight happens
occasionally, while the bookkeeping binding is overwritten for each child.

## Broad scan

The [short scan](screen/summary.md) covered all 25 workloads in 50 fresh JVMs:

- Clojure won single-binding overrides at 1, 4, 8, 9, 16 and 32 entries in both
  time and allocation, and won all three ten-child chains (sizes 1, 8 and 32).
- Bifurcan won existing lookups at 9 and 16 entries and misses at 8 and 32.
  The other existing-lookup cases favored Clojure, sometimes by a small margin.
- Bifurcan won the ninth-entry addition and mixed batches growing an eight-entry
  parent, both in time and allocation.
- On a 32-entry parent, Bifurcan's mixed batches were faster but allocated more.
  Those batch results have only the short protocol; they are not full confirmations.
- Clojure won the mixed batches on a one-entry parent and construction of eight
  entries from empty. The eight-binding batch on a one-entry parent includes
  repeated updates to the one existing key; it is not eight distinct additions.

Only the seven rows above received standard `criterium/bench` confirmation.
Do not compare absolute short/full times as a code change: warm-up and overhead
subtraction differ, and JIT decisions and Var hash layouts can affect allocation.

## Reproduction and limits

Local aarch64 macOS 26.3.1, 10 reported processors. Amazon Corretto
**25.0.2+10-LTS**, Clojure **1.12.5**, Criterium **0.4.6**, Bifurcan
**0.2.0-rc1**. Every worker used `-Xms512m -Xmx512m` and
`-XX:-OmitStackTraceInFastThrow`. This is a local comparison, not a comparison to
any Totem baseline. There was one fresh JVM per model/case/protocol, run serially.
Model order alternated by case; the full pass started in the reverse model order.

The scan used 3 s warm-up, 16 samples targeting 50 ms, 300 bootstrap samples and
no overhead subtraction. Full confirmation used ordinary `criterium/bench`
defaults: 10 s warm-up, 60 samples targeting 1 s, 1,000 bootstrap samples and
normal overhead estimation/subtraction. Allocation was measured afterward using
ThreadMXBean in three batches of 30,000 operations. Raw results preserve every
allocation batch and timing sample. The complete scan took 4.1 minutes and the
14 full workers took 21.3 minutes, excluding setup and compilation.

Var identity hashes differed between models in 18/25 scan pairs and
3/7 full pairs. Every worker records its complete hash vector and rotates
through 128 fixtures. The independent scan/full runs support the broad rankings,
but these are not many repeated full forks over a population of key layouts.
Criterium's intervals below describe sampling within each full run, not all
variation between processes, hash layouts or machines.

| Operation | Clojure mean interval, ns | Bifurcan mean interval, ns |
| --- | ---: | ---: |
| Override one, parent 1 | 14.80–15.18 | 27.50–28.15 |
| Override one, parent 8 | 18.46–18.78 | 33.86–34.49 |
| Override one, parent 32 | 43.75–44.54 | 65.19–66.30 |
| Ten child updates, parent 8 | 146.01–148.48 | 282.95–286.43 |
| Existing lookup, parent 16 | 16.49–16.75 | 13.96–14.19 |
| Four overrides + four additions, parent 8 | 550.17–560.66 | 292.53–296.25 |
| Add ninth entry | 284.79–287.70 | 52.42–53.20 |

Both models use direct Java calls with real Var keys, prebuilt inputs and
persistent parents. All fixture checks passed, along with 500 branching updates
from retained ancestors for each model, including nil, false, missing bindings,
SKIP and duplicate updates. Bifurcan batches start a fresh linear builder and
discard it after publishing a forked map. This does not measure carriers, macro
expansion, Var-root fallback, task allocation, scheduling or concurrent GC.
See the [experiment description](../README.md) for the exact execution shapes.

Artifacts:

- [Screen plan](screen/plan.edn) and [screen summary](screen/summary.md).
- [Full plan](full/plan.edn) and [full summary](full/summary.md).
- Individual `.edn` results and `.edn.log` files beside each summary.
- [Exact measured sources and dependency/build configuration](sources.zip).

All 64 results have identical source/class/dependency fingerprints and JDK
settings. The archive was checked against those fingerprints. A six-worker
harness pilot that stopped on a strict identity-hash equality check is retained
under `target/bench/bifurcan/validation-pilot`, outside this result set.

Run the commands in the [experiment README](../README.md), using a fresh output
directory and the desired `:specs`, `:protocol` and model order from the plans.
