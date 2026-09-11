# Conditional binding benchmarks

For public-API read, construction, entry/exit and capture/restore measurements,
see the [JVM runtime benchmark suite](RUNTIME.md).

Recorded measurements and interpretation are in [RESULTS.md](RESULTS.md).

These comparisons retain the Clojure construction algorithms from before the
Java runtime experiment. Their expansion and merge loop are frozen in
`co.multiply.scoped-bench-construction`; use `:bench-runtime` to measure the
current public API. The original Clojure baseline requires no Java compilation.

The `:bench` alias adds [Criterium 0.4.6](https://github.com/hugoduncan/criterium)
and the benchmark source directory. It uses a fixed 512 MiB JVM heap. Run:

```sh
clojure -M:bench
clojure -M:bench '{:reverse? true :output "target/bench/skip-reverse.edn"}'
```

The runner compares three ways to construct a scope:

- `baseline`: the original direct assoc operations, without sentinel support.
- `function`: benchmark-local copies of the previous assoc wrapper functions.
- `macro`: the `h/persistentAssocSkip` and `h/transientAssocSkip` macros.

The small-binding variants reuse the frozen `extend-scope` expansion, replacing
only the association symbols. For ten or more bindings, the two comparison loops
match its `merge-resolved-bindings`, replacing only its association operation. These
baseline and function alternatives are confined to the benchmark namespace.

One, two, and ten bindings exercise persistent assoc, unrolled transient assocs,
and the vector/loop path, respectively. Workloads supply ordinary values
(including `nil` and `false`), alternate skipped and supplied bindings within a
scope, or skip every binding. The baseline is measured only with ordinary values
because it cannot implement skipping. With one binding, `mixed` would be the same
as `skip`, so that duplicate case is omitted.

The default input scope is empty. To measure overriding inherited bindings:

```sh
clojure -M:bench '{:scope :inherited :output "target/bench/skip-inherited.edn"}'
```

Kernels are compiled before timing. Each invocation reads its values from a
runtime object array and returns its resulting map to Criterium's result sink.
The default workloads reuse the same inputs, so skipped positions are
predictable. A separate workload cycles through 256 precomputed frames with
independent 50% skip decisions at the same call site:

```sh
clojure -M:bench '{:workloads [:changing] :output "target/bench/skip-changing.edn"}'
```

No random-number generation is timed. This case includes the cursor increment
and input-frame lookup, equally for both variants. It is not directly comparable
to the fixed-input timings. The seed is fixed so fresh JVMs use identical inputs.
Neither mode measures whole `scoping` bodies or CLJS.

Each comparison group runs serially in round-robin order, with at least five
seconds of JIT warm-up per variant and 30 samples targeting 100 ms each. Reported
times are nanoseconds per complete scope construction, including Criterium's
invocation/result-sink overhead; no overhead estimate is subtracted. Confidence
intervals are Criterium's bootstrap 95% intervals within a single JVM run. Use
fresh JVM runs to check whether differences survive compilation and run order.

Raw samples, estimates, JVM details, and options are written to
`target/bench/skip.edn` by default, after each group. Options are supplied as one
EDN map; nested `:criterium` options override the defaults. For example:

```sh
clojure -M:bench '{:bindings [1 2 10] :workloads [:values] :output "target/bench/values.edn"}'
clojure -M:bench '{:verify-only? true}'
```

The runner checks both skip variants against an independent expected map before
timing, including inherited and absent bindings and the 0/1/2/9/10/11 boundaries.
