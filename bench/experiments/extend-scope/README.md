# Scope-extension argument transport

This isolated JDK 25 experiment compares three expansions of the same explicit
bindings. It leaves the library's macros and both Java runtimes unchanged.

Results: [JDK 25 measurements, 2026-09-14](2026-09-14/README.md).

- **array**: a frozen copy of the pre-unrolling macro fills an Object array with
  alternating Vars and values, then calls Java `MapUpdates.extendScope`.
- **helpers**: evaluate the parent and all Var/value inputs into locals, obtain
  one transient, emit one Java `MapUpdates.assocTransient` call per binding,
  then persist once. Each call consumes the previous call's returned transient.
- **inline**: the same input locals and transient lifecycle, with each SKIP
  identity check and `ITransientAssociative.assoc` call emitted into the caller.
  This uses direct interface calls, without reflective dispatch or Clojure Var
  invocation for the transient operations.

All three use transients regardless of batch size. This measures argument
transport and generated call shape, not the persistent/transient crossover.

Following the recorded comparison, the production 11+ binding expansion adopted
the helper approach. Production subsequently dropped the input locals and now
threads the transient directly through the helper calls; values still evaluate
once, left to right, interleaved with the updates. The benchmark retains the
measured input locals and the old array expansion. The archived source snapshots
contain the exact earlier kernels used for the published measurements.
The current runner follows the extraction of map helpers into `MapUpdates`;
the archived versions call the same methods on `ScopedRuntime`.

## Workloads

There are four workloads: 11 or 20 bindings, either adding distinct Vars to an
eight-entry array map or overriding distinct Vars in a 32-entry hash map. Every
timed update changes every specified binding. Incoming values are prebuilt
objects, loaded individually from a fixture array in all three expansions. That
input array is not the additional transport array allocated by the baseline.

A common Java harness rotates through 128 prebuilt parent/value fixtures and
stores each result in a bounded result array, making the resulting map escape.
Map creation, fixture rotation, the common IFn invocation and result storage are
timed. Fixture construction, value-object allocation, ScopedValue binding,
callbacks and scheduling are excluded. The dominant single-binding operation
is outside this experiment.

Verification checks expected maps against persistent `assoc`, parent and
retained-snapshot immutability, representation promotion, nil/false, partial and
complete SKIP, ordered duplicate updates, once-only left-to-right input
evaluation, invalid-parent error ordering and exceptions in binding values.

## Running

From the repository root:

```sh
JAVA_HOME=/path/to/jdk-25 bash bench/experiments/extend-scope/run.sh \
  '{:verify-only? true}'
JAVA_HOME=/path/to/jdk-25 bash bench/experiments/extend-scope/run.sh \
  '{:output "../../../target/bench/extend-scope/screen"}'
JAVA_HOME=/path/to/jdk-25 bash bench/experiments/extend-scope/run.sh \
  '{:protocol :full :forks 1 :output "../../../target/bench/extend-scope/full"}'
```

Output paths are relative to this experiment directory. Its standalone deps
file avoids changing the main project's development setup. The script compiles
the current primary Java runtime and shared map helper into isolated experiment
classes, then AOT compiles the Clojure kernels and runner. It does not rebuild
the library JAR.

The short protocol uses Criterium 0.4.6 with three seconds of warm-up, 16 samples
targeting 50 ms, 300 bootstrap samples and no overhead subtraction. The default
two forks reverse model order in the second pass. Every model/workload/fork runs
serially in its own fresh JVM with a 512 MiB heap.

The full protocol uses standard `criterium/bench` defaults, including normal
overhead estimation/subtraction. Keep its results separate from short runs.
Allocation is measured after timing with ThreadMXBean in three batches of
30,000 updates. Recorded sources/classes and JVM options must match for resume;
use `:resume? true` with the same plan. Raw records are not overwritten.

The runner records Var key hashes as an additional check on fixture equivalence
between JVMs. Bytecode inspection should confirm that the helper and inline
versions allocate no argument array and contain no reflection. Smaller bytecode
and fewer allocations do not establish lower execution time by themselves.
