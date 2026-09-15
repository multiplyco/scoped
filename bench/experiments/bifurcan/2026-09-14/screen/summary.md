# Bifurcan scope storage

Protocol: `screen`. Completed 50/50 fresh JVMs. Time and bytes are per operation (chain = ten retained child maps).

Paired cases with differing Var hash layouts: 18. Fixtures rotate over many keys; repeat important cases to assess variation.

| Operation | Parent entries | Batch | Model | Fork | ns/op | B/op |
| --- | ---: | ---: | --- | ---: | ---: | ---: |
| override | 1 | 1 | clojure | 1 | 15,36 | 56,01 |
| override | 1 | 1 | bifurcan | 1 | 28,43 | 144,01 |
| hit | 1 | 1 | bifurcan | 1 | 11,49 | 0,01 |
| hit | 1 | 1 | clojure | 1 | 9,36 | 0,00 |
| override | 4 | 1 | clojure | 1 | 16,80 | 80,01 |
| override | 4 | 1 | bifurcan | 1 | 31,68 | 178,51 |
| hit | 4 | 1 | bifurcan | 1 | 12,54 | 0,01 |
| hit | 4 | 1 | clojure | 1 | 11,02 | 0,01 |
| override | 8 | 1 | clojure | 1 | 21,26 | 112,01 |
| override | 8 | 1 | bifurcan | 1 | 35,40 | 237,75 |
| hit | 8 | 1 | bifurcan | 1 | 13,01 | 0,01 |
| hit | 8 | 1 | clojure | 1 | 12,56 | 0,01 |
| override | 9 | 1 | clojure | 1 | 27,25 | 174,07 |
| override | 9 | 1 | bifurcan | 1 | 38,08 | 262,31 |
| hit | 9 | 1 | bifurcan | 1 | 12,84 | 0,01 |
| hit | 9 | 1 | clojure | 1 | 16,06 | 0,01 |
| override | 16 | 1 | clojure | 1 | 30,35 | 226,88 |
| override | 16 | 1 | bifurcan | 1 | 51,27 | 349,69 |
| hit | 16 | 1 | bifurcan | 1 | 14,33 | 0,01 |
| hit | 16 | 1 | clojure | 1 | 17,75 | 0,01 |
| override | 32 | 1 | clojure | 1 | 47,00 | 283,19 |
| override | 32 | 1 | bifurcan | 1 | 67,71 | 516,00 |
| hit | 32 | 1 | bifurcan | 1 | 15,56 | 0,01 |
| hit | 32 | 1 | clojure | 1 | 15,33 | 0,01 |
| add | 8 | 1 | clojure | 1 | 308,63 | 533,77 |
| add | 8 | 1 | bifurcan | 1 | 51,30 | 302,37 |
| miss | 8 | 1 | bifurcan | 1 | 12,62 | 0,01 |
| miss | 8 | 1 | clojure | 1 | 16,66 | 0,01 |
| miss | 32 | 1 | clojure | 1 | 16,20 | 0,01 |
| miss | 32 | 1 | bifurcan | 1 | 11,74 | 0,01 |
| batch | 1 | 2 | bifurcan | 1 | 67,61 | 179,01 |
| batch | 1 | 2 | clojure | 1 | 55,57 | 168,01 |
| batch | 1 | 8 | clojure | 1 | 127,69 | 192,01 |
| batch | 1 | 8 | bifurcan | 1 | 241,49 | 375,50 |
| batch | 8 | 2 | bifurcan | 1 | 80,24 | 359,63 |
| batch | 8 | 2 | clojure | 1 | 379,85 | 737,75 |
| batch | 8 | 8 | clojure | 1 | 545,12 | 809,83 |
| batch | 8 | 8 | bifurcan | 1 | 268,79 | 583,44 |
| batch | 32 | 2 | bifurcan | 1 | 113,01 | 643,42 |
| batch | 32 | 2 | clojure | 1 | 137,03 | 446,57 |
| batch | 32 | 8 | clojure | 1 | 419,58 | 887,70 |
| batch | 32 | 8 | bifurcan | 1 | 352,55 | 1084,37 |
| batch | 0 | 8 | bifurcan | 1 | 271,83 | 462,12 |
| batch | 0 | 8 | clojure | 1 | 245,86 | 216,01 |
| chain | 1 | 1 | clojure | 1 | 142,20 | 560,01 |
| chain | 1 | 1 | bifurcan | 1 | 271,68 | 1440,01 |
| chain | 8 | 1 | bifurcan | 1 | 304,13 | 2379,39 |
| chain | 8 | 1 | clojure | 1 | 146,70 | 1120,01 |
| chain | 32 | 1 | clojure | 1 | 350,43 | 2831,87 |
| chain | 32 | 1 | bifurcan | 1 | 454,36 | 5159,82 |
