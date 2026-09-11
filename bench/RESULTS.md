# Skip wrapper measurements

Measured on 2026-09-11 with Clojure 1.12.5, Corretto OpenJDK 26.0.2 on macOS
ARM64, a fixed 512 MiB heap, and normal Clojure compilation. See
[README.md](README.md) for the harness and its limits.

These measurements were taken before adopting the macro wrappers in production.
The harness now preserves the previous functions locally and measures the
production macros. Fresh runs may differ because the code locations have changed.

## Fixed inputs, empty starting scope

These are ranges of mean times from two fresh JVMs with opposite variant order.
All numbers are **nanoseconds per complete scope construction**, not per binding.
Each variant had at least five seconds of warm-up and 30 interleaved samples
targeting 100 ms. The raw files retain samples and bootstrap confidence intervals.

| Bindings | Workload | Original assoc | Function wrappers | Macro wrappers |
| ---: | --- | ---: | ---: | ---: |
| 1 | Ordinary values | 12.5–15.6 | 12.0–12.1 | 11.1–12.7 |
| 2 | Ordinary values | 82.3–84.0 | 84.0–86.8 | 82.8–84.8 |
| 10 | Ordinary values | 593.7–596.2 | 529.9–539.2 | 585.8–595.8 |
| 1 | All skipped | — | 6.5 | 5.9–6.1 |
| 2 | All skipped | — | 34.8–35.4 | 13.2–13.4 |
| 10 | All skipped | — | 57.1–58.0 | 45.9–46.1 |
| 2 | Half skipped | — | 82.9–83.6 | 80.0–85.0 |
| 10 | Half skipped | — | 173.1–175.6 | 160.9–169.6 |

The original associations cannot implement skipping, so they are excluded from
those workloads. Skipped positions in this table stay fixed across invocations.

The function implementation adds roughly 2–3 ns for two ordinary bindings in
these runs. One and ten bindings do not show an added cost. In particular,
functions outperform the original loop at ten bindings; treat that as a result
specific to this workload and JVM compilation, rather than a fixed saving that
will transfer to other callers.

Macros have their clearest advantage when every supplied binding is skipped:
about 22 ns for two bindings and 11–12 ns for ten. The ordinary-value comparison
does not establish a general performance advantage for macro wrappers.

Reproduce these runs with:

```sh
clojure -M:bench '{:output "target/bench/skip-forward.edn"}'
clojure -M:bench '{:reverse? true :output "target/bench/skip-reverse.edn"}'
```

## Skipping over an inherited scope

A supplementary fresh JVM run started from a scope with twelve existing Var
bindings, all set to `:outer`, and skipped every new binding. These are single-run
means, with the same warm-up and sampling settings as above:

| Bindings | Function wrappers | Macro wrappers |
| ---: | ---: | ---: |
| 1 | 3.0 ns | 2.6 ns |
| 2 | 9.4 ns | 8.7 ns |
| 10 | 37.0 ns | 19.8 ns |

The large two-binding advantage in the empty-scope run does not carry over here.
Starting map representation matters, so the skipped-binding timings should not
be generalized into a fixed saving per binding.

```sh
clojure -M:bench '{:scope :inherited :workloads [:skip] :output "target/bench/skip-inherited.edn"}'
```

## Changing skip decisions at the same call site

Two further fresh JVMs, again with opposite variant order, cycled through the
same 256 precomputed input frames. Each binding has an independent 50% chance of
being skipped in each frame. The starting scope is empty. Times include the
cursor update and frame lookup, so compare variants within this table rather
than comparing directly to the fixed-input table.

| Bindings | Function wrappers | Macro wrappers |
| ---: | ---: | ---: |
| 1 | 15.6–17.1 ns | 7.3–11.8 ns |
| 2 | 58.1–64.4 ns | 49.8–51.5 ns |
| 10 | 214.3–215.3 ns | 202.2–225.3 ns |

Macros won both one-binding comparisons, by 3.7 and 9.8 ns, and both two-binding
comparisons, by 14.5 and 6.7 ns. At ten bindings the winner reversed between JVMs.
The narrow confidence intervals within some runs do not account for this
between-JVM variation.

```sh
clojure -M:bench '{:workloads [:changing] :output "target/bench/skip-changing.edn"}'
clojure -M:bench '{:workloads [:changing] :reverse? true :output "target/bench/skip-changing-reverse.edn"}'
```

## Interpretation

There is no constant additive cost for the helper function invocation. The
ordinary-value paths are close at one and two bindings, and the function version
actually wins at ten in this setup. Both designs keep the sentinel check out of
`ask`; this benchmark measures construction only.

For small scopes that conditionally override values, the macro wrappers are a
reasonable choice: they retain the assoc-helper abstraction and improve the
changing-input cases here. For ten-binding workloads, these measurements do not
establish an overall winner. Full `scoping` bodies, other map sizes, other JVMs,
and CLJS would need their own measurements before making broader claims.
