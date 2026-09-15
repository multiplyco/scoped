# JVM experiment candidates

These are unmeasured proposals for the current Java runtime. Prioritize JDK 25+
with ScopedValue; retain JDK 17 correctness and treat ThreadLocal performance as
secondary. Use the [development suite](DEV.md) for initial comparisons and
focused isolated runs for confirmation.

Measured follow-ups: [direct Clojure lookup and scope-entry allocation profiles](experiments/lookup-allocation/README.md).

Standalone comparison: [Var-keyed representation versus Clojure maps](experiments/representation/README.md),
using standard `criterium/bench` defaults outside the library runtime.

Batch-size follow-up: [persistent versus transient crossover](experiments/representation/BATCH.md),
with short isolated scans and focused standard Criterium confirmations.

Task-bookkeeping follow-up: [a separate task ScopedValue versus one map update](experiments/task-slot/README.md),
covering nested calls and capture/restore sequences with stable application context.

Scope-extension follow-up: [argument arrays versus unrolled Java helpers and inline transients](experiments/extend-scope/README.md),
covering explicit batches of 11 and 20 bindings on JDK 25.

Storage follow-up: [Bifurcan versus Clojure maps](experiments/bifurcan/README.md),
focused on single-binding updates, lookups, retained child scopes and small batches.

## Distinguish an unbound carrier with `NO_SCOPE`

Status: deferred idea, 2026-09-14. No runtime change or integrated measurement.

Use a private, non-null `NO_SCOPE` sentinel as the unbound carrier fallback.
On JDK 25, `ScopedValue.orElse(NO_SCOPE)` would distinguish an absent scope from
an explicitly bound empty map. Keep this distinction inside Java and present
the same interface to Clojure on both carrier implementations.

Hypothesis: scope entry with no parent can construct a map directly from the
incoming bindings, and lookup with no scope can proceed directly to `Var.deref()`.
The standalone representation experiment found direct construction from empty
promising, but did not measure the sentinel check or complete scope entry.

Public `current-scope` must still return an empty map when unbound. Preserve
ordinary Var thread bindings and roots, nil/false values, ordered duplicate
updates, `SKIP`, captured empty scopes, and restoration after exceptions.
Any directly constructed array map must own its backing array. Keep the choice
of an assoc/transient cutoff independent of this experiment.

## Compose fixed arities from small transient helpers

Status: proposed, 2026-09-11. No candidate implementation or A/B measurement yet.

Add `assocTransientTwo` and `assocTransientThree` alongside `assocTransient`,
then express `assocTwo` through `assocTen` as compositions of those helpers.
Keep exactly one `asTransient`/`persistent` pair per operation. Pass each
helper's returned transient to the next helper so a change in map representation
is preserved. Keep binding order, duplicate-key behavior and `SKIP` semantics.

Hypothesis: smaller outer methods may pass HotSpot's per-method inlining limit
and allow useful partial inlining. Fully expanding the helpers still produces
roughly the same work and adds call depth; total compilation budgets can remain
the limiting factor. Calls that remain uninlined may add overhead.

At proposal time, compiled bytecode sizes were 20 bytes for `assocTransient`,
41 for `assocTwo`, 52 for `assocThree` and 129 for `assocTen`. The local JDK
25.0.2 reported `MaxInlineSize=35`, `FreqInlineSize=325` and `MaxInlineLevel=15`.
These are heuristic limits, not guarantees. The smaller arities crossing the
ordinary limit are a particular reason to try this; the larger methods already
fit below the hot-call size limit. See [HotSpot's inlining logic](https://raw.githubusercontent.com/openjdk/jdk25u/master/src/hotspot/share/opto/bytecodeInfo.cpp).

Compare unchanged and composed versions in fresh JVMs with the same workloads
and settings. Include small arities, nine/ten bindings, skipped and duplicate
bindings, and parent maps on both sides of the array-map/hash-map transition.
Add any missing kernels before recording either baseline, keeping suite hashes
identical for the comparison. Record time and allocations; check warm-up
behavior separately if it appears to be the main difference.

Capture `-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining` in separate diagnostic
runs. Distinguish method-size rejection from inlining-depth or total-size limits;
use the diagnostics to explain results rather than assuming that shorter source
methods improve performance.
