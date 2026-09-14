# Standalone scope representation experiment

This experiment compares a small Var-keyed store with Clojure maps without
loading `scoped`, its macros, or either carrier. It answers whether changing the
representation looks promising before paying the cost of integration.

Measured results: [JDK 25 comparison, 2026-09-14](2026-09-14/README.md).

Follow-up: [persistent/transient crossover by batch size](BATCH.md).

## Implementations

- **clojure-persistent:** Clojure array/hash maps, extending with ordinary
  persistent associations. Lookup uses `ILookup.valAt(key, notFound)` directly.
- **clojure-transient:** the same map representations and lookup, using a
  transient batch for two or more bindings and persistent association for one.
- **var-scope:** an immutable array of alternating Var keys and values for up to
  eight bindings, with identity comparisons. A two-pass bulk extension counts
  new distinct keys, allocates one correctly sized array, then applies updates.
  Above eight bindings it wraps a Clojure persistent hash map and uses transient
  batches. Single associations have their own copy-on-write path.

The prototype is in [VarScope.java](java/co/multiply/scoped/experiment/VarScope.java).
The matching Java adapters and kernels are in
[RepresentationBench.java](java/co/multiply/scoped/experiment/RepresentationBench.java).
All implementations receive the same Var keys, value shapes and borrowed
argument-array convention. None retains or mutates that argument array.

The cutoff of eight is deliberately the same as Clojure's ordinary array-map
growth cutoff. Large stores reuse the persistent hash map rather than introducing
a new hash-table implementation. Lookup only benchmarks the Clojure map once;
its representation is the same regardless of the update strategy.

This prototype implements only count, lookup and extension. It is not a complete
replacement for a Clojure map: metadata, sequence/iteration, equality, hashing,
serialization and public API compatibility are not implemented or benchmarked.
Any improvement from bulk construction may also be available while retaining a
Clojure map as the output. These comparisons alone cannot attribute a gain
exclusively to Var identity comparisons or establish an integration win.

### Rebuilding an ordinary array map

The opt-in **clojure-rebuild** control in
[ArrayMapBatch.java](java/co/multiply/scoped/experiment/ArrayMapBatch.java) keeps
Clojure maps throughout. For a batch that leaves at most eight bindings, it counts
new distinct keys, copies the parent with the public `kvreduce` API into one fresh
array, applies the updates, and calls the `PersistentArrayMap` constructor. It
preserves metadata, resolves duplicate keys in order, and omits skipped updates.
The constructor retains its argument array, so the control owns that array and
never mutates it after construction. It does not access Clojure's private backing
array or use reflection. Single updates and larger results use ordinary `assoc`.
For a single update, ordinary `assoc` already creates at most one new backing
array; the proposed saving is avoiding intermediate copies across a batch.

In [Clojure 1.12.5's source](https://github.com/clojure/clojure/blob/clojure-1.12.5/src/jvm/clojure/lang/PersistentArrayMap.java),
ordinary persistent array-map growth allows **eight key/value
bindings** (16 array slots); the ninth distinct key triggers promotion. Explicit
array-map construction can exceed that size, but this control deliberately does
not do so. Overrides do not increase the binding count.

## Operations

| Case | Work per measured invocation |
| --- | --- |
| `lookup/small` | One lookup in a 4-binding store |
| `lookup/large` | One lookup in a 32-binding store |
| `extend/empty` | Add two bindings to an empty store |
| `extend/small` | Add two bindings to a 4-binding store |
| `extend/crossover` | Add two bindings to a 7-binding store, crossing the array/hash boundary |
| `extend/large` | Add two bindings to a 32-binding store |
| `nested/small` | Extend 4 bindings to 6, capture that snapshot, make an inner scope with an override and one new binding, then read the captured parent |

Each kernel rotates through 128 prebuilt input frames. Lookup cycles evenly
through explicit nil, false, a non-null hit and a miss. The frames use different
Vars and values, spreading hash-map layout effects across many maps rather than
relying on a single constant fixture. A shared cursor/input-selection cost is
included. Fixtures and argument arrays are allocated before measurement.

Extension results escape through Criterium's result sink. The nested kernel
stores both snapshots and the restored-parent read in a preallocated output
array. Capture/restore here means retaining and reusing an immutable store
reference; it does not establish a ScopedValue binding. Capturing a reference
alone is not given a separate benchmark.

## Protocol and commands

Use an actual JDK 25 installation for the measurement process:

```sh
JAVA_HOME=/path/to/jdk-25 clojure -T:build compile-representation
JAVA_HOME=/path/to/jdk-25 clojure -M:bench-representation \
  '{:output "target/bench/representation/my-run"}'
```

The alias replaces the project source paths with the experiment's Clojure sources
and compiled Java classes. Its classes live under
`target/bench/representation/classes` and do not enter the library JAR.

The runner calls **`criterium/bench` with unmodified 0.4.6 defaults**: 10-second
JIT warm-up, 60 samples targeting one second each, bootstrap size 1000 and the
normal overhead estimation/subtraction. Only the reporting function is wrapped
to retain the raw result alongside the normal verbose text. These are not
`quick-bench` or development-suite settings.

The seven operations produce 19 measured combinations. Each gets a fresh JVM
with Clojure 1.12.5 and a fixed 512 MiB heap. Processes run serially, and model
order alternates between operations. Allow approximately half an hour, with
variation from calibration, GC and machine load. This is one fork per combination,
not a replicated statistical claim across JVMs.

After timing, the runner also measures thread-allocated bytes for three batches
of one million invocations. Raw EDN contains time samples, confidence intervals,
allocation samples, source/class hashes, JVM settings and completion timestamps.
Each worker has a verbose `.log`. `summary.md` updates after every completed row.

Existing results are never overwritten. Resume an interrupted run with the same
source, compiled classes, JVM and selection:

```sh
JAVA_HOME=/path/to/jdk-25 clojure -M:bench-representation \
  '{:output "target/bench/representation/my-run" :resume? true}'
```

Select a smaller set of full-duration comparisons with `:selected-cases`:

```sh
JAVA_HOME=/path/to/jdk-25 clojure -M:bench-representation \
  '{:selected-cases [:extend/empty :extend/small]
    :output "target/bench/representation/small-updates"}'
```

The rebuild follow-up is opt-in. `extend/limit` adds two bindings to a six-binding
parent, and `overwrite/small` replaces the first and last values in a six-binding
parent without changing its size. This selection runs eight standard benchmarks,
comparing ordinary persistent associations with bulk array-map construction:

```sh
JAVA_HOME=/path/to/jdk-25 clojure -M:bench-representation \
  '{:selected-cases [:extend/empty :extend/small :extend/limit :overwrite/small]
    :selected-models ["clojure-persistent" "clojure-rebuild"]
    :output "target/bench/representation/rebuild"}'
```

All four model names can be selected for update cases. Lookup comparisons use
`clojure-persistent` and `var-scope`; the other update strategies return the same
Clojure map representation, so they do not add distinct lookup workloads.

## Correctness

Before launching workers, deterministic property checks compare all models with
ordinary Clojure associations. They cover every size through 64 bindings and
2,000 randomized branches per model, including duplicate keys, skipped updates,
nil/false, empty batches and mutation of the caller's input array after extension.
All retained parent and child snapshots are rechecked for immutability. The
128 frames of every kernel/model combination are checked separately.

Workers validate only their selected model/kernel so verification does not mix
implementation type profiles inside a measured JVM.

```sh
JAVA_HOME=/path/to/jdk-25 clojure -M:bench-representation '{:verify-only? true}'
```
