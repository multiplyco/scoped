# Standalone representation comparison

Completed 19/19 standard Criterium runs. One fresh JVM per row.

| Case | Implementation | ns/operation | B/operation |
| --- | --- | ---: | ---: |
| `lookup/small` | `clojure-persistent` | 5,27 | 0,00 |
| `lookup/small` | `var-scope` | 4,14 | 0,00 |
| `lookup/large` | `var-scope` | 11,97 | 0,00 |
| `lookup/large` | `clojure-persistent` | 10,50 | 0,00 |
| `extend/empty` | `clojure-persistent` | 31,46 | 120,00 |
| `extend/empty` | `clojure-transient` | 49,37 | 144,00 |
| `extend/empty` | `var-scope` | 17,98 | 56,00 |
| `extend/small` | `var-scope` | 25,75 | 88,00 |
| `extend/small` | `clojure-transient` | 104,40 | 200,00 |
| `extend/small` | `clojure-persistent` | 57,56 | 184,00 |
| `extend/crossover` | `clojure-persistent` | 309,29 | 602,75 |
| `extend/crossover` | `clojure-transient` | 361,45 | 735,56 |
| `extend/crossover` | `var-scope` | 267,81 | 433,63 |
| `extend/large` | `var-scope` | 113,42 | 457,31 |
| `extend/large` | `clojure-transient` | 120,70 | 459,50 |
| `extend/large` | `clojure-persistent` | 96,08 | 569,94 |
| `nested/small` | `clojure-persistent` | 111,01 | 384,00 |
| `nested/small` | `clojure-transient` | 158,73 | 408,00 |
| `nested/small` | `var-scope` | 53,23 | 184,00 |
