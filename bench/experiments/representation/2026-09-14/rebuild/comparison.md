# Clojure array-map rebuild control

Cells are nanoseconds / allocated bytes per operation.

| Operation | Repeated `assoc` | Rebuild Clojure array map |
| --- | ---: | ---: |
| Extend 0 → 2 | 31.24 / 120 | 22.37 / 64 |
| Extend 4 → 6 | 57.57 / 184 | 55.68 / 96 |
| Extend 6 → 8 | 72.87 / 216 | 71.38 / 112 |
| Override 2 values within 6 | 43.18 / 192 | 49.59 / 96 |
