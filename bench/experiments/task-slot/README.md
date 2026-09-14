# Separate task bookkeeping from application context

This isolated Java 25 experiment compares one map containing application values
and a task Var with a separate ScopedValue for the task. It changes neither
Scoped's runtime/API nor Quiescent. The question is whether removing the repeated
single `assoc` is promising enough to justify an integration experiment.

Results: [JDK 25 measurements, 2026-09-14](2026-09-14/README.md).

## Compared implementations

- **`map`**: a captured persistent map contains application bindings and the task.
  Each child replaces the task Var using one persistent `assoc`, then binds the
  resulting map to one ScopedValue.
- **`split`**: application bindings stay in the same persistent map. Each child
  creates a fresh `where(context-key, map).where(task-key, task)` carrier and calls
  one body with both bindings established.
- **`split-cached`**: a task-free `where(context-key, map)` carrier is prepared with
  the root fixture. Every child extends that same carrier with its own task
  binding. It never extends a previous child's carrier, avoiding retained task
  history. Preparing the base carrier is outside timing; this variant models
  amortizing its cost over many children with unchanged application context.

The default application-map sizes are 0, 7, 8 and 32. The combined map has one
additional task entry, so seven and eight application bindings cover the
eight/nine-entry array-map boundary. Root maps use ordinary Clojure `assoc`
construction. Each measured sequence creates ten child bindings and actually
changes the task at every step. Both strategies begin with prebuilt root
snapshots; there is no additional timed root binding.

## Execution shapes

- **`nested`**: ten recursive calls, each updating/binding its task and capturing
  the resulting scope. Ancestor bindings remain active until recursion unwinds.
- **`handoff`**: ten successive child entries. Each captures its context, exits
  its binding, then uses the captured context for the next child. This models
  the capture/restore operations across an async boundary, without a queue,
  thread launch, scheduling, or cross-thread execution.

Each body reads its task and one rotating application key. Identity checks make
both reads observable and fail on incorrect propagation. The application values
include nil and false. With zero application entries the context read is a miss.
There are 128 rotating root fixtures, varying the task's insertion position in
array maps and the application key read. Task objects are prebuilt: allocation
of Quiescent's task itself is excluded equally from all variants.

Captured maps are written to bounded, preallocated fixture arrays so every
level's snapshot escapes and remains observable after the sequence. Those arrays
are overwritten on fixture reuse. For split models all entries share the
original context-map reference. No carrier containing a task is retained by
the fixture after execution.

Verification checks every application key, retained task values, parent
immutability, shared context identity, nil/false task values, restoration of
outer bindings, and exception propagation/restoration. It runs before timing.

## Measurement

```sh
JAVA_HOME=/path/to/jdk-25 clojure -T:build compile-task-slot
JAVA_HOME=/path/to/jdk-25 clojure -M:bench-task-slot '{:verify-only? true}'
JAVA_HOME=/path/to/jdk-25 clojure -M:bench-task-slot \
  '{:output "target/bench/task-slot/screen"}'
```

The default **screen** uses Criterium 0.4.6 with three seconds of warm-up, 16
samples targeting 50 ms, 300 bootstrap samples, and no overhead subtraction.
Each model/case gets two fresh JVMs, run serially with reversed model order in
the second fork. This is an exploratory comparison, not standard `bench`.

Use `:protocol :full` for standard `criterium/bench` defaults, including normal
overhead estimation/subtraction. Select cases and models explicitly if needed:

```clojure
{:protocol :full :forks 1
 :specs [{:context-size 8 :depth 10 :mode :handoff}]
 :selected-models ["map" "split" "split-cached"]
 :output "target/bench/task-slot/confirmation"}
```

The runner records raw timing samples, allocations from three batches of 30,000
sequences using ThreadMXBean, JVM flags, runtime versions, and source/class
hashes. Each process uses a 512 MiB heap. Results report **time and bytes per
complete sequence**, not per child. Resume requires `:resume? true` and exactly
matching sources, classes, settings, cases and JVM. Existing records are never
overwritten. Keep screen and full measurements separate.

These kernels compare Java operations directly, with one capturing Java lambda
per child. They omit Clojure macro expansion, IFn adapters, Var root fallback,
application work, task allocation and the executor. A callback fetches the map
once and performs both map lookups on it; it does not reproduce every lookup
through Scoped's public API. This experiment cannot establish application
throughput, GC effects under concurrency, or production API compatibility.

Sources: [Java kernels](java/co/multiply/scoped/experiment/TaskSlotBench.java)
and [Criterium runner](clj/co/multiply/scoped_task_slot_bench.clj).
