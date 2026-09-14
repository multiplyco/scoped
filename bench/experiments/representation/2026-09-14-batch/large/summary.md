# Persistent/transient batch crossover

Protocol: `screen`. Completed 28/28 isolated JVM runs. Screen times include overhead; full times use normal Criterium subtraction.

| Workload | Parent | Batch | Model | Fork | ns/op | B/op |
| --- | ---: | ---: | --- | ---: | ---: | ---: |
| add | 0 | 32 | clojure-persistent | 1 | 1796,22 | 7726,50 |
| add | 0 | 32 | clojure-transient | 1 | 1854,28 | 3188,43 |
| add | 0 | 64 | clojure-transient | 1 | 2904,36 | 4273,25 |
| add | 0 | 64 | clojure-persistent | 1 | 3245,14 | 16917,49 |
| add | 4 | 32 | clojure-persistent | 1 | 1776,96 | 8552,56 |
| add | 4 | 32 | clojure-transient | 1 | 1881,44 | 3404,12 |
| add | 8 | 32 | clojure-transient | 1 | 1882,00 | 3603,50 |
| add | 8 | 32 | clojure-persistent | 1 | 1828,74 | 9292,88 |
| add | 32 | 32 | clojure-persistent | 1 | 1279,37 | 9233,88 |
| add | 32 | 32 | clojure-transient | 1 | 1405,26 | 2631,80 |
| add | 32 | 64 | clojure-transient | 1 | 2488,36 | 3918,05 |
| add | 32 | 64 | clojure-persistent | 1 | 2731,16 | 18850,63 |
| override | 32 | 32 | clojure-persistent | 1 | 1082,70 | 9073,69 |
| override | 32 | 32 | clojure-transient | 1 | 926,58 | 1582,69 |
| mixed | 32 | 32 | clojure-transient | 1 | 1174,98 | 2132,50 |
| mixed | 32 | 32 | clojure-persistent | 1 | 1227,94 | 9142,37 |
| add | 128 | 8 | clojure-persistent | 1 | 368,83 | 2572,61 |
| add | 128 | 8 | clojure-transient | 1 | 409,94 | 1472,75 |
| add | 128 | 32 | clojure-transient | 1 | 1587,59 | 3888,87 |
| add | 128 | 32 | clojure-persistent | 1 | 1530,11 | 10454,53 |
| add | 128 | 64 | clojure-persistent | 1 | 3112,98 | 21282,30 |
| add | 128 | 64 | clojure-transient | 1 | 2984,44 | 5874,34 |
| override | 128 | 8 | clojure-transient | 1 | 351,26 | 960,38 |
| override | 128 | 8 | clojure-persistent | 1 | 337,81 | 2479,32 |
| override | 128 | 32 | clojure-persistent | 1 | 1284,30 | 9943,11 |
| override | 128 | 32 | clojure-transient | 1 | 1344,86 | 2235,50 |
| override | 128 | 64 | clojure-transient | 1 | 2295,69 | 2969,19 |
| override | 128 | 64 | clojure-persistent | 1 | 2518,14 | 19892,12 |
