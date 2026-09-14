# Standard Criterium confirmation

One fresh JVM per strategy and case, standard criterium/bench defaults. Cells are nanoseconds / allocated bytes per batch.

| Parent | Incoming bindings | Workload | Persistent assoc | Transient batch |
| ---: | ---: | --- | ---: | ---: |
| 8 | 8 | override | 184.15 / 896.00 | 198.96 / 216.00 |
| 32 | 16 | override | 618.55 / 4535.25 | 544.03 / 1091.88 |
| 32 | 32 | add | 1287.71 / 9233.87 | 1428.20 / 2633.32 |
| 32 | 32 | override | 1081.86 / 9073.69 | 908.88 / 1590.32 |
