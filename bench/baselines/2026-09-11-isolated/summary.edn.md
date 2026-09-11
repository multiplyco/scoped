# Isolated JVM runtime measurements

Status: in progress. Completed 47 / 310 forks.

Quality profile: `full`. Warm-up: 20.0 seconds; 60 samples targeting 1.0 seconds each.

Every measured fork runs one case. Values below summarize fork means; ranges are not confidence intervals. Flags identify drift, warm-up shifts or compilation/GC during sampling; they are diagnostic signals, not automatic exclusions. All forks remain included. The EDN manifest links raw results and logs.

| Backend / case | Forks | Median ns/call | Min–max ns/call | Median B/call | Flagged forks |
| --- | ---: | ---: | ---: | ---: | ---: |
| scoped-value / `ask/false` | 1 | 5.92 | 5.92–5.92 | 0.00 | 1 |
| scoped-value / `ask/hit` | 1 | 6.14 | 6.14–6.14 | 0.00 | 1 |
| scoped-value / `ask/hit-default` | 1 | 5.91 | 5.91–5.91 | 0.00 | 1 |
| scoped-value / `ask/root-default` | 1 | 13.21 | 13.21–13.21 | 0.00 | 1 |
| scoped-value / `ask/unbound-default` | 1 | 5.90 | 5.90–5.90 | 0.00 | 1 |
| scoped-value / `build/nine` | 1 | 626.60 | 626.60–626.60 | 936.00 | 1 |
| scoped-value / `build/one` | 1 | 8.15 | 8.15–8.15 | 56.00 | 1 |
| scoped-value / `build/ten` | 1 | 781.70 | 781.70–781.70 | 1144.00 | 1 |
| scoped-value / `build/two` | 1 | 86.50 | 86.50–86.50 | 168.00 | 1 |
| scoped-value / `build/zero` | 1 | 1.70 | 1.70–1.70 | 0.00 | 1 |
| scoped-value / `capture/restore` | 1 | 51.42 | 51.42–51.42 | 112.00 | 1 |
| scoped-value / `control/return` | 1 | 2.14 | 2.14–2.14 | 0.00 | 1 |
| scoped-value / `current/empty` | 1 | 2.80 | 2.80–2.80 | 0.00 | 1 |
| scoped-value / `entry/scoping-captured` | 1 | 57.29 | 57.29–57.29 | 112.00 | 1 |
| scoped-value / `entry/scoping-one` | 1 | 38.52 | 38.52–38.52 | 112.00 | 1 |
| scoped-value / `entry/scoping-ten` | 1 | 776.08 | 776.08–776.08 | 1200.00 | 1 |
| scoped-value / `entry/scoping-two` | 1 | 57.79 | 57.79–57.79 | 200.00 | 1 |
| scoped-value / `entry/scoping-zero` | 1 | 34.70 | 34.70–34.70 | 88.00 | 1 |
| scoped-value / `entry/skip-inherited` | 1 | 34.79 | 34.79–34.79 | 56.00 | 1 |
| scoped-value / `entry/with-scope` | 1 | 8.36 | 8.36–8.36 | 56.00 | 1 |
| scoped-value / `entry/with-scope-captured` | 1 | 8.87 | 8.87–8.87 | 56.00 | 1 |
| scoped-value / `nested/override` | 1 | 114.85 | 114.85–114.85 | 224.00 | 1 |
| scoped-value / `workload/changing-scopes` | 1 | 125.91 | 125.91–125.91 | 534.31 | 1 |
| thread-local / `ask/false` | 1 | 5.88 | 5.88–5.88 | 0.00 | 1 |
| thread-local / `ask/hit` | 1 | 6.34 | 6.34–6.34 | 0.00 | 1 |
| thread-local / `ask/hit-default` | 1 | 5.62 | 5.62–5.62 | 0.00 | 1 |
| thread-local / `ask/root-default` | 1 | 13.54 | 13.54–13.54 | 0.00 | 1 |
| thread-local / `ask/unbound-default` | 1 | 13.76 | 13.76–13.76 | 0.00 | 0 |
| thread-local / `build/nine` | 1 | 385.36 | 385.36–385.36 | 688.00 | 1 |
| thread-local / `build/one` | 1 | 8.17 | 8.17–8.17 | 56.00 | 1 |
| thread-local / `build/ten` | 1 | 451.93 | 451.93–451.93 | 824.00 | 1 |
| thread-local / `build/two` | 1 | 86.53 | 86.53–86.53 | 168.00 | 1 |
| thread-local / `build/zero` | 1 | 1.62 | 1.62–1.62 | 0.00 | 1 |
| thread-local / `capture/restore` | 1 | 16.27 | 16.27–16.27 | 0.00 | 0 |
| thread-local / `control/return` | 1 | 1.83 | 1.83–1.83 | 0.00 | 1 |
| thread-local / `current/empty` | 1 | 3.12 | 3.12–3.12 | 0.00 | 0 |
| thread-local / `entry/scoping-captured` | 1 | 25.06 | 25.06–25.06 | 56.00 | 1 |
| thread-local / `entry/scoping-one` | 1 | 24.38 | 24.38–24.38 | 56.00 | 1 |
| thread-local / `entry/scoping-ten` | 1 | 542.18 | 542.18–542.18 | 824.00 | 1 |
| thread-local / `entry/scoping-two` | 1 | 61.29 | 61.29–61.29 | 144.00 | 1 |
| thread-local / `entry/scoping-zero` | 1 | 12.17 | 12.17–12.17 | 0.00 | 1 |
| thread-local / `entry/skip-inherited` | 1 | 13.69 | 13.69–13.69 | 0.00 | 1 |
| thread-local / `entry/with-scope` | 1 | 6.15 | 6.15–6.15 | 0.00 | 1 |
| thread-local / `entry/with-scope-captured` | 1 | 6.46 | 6.46–6.46 | 0.00 | 0 |
| thread-local / `nested/override` | 1 | 60.21 | 60.21–60.21 | 112.00 | 1 |
| thread-local / `workload/changing-scopes` | 1 | 75.06 | 75.06–75.06 | 299.31 | 1 |
| thread-local / `workload/read-100` | 1 | 647.41 | 647.41–647.41 | 56.00 | 1 |
