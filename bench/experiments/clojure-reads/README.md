# Clojure reads with Java scope management

This experiment switches both public `ask` arities to Clojure. The defaulted arity expands map
lookup, absence detection and root fallback into its caller. The no-default
arity calls `impl/get-scoped-var`, whose body uses the same macro, matching the
original Clojure function shape. Java still supplies the carrier, scope
entry/exit and map construction. `ScopedRuntime.lookup` and `get` remain intact
but are no longer called by the public read paths.

The runtime experiments have since been stashed. Active production sources now
match the original Clojure version; see the [current protocol](../../RUNTIME.md)
for the clean baseline and stash identifier. Results below use the older exploratory protocol.

An opaque Clojure `defonce` holds the missing-value marker, loaded once per
read. This retains support for arbitrary keyword values. The comparison
includes that marker access and Clojure function dispatch; it does not isolate
macro expansion alone. Both implementations continue to use `Map.getOrDefault`.

The [Java-read patch](java-reads.patch), applied to this version, restores the
exact two Clojure files used by the Java control. Java sources are identical
between variants. The public benchmark suite is unchanged, with SHA-256
`41001f26b58e773f786546e4da05a3b6dab3aeecb12a06a1d2c708ac35f2651c`.

## Validation

- 22 tests / 269 assertions pass on each JVM backend.
- All 17 shared CLJS tests pass in headless Chrome.
- Every benchmark run verifies all 30 kernels, lazy defaults and exceptional
  restoration before measuring the selected cases.
- The Java-read patch passes `git apply --check`; formatting checks pass.

## Measurement

The comparison remeasures the Java control rather than relying only on the
[earlier full-suite results](../java/README.md). Thirteen cases cover all eight
isolated reads, the return/current-scope controls, prebuilt scope entry with a
read, scope construction with a capturing body, and the 100-read workload.
Selection and case order are identical between variants for each matched pair.

All eight runs use fresh JVMs and run serially, in this order:

1. Java reads, ScopedValue, forward.
2. Clojure reads, ScopedValue, forward.
3. Clojure reads, ThreadLocal, forward.
4. Java reads, ThreadLocal, forward.
5. Clojure reads, ScopedValue, reverse.
6. Java reads, ScopedValue, reverse.
7. Java reads, ThreadLocal, reverse.
8. Clojure reads, ThreadLocal, reverse.

The Java control runs from an isolated source snapshot; the Clojure variant
runs from the working tree. Source, dependency and suite hashes are recorded in
each result. Both use Clojure 1.12.5, OpenJDK 26.0.2+10-FR, macOS/aarch64 and a
512 MiB heap, without direct linking or JIT diagnostic flags. The only JVM
argument difference within a backend is the CLI's checkout-specific basis path.

Each case uses three seconds of warm-up and 20 samples targeting 50 ms, with
1,000 bootstrap resamples and no invocation-overhead subtraction. Allocations
use three batches of 100,000 calls. Ranges below span the forward/reverse means,
not confidence intervals. Ratios compare matching case orders; less than one
means faster with Clojure reads. Two forks expose some variability but do not
establish application-wide performance.

## Findings

Switching the reads back to Clojure does not recover the read performance of
the earlier all-Clojure baseline. The Clojure implementation remains active
for further experiments; the Java read implementation is retained unchanged.

- ScopedValue defaulted hits are mixed (0.93–1.04 times the Java time).
  Defaulted root reads improve by only 0–2%, and unbound defaults are essentially
  unchanged. Explicit nil is about 5% slower in both matched runs.
- ThreadLocal defaulted hits improve strongly only in the reverse run. Root
  defaults are 6–40% slower, and explicit nil is 13–37% slower. Explicit false
  is 5–19% faster in both orders.
- No-default root fallback is slower on both backends: 4–92% on ScopedValue
  and 37–118% on ThreadLocal. The 32-binding hit is also slower in both orders,
  by 10–13% on ScopedValue and 2–3% on ThreadLocal.
- The 100-read workload is 4–12% slower on ScopedValue. On ThreadLocal it is
  2.1–2.7 times the matched Java timing. Absolute ThreadLocal timings vary
  greatly with order: Java measures 0.55–1.17 microseconds and Clojure measures
  1.16–3.12 microseconds. This warrants caution about extrapolating the size
  of that regression to an application.
- The ThreadLocal capturing-body workload improves by 3–20%; its measured
  allocation falls from 80 bytes to 56–80 bytes. All isolated reads still
  allocate zero bytes on both implementations.

The smaller selection also makes the unchanged Java implementation faster in
some cases than in the earlier full-suite run. Comparing these new Clojure
numbers directly to the older Java table would conflate implementation and
benchmark-context effects. The fresh controls above are the comparison used
for every ratio here.

This weakens the earlier hypothesis that returning the reads to Clojure would
restore the lost performance. The observed JIT differences did not by
themselves identify a remedy. The result measures this complete Clojure read
implementation with the Java carrier; it does not isolate the effects of
inlining, sentinel access or function dispatch individually.

## Confidence and warm-up review

These results favor Java in several cases, but the benchmark protocol needs
stronger validation before they decide the implementation. The recorded warm-up
periods are only 3.00–3.13 seconds; measurements generally last about one second.
The installed Criterium 0.4.6 defaults are ten seconds of warm-up and 60 samples
targeting one second each. This suite deliberately uses shorter settings for
exploration.

The ThreadLocal 100-read workload illustrates the limitation:

| Variant / order | Warm-up average µs/call | Measurement mean µs/call |
| --- | ---: | ---: |
| Java forward | 1.20 | 1.17 |
| Java reverse | 0.55 | 0.55 |
| Clojure forward | 1.16 | 3.12 |
| Clojure reverse | 1.18 | 1.16 |

Warm-up averages divide the recorded warm-up time by its execution count;
they are not measurements of the end of warm-up. In the Clojure forward run,
the first and last five measured samples average 3.121 and 3.126 µs/call.
Its stable measurement period therefore does not establish agreement with
warm-up or other forks. These records alone cannot identify the cause of the
change. Elsewhere, first/last-five averages differ by around 20% in one scope
entry case.

All selected cases share a JVM, and verification executes every kernel before
timing. Longer warm-up does not by itself remove shared compilation profiles.
The [JMH forking example](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_12_Forking.java)
demonstrates this problem. Also, the 100-read case is a loop workload; repeated
lookups can be optimized together, so its ratio is not an independent read
latency ratio. See the [JMH loop example](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_11_Loops.java).

The next validation should isolate each case in its own JVM, separate full
correctness verification from performance forks, use at least five forks per
variant/case, and compare longer warm-up and measurement windows. Compilation
and deoptimization logs should accompany the suspicious cases. A separate mixed
workload with changing scopes and values is needed to check whether isolated
wins carry over. No runtime or benchmark code changed during this review.

### scoped-value

| Case | Java reads ns/call | Clojure reads ns/call | Clojure/Java | Java B/call | Clojure B/call |
| --- | ---: | ---: | ---: | ---: | ---: |
| `control/return` | 2.2–5.7 | 2.1–5.7 | 0.92–1.01 | 0 | 0 |
| `current/bound` | 3.1–6.2 | 3.6–5.9 | 0.95–1.17 | 0 | 0 |
| `ask/hit` | 13.4–15.3 | 14.0–14.1 | 0.92–1.04 | 0 | 0 |
| `ask/hit-default` | 14.3–14.7 | 13.7–14.8 | 0.93–1.04 | 0 | 0 |
| `ask/hit-large` | 9.9–12.8 | 10.9–14.5 | 1.10–1.13 | 0 | 0 |
| `ask/root` | 10.9–17.0 | 17.6–21.0 | 1.04–1.92 | 0 | 0 |
| `ask/root-default` | 17.5–19.0 | 17.4–18.6 | 0.98–1.00 | 0 | 0 |
| `ask/unbound-default` | 17.6–19.5 | 17.6–19.5 | 1.00 | 0 | 0 |
| `ask/nil` | 8.1–15.4 | 8.5–16.2 | 1.05 | 0 | 0 |
| `ask/false` | 13.7–14.2 | 13.1–14.6 | 0.96–1.03 | 0 | 0 |
| `entry/with-scope-read` | 34.6–38.9 | 34.3–37.9 | 0.98–0.99 | 56–72 | 56–72 |
| `entry/scoping-captured` | 51.7–52.4 | 52.8–57.1 | 1.02–1.09 | 112–136 | 112–152 |
| `workload/read-100` | 1072.4–1151.8 | 1197.7–1200.1 | 1.04–1.12 | 136–152 | 112–136 |

### thread-local

| Case | Java reads ns/call | Clojure reads ns/call | Clojure/Java | Java B/call | Clojure B/call |
| --- | ---: | ---: | ---: | ---: | ---: |
| `control/return` | 1.9–5.6 | 2.1–5.7 | 1.01–1.16 | 0 | 0 |
| `current/bound` | 4.2–6.7 | 3.8–6.8 | 0.90–1.01 | 0 | 0 |
| `ask/hit` | 13.8–14.7 | 9.7–14.4 | 0.66–1.04 | 0 | 0 |
| `ask/hit-default` | 14.7–14.9 | 9.0–14.9 | 0.61–1.02 | 0 | 0 |
| `ask/hit-large` | 13.8–13.9 | 14.1–14.3 | 1.02–1.03 | 0 | 0 |
| `ask/root` | 8.9–13.8 | 18.8–19.3 | 1.37–2.18 | 0 | 0 |
| `ask/root-default` | 13.7–16.6 | 17.6–19.2 | 1.06–1.40 | 0 | 0 |
| `ask/unbound-default` | 14.8–18.2 | 18.1–20.3 | 0.99–1.37 | 0 | 0 |
| `ask/nil` | 9.8–15.9 | 11.0–21.8 | 1.13–1.37 | 0 | 0 |
| `ask/false` | 15.9–18.2 | 14.7–15.2 | 0.81–0.95 | 0 | 0 |
| `entry/with-scope-read` | 18.9–20.3 | 19.6–19.9 | 0.98–1.04 | 0 | 0 |
| `entry/scoping-captured` | 35.1–38.2 | 30.7–33.9 | 0.80–0.97 | 80 | 56–80 |
| `workload/read-100` | 548.0–1174.7 | 1161.9–3124.9 | 2.12–2.66 | 56 | 56 |

## Reproduction

Run the following in this version and in a separate checkout with
`java-reads.patch` applied, changing the output path each time. Repeat with
`:reverse? true` and with `-J-Dco.multiply.scoped.force-fallback=true` for both
variants. Keep benchmark JVMs serial.

```sh
clojure -T:build compile-java
clojure -M:bench-runtime '{:cases [:control/return :current/bound :ask/hit :ask/hit-default :ask/hit-large :ask/root :ask/root-default :ask/unbound-default :ask/nil :ask/false :entry/with-scope-read :entry/scoping-captured :workload/read-100] :output "target/read-comparison.edn"}'
```

## Raw results

- [java, scoped-value, forward](2026-09-11-java-scoped-value-forward.edn)
- [java, scoped-value, reverse](2026-09-11-java-scoped-value-reverse.edn)
- [java, thread-local, forward](2026-09-11-java-thread-local-forward.edn)
- [java, thread-local, reverse](2026-09-11-java-thread-local-reverse.edn)
- [clojure, scoped-value, forward](2026-09-11-clojure-scoped-value-forward.edn)
- [clojure, scoped-value, reverse](2026-09-11-clojure-scoped-value-reverse.edn)
- [clojure, thread-local, forward](2026-09-11-clojure-thread-local-forward.edn)
- [clojure, thread-local, reverse](2026-09-11-clojure-thread-local-reverse.edn)
