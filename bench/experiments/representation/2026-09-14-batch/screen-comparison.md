# Exploratory crossover ratios

Transient time / persistent time. Below 1 favors transients; above 1 favors persistent associations. Each cell compares one fresh JVM per strategy using the screen protocol. Small differences are uncertain.

| Parent entries | Workload | Batch 2 | 4 | 8 | 16 | 32 | 64 |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 0 | add | 2.41 | 1.02 | 0.94 | 0.95 | 1.03 | 0.89 |
| 4 | add | 1.12 | 1.24 | 1.06 | 1.09 | 1.06 | — |
| 4 | override | 1.08 | 1.65 | — | — | — | — |
| 4 | mixed | 1.73 | 1.27 | 1.12 | — | — | — |
| 8 | add | 1.11 | 1.09 | 1.04 | 1.08 | 1.03 | — |
| 8 | override | 1.66 | 1.48 | 1.20 | — | — | — |
| 8 | mixed | 1.16 | 1.12 | 1.05 | 0.97 | — | — |
| 32 | add | 1.19 | 1.49 | 1.38 | 1.28 | 1.10 | 0.91 |
| 32 | override | 1.16 | 1.41 | 1.29 | 0.99 | 0.86 | — |
| 32 | mixed | 1.24 | 1.35 | 1.32 | 1.18 | 0.96 | — |
| 128 | add | — | — | 1.11 | — | 1.04 | 0.96 |
| 128 | override | — | — | 1.04 | — | 1.05 | 0.91 |
