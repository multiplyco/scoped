# Clojure runtime baseline — 2026-09-11

The strengthened isolated-JVM baseline is in [2026-09-11-isolated](2026-09-11-isolated/README.md).
The results below retain the earlier exploratory protocol.

These runs establish a reference before moving JVM runtime machinery into Java.
They use the [public-API suite](../RUNTIME.md), with production source at commit
`0b3dd159b88cf3072b59f19285dc2f0adb5aaf72` and the new benchmark files uncommitted.
Each artifact records source and suite hashes to identify the measured code.

Environment: Clojure 1.12.5, OpenJDK 26.0.2+10-FR, macOS 26.3.1, aarch64,
10 JVM-visible processors, fixed 512 MiB heap, ordinary compilation (no direct
linking). Defaults: three-second warm-up, 20 samples targeting 50 ms each,
1,000 bootstrap resamples. Allocation samples use three batches of 100,000 calls.

The four fresh JVMs run serially, in this order:

```sh
clojure -M:bench-runtime '{:label "clojure-baseline-scoped-value-forward" :output "bench/baselines/2026-09-11-scoped-value-forward.edn"}'
clojure -J-Dco.multiply.scoped.force-fallback=true -M:bench-runtime '{:label "clojure-baseline-thread-local-forward" :output "bench/baselines/2026-09-11-thread-local-forward.edn"}'
clojure -J-Dco.multiply.scoped.force-fallback=true -M:bench-runtime '{:label "clojure-baseline-thread-local-reverse" :reverse? true :output "bench/baselines/2026-09-11-thread-local-reverse.edn"}'
clojure -M:bench-runtime '{:label "clojure-baseline-scoped-value-reverse" :reverse? true :output "bench/baselines/2026-09-11-scoped-value-reverse.edn"}'
```

The Clojure version currently comes from the local CLI installation. To pin it
when reproducing these commands, add
`-Sdeps '{:deps {org.clojure/clojure {:mvn/version "1.12.5"}}}'` before `-M`.
Use the same JDK as well; the forced fallback permits both backends to be
compared without changing JVM versions.

Raw samples and per-run confidence intervals:

- [ScopedValue, forward](2026-09-11-scoped-value-forward.edn)
- [ScopedValue, reverse](2026-09-11-scoped-value-reverse.edn)
- [ThreadLocal, forward](2026-09-11-thread-local-forward.edn)
- [ThreadLocal, reverse](2026-09-11-thread-local-reverse.edn)

| Case | ScopedValue ns/call | ThreadLocal ns/call | ScopedValue B/call | ThreadLocal B/call |
| --- | ---: | ---: | ---: | ---: |
| `control/return` | 2.2–5.6 | 2.1–5.6 | 0 | 0 |
| `current/unbound` | 4.7–10.0 | 3.6–6.6 | 0 | 0 |
| `current/empty` | 5.7–7.0 | 5.5–6.9 | 0 | 0 |
| `current/bound` | 6.5–6.7 | 6.5–6.6 | 0 | 0 |
| `ask/hit` | 9.4–14.7 | 9.3–15.8 | 0 | 0 |
| `ask/hit-default` | 8.9–15.4 | 8.7–15.4 | 0 | 0 |
| `ask/hit-large` | 13.1–13.9 | 13.9–14.4 | 0 | 0 |
| `ask/root` | 17.8–19.6 | 13.8–14.6 | 0 | 0 |
| `ask/root-default` | 13.9–17.0 | 13.5–13.8 | 0 | 0 |
| `ask/unbound-default` | 14.3–17.6 | 13.8–14.4 | 0 | 0 |
| `ask/nil` | 14.8–15.4 | 15.3–17.8 | 0 | 0 |
| `ask/false` | 8.9 | 8.8–10.8 | 0 | 0 |
| `build/zero` | 5.3 | 5.2–5.3 | 0 | 0 |
| `build/one` | 12.3–12.9 | 12.5–20.2 | 56 | 56 |
| `build/two` | 82.9–83.3 | 82.6–82.7 | 168 | 168 |
| `build/nine` | 410.4–455.6 | 412.9–423.3 | 728 | 728 |
| `build/ten` | 473.4–488.1 | 523.4–616.3 | 824 | 824–936 |
| `entry/with-scope` | 15.4–17.2 | 9.8–9.9 | 56–72 | 0 |
| `entry/with-scope-captured` | 16.0–17.9 | 10.1 | 56–80 | 0 |
| `entry/with-scope-read` | 31.4–35.5 | 13.7–16.1 | 72 | 0 |
| `entry/scoping-zero` | 54.5–59.6 | 14.5–17.2 | 72 | 0 |
| `entry/scoping-one` | 46.6–54.4 | 28.7–31.2 | 128 | 56 |
| `entry/scoping-captured` | 45.7–59.0 | 28.6–32.5 | 136 | 56 |
| `entry/scoping-two` | 81.6–86.4 | 66.4–70.4 | 240 | 168 |
| `entry/scoping-ten` | 558.1–579.4 | 571.0–608.3 | 896 | 824–936 |
| `entry/skip-inherited` | 46.8–49.0 | 17.4–20.8 | 72 | 0 |
| `entry/skip-absent` | 38.3–48.5 | 20.4–26.3 | 80 | 0 |
| `nested/override` | 171.3–171.6 | 66.2–74.8 | 264 | 112 |
| `capture/restore` | 58.9–59.0 | 22.3–22.5 | 152 | 0 |
| `workload/read-100` | 1113.3–1153.7 | 915.9–1123.2 | 112–136 | 56 |

Ranges in the table span the two per-JVM means; they are **not confidence
intervals**. Allocations likewise span the two runs and are rounded to bytes per
call. Read-only allocation counts round to zero; the allocation harness has a
small fixed per-batch cost. All times measure complete calls, including the
100-read workload.

Use these measurements to identify which operations a change improves. Re-run
the original implementation and the candidate on the same machine and compare
matching case orders. Treat results near the control floor cautiously and
check changes across fresh JVMs before claiming a speedup. Two forks are an
initial reference, not a comprehensive characterization of JIT variability.
