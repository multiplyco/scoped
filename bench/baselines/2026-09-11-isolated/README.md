# Original Clojure baseline, isolated JVM protocol

The active production sources match commit `0b3dd159b88cf3072b59f19285dc2f0adb5aaf72`
byte for byte. The runtime/build/test experiments are preserved in stash
`6fbf3f8a8deee6fbf2d464cb4c0de0e3ae975ad5`; benchmark helpers remain active.

This baseline uses 31 cases, both ScopedValue and ThreadLocal, and five fresh
JVMs per case/backend: 310 serial forks. Every fork warms for 20 seconds and
collects 60 samples targeting one second each. Full correctness verification
runs separately. The JVM is OpenJDK 26.0.2+10-FR on macOS/aarch64, with Clojure
1.12.5, Criterium 0.4.6 and a fixed 512 MiB heap. The machine was on AC power at
launch; idle system sleep is inhibited for the run.

During the run, the user confirmed that this computer will also be used for
other work, including launching other JVMs. These measurements therefore share
the machine with uncontrolled workloads. The full timing protocol does not
establish an uncontended environment, and stable samples do not rule out
contention. Retain all results, but confirm small implementation differences
with matched baseline/candidate runs under quieter, comparable conditions.

See the [live report](summary.edn.md) for completion status, fork medians/ranges,
allocations and diagnostic flags. The [manifest](summary.edn) records all source
hashes, settings, the deterministic schedule and completed raw results. Each
fork also has its own EDN and log under this directory. Do not interpret an
incomplete report as a finished baseline.

Validation before launch: 17 original JVM tests / 230 assertions pass on each
backend; 14 original CLJS tests pass in headless Chrome; five runner tests /
25 assertions pass. Smoke runs exercised both backends, changing inputs,
checkpointing, resumption and comparison. The benchmark classpath excludes
`target/classes`, so compiled artifacts from the stashed experiment cannot
shadow the original Clojure namespace.

Run or resume using the commands in the [runtime guide](../../RUNTIME.md).
These results use protocol version 2 and must not be compared directly with the
earlier short, shared-JVM tables.
