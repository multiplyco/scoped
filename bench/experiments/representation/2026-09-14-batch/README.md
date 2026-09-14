# Persistent/transient crossover measurements, 2026-09-14

This follow-up keeps Clojure maps and varies the incoming batch size. It estimates
where a transient editing session becomes worthwhile, without adding a key scan
or additions/overrides classifier to the measured operation. See the
[workloads, protocols and commands](../BATCH.md).

Measurements use actual JDK **25.0.2+10-LTS**, Clojure **1.12.5**, Criterium
**0.4.6**, macOS aarch64, and a fixed 512 MiB heap. One serial, fresh JVM runs each
strategy/workload/fork. The [source snapshot](sources.zip) matches the source
fingerprints recorded in the results. The library runtime was not changed.

The estimate is a broad **16–64 update crossover**, depending on the workload,
with **32–64 incoming bindings** the range worth testing for one simple rule
that does not classify keys. Small batches generally favor persistent updates
for elapsed time. This is an empirical range, not a universal cutoff at eight
map entries or a proof that transients always win above a particular arity.

## Exploratory scans

The [main scan](screen/summary.md) contains 80 runs: parents of 0, 4, 8 and 32;
batches of 2, 4, 8 and 16; additions, overrides and mixed batches; plus four
sensitivity cases. The [larger scan](large/summary.md) adds 28 runs, extending
batches to 32 and 64 and parent size to 128. Every raw EDN result, verbose log
and plan is retained beside its summary.

The [combined ratio table](screen-comparison.md) groups the exploratory results
by parent, workload and batch size. Ratios below one favor transients.

These scans use three-second warm-up, 12 samples targeting 50 ms each, and no
overhead subtraction. They are useful for locating a crossover and choosing
confirmations; small differences are not reliable cutoff estimates. Screen and
full results must be compared separately.

For the main scan, persistent associations were faster in most two-, four- and
eight-binding cases. At 16, overrides in a 32-entry map were approximately tied,
while additions and mixed batches still favored persistent associations. The
larger scan found a transient advantage for 32 overrides in a 32-entry map,
but persistent associations were faster for 32 additions to that same-sized
parent. At 64 additions the transient strategy was faster in the sampled
0-, 32- and 128-entry parents, though some timing differences were small.

Allocation often favored transients well before elapsed time did. For example,
the 32-entry parent with 32 additions allocated about 9.2 KB using persistent
associations versus 2.6 KB using transients in the larger scan. That scan still
favored persistent associations for elapsed time. Lower allocation can matter
under a different application's GC load; these runs do not quantify that benefit.

The sensitivity cases also show why raw arity cannot identify effective work.
Eight skipped bindings favored persistent updates (about 6.5 versus 10.5 ns and
0 versus 40 allocated bytes). Repeated updates to only two new keys narrowed the
timing gap substantially and reduced transient allocation, because editable
storage could be reused. No-op value assignments and skipped bindings are
separate cases; they do not necessarily have the same allocation behavior.

## Standard Criterium confirmations

Four selected workloads were repeated with unmodified `criterium/bench`
defaults: ten-second warm-up, 60 samples targeting one second each and normal
overhead subtraction. Each strategy gets one fresh JVM. The [full comparison](full-comparison.md)
and [all eight raw results and logs](full/) are retained separately from the
screen results. Cells below are nanoseconds / allocated bytes per batch.

| Parent | Incoming bindings | Workload | Persistent assoc | Transient batch |
| ---: | ---: | --- | ---: | ---: |
| 8 | 8 | Override | 184.15 / 896.00 | 198.96 / 216.00 |
| 32 | 16 | Override | 618.55 / 4535.25 | 544.03 / 1091.88 |
| 32 | 32 | Add | 1287.71 / 9233.87 | 1428.20 / 2633.32 |
| 32 | 32 | Override | 1081.86 / 9073.69 | 908.88 / 1590.32 |

The sixteen-override case moved from nearly tied in the screen to a transient
win in the full comparison. This is why the short-run numbers should not be used
to choose a precise cutoff. The eight-override array-map case still favored
persistent associations after the longer run, while allocating much more.

The two 32-binding batches demonstrate the unresolved workload choice directly:
transients were about 16% faster for overrides but about 11% slower for additions
to a same-sized parent. Both cases allocated substantially less with transients.
A rule using only parent size and incoming arity must make the same choice for
those cases; it cannot pick both winners without examining the bindings.

## Candidate policy

Keep persistent associations as the default for small batches. For a simple
arity-only policy, **32** is an allocation-conscious candidate: it captures the
confirmed override win and substantial allocation savings, while accepting the
measured addition penalty at that arity. **64** is the more conservative timing
candidate suggested by the screen: it avoids that addition penalty, but misses
transient wins on smaller override-heavy batches. The 64-binding results were
screened only, not confirmed with standard Criterium, and neither threshold is
established as a universal optimum.

There is no evidence here for introducing a branch precisely when the parent or
result exceeds eight entries. Nor do these results support using transients for
every batch of two or more bindings. The fixed-arity Java helpers and an
arity-only 32/64 cutoff are the appropriate next integrated comparison. Preserve
binding order and skip behavior, and include the small regression cases when
checking a candidate. Do not add a preliminary key scan based on this study.

## Interpretation limits

Additions and overrides are observable by inspecting the map's keys, but choosing
the strategy based on them would require extra work before applying the batch.
These measurements keep that classification outside the operation. They can
support a policy based on already-known arity and cheap parent properties; they
cannot establish the best policy for an unknown production workload mixture.

The kernels receive prebuilt argument arrays and execute a Java loop through the
same persistent/transient adapters used by the original representation study.
They exclude carrier binding, argument construction and the production helpers'
unrolled fixed arities. A proposed policy should therefore be checked in the
integrated development suite before changing the runtime.

The default 40 workloads passed semantic checks on actual JDK 17 and JDK 25;
the larger fixtures also passed on JDK 25 before timing. Checks cover expected
map count and every Var lookup, retained-parent immutability and ownership of
borrowed argument arrays. Java compilation used `--release 17`.
