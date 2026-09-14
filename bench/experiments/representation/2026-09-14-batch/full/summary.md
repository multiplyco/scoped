# Persistent/transient batch crossover

Protocol: `full`. Completed 8/8 isolated JVM runs. Screen times include overhead; full times use normal Criterium subtraction.

| Workload | Parent | Batch | Model | Fork | ns/op | B/op |
| --- | ---: | ---: | --- | ---: | ---: | ---: |
| override | 8 | 8 | clojure-persistent | 1 | 184,15 | 896,00 |
| override | 8 | 8 | clojure-transient | 1 | 198,96 | 216,00 |
| override | 32 | 16 | clojure-transient | 1 | 544,03 | 1091,88 |
| override | 32 | 16 | clojure-persistent | 1 | 618,55 | 4535,25 |
| override | 32 | 32 | clojure-persistent | 1 | 1081,86 | 9073,69 |
| override | 32 | 32 | clojure-transient | 1 | 908,88 | 1590,32 |
| add | 32 | 32 | clojure-transient | 1 | 1428,20 | 2633,32 |
| add | 32 | 32 | clojure-persistent | 1 | 1287,71 | 9233,87 |
