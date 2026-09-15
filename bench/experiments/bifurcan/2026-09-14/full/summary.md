# Bifurcan scope storage

Protocol: `full`. Completed 14/14 fresh JVMs. Time and bytes are per operation (chain = ten retained child maps).

Paired cases with differing Var hash layouts: 3. Fixtures rotate over many keys; repeat important cases to assess variation.

| Operation | Parent entries | Batch | Model | Fork | ns/op | B/op |
| --- | ---: | ---: | --- | ---: | ---: | ---: |
| override | 1 | 1 | bifurcan | 1 | 27,70 | 144,01 |
| override | 1 | 1 | clojure | 1 | 14,90 | 56,01 |
| hit | 16 | 1 | clojure | 1 | 16,59 | 0,01 |
| hit | 16 | 1 | bifurcan | 1 | 14,04 | 0,01 |
| override | 8 | 1 | bifurcan | 1 | 34,11 | 237,94 |
| override | 8 | 1 | clojure | 1 | 18,59 | 112,01 |
| chain | 8 | 1 | clojure | 1 | 146,96 | 1120,01 |
| chain | 8 | 1 | bifurcan | 1 | 284,21 | 2379,38 |
| override | 32 | 1 | bifurcan | 1 | 65,63 | 521,26 |
| override | 32 | 1 | clojure | 1 | 44,05 | 283,19 |
| batch | 8 | 8 | clojure | 1 | 553,56 | 889,19 |
| batch | 8 | 8 | bifurcan | 1 | 293,77 | 579,69 |
| add | 8 | 1 | bifurcan | 1 | 52,68 | 305,13 |
| add | 8 | 1 | clojure | 1 | 285,69 | 493,75 |
