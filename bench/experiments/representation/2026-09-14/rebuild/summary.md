# Standalone representation comparison

Completed 8/8 standard Criterium runs. One fresh JVM per row.

| Case | Implementation | ns/operation | B/operation |
| --- | --- | ---: | ---: |
| `extend/empty` | `clojure-persistent` | 31,24 | 120,00 |
| `extend/empty` | `clojure-rebuild` | 22,37 | 64,00 |
| `extend/small` | `clojure-rebuild` | 55,68 | 96,00 |
| `extend/small` | `clojure-persistent` | 57,57 | 184,00 |
| `extend/limit` | `clojure-persistent` | 72,87 | 216,00 |
| `extend/limit` | `clojure-rebuild` | 71,38 | 112,00 |
| `overwrite/small` | `clojure-rebuild` | 49,59 | 96,00 |
| `overwrite/small` | `clojure-persistent` | 43,18 | 192,00 |
