# Scope-extension call shape

Protocol: `full`. Completed 12/12 fresh JVMs. Time and bytes are per map update.

| Workload | Parent entries | Bindings | Model | Fork | ns/update | B/update |
| --- | ---: | ---: | --- | ---: | ---: | ---: |
| add | 8 | 11 | array | 1 | 738,68 | 1448,01 |
| add | 8 | 11 | helpers | 1 | 609,48 | 1384,01 |
| add | 8 | 11 | inline | 1 | 610,40 | 1344,01 |
| override | 32 | 11 | inline | 1 | 366,57 | 832,01 |
| override | 32 | 11 | helpers | 1 | 382,02 | 832,00 |
| override | 32 | 11 | array | 1 | 343,49 | 936,01 |
| add | 8 | 20 | array | 1 | 1148,15 | 3280,01 |
| add | 8 | 20 | helpers | 1 | 1044,53 | 3104,01 |
| add | 8 | 20 | inline | 1 | 1102,70 | 3144,01 |
| override | 32 | 20 | inline | 1 | 570,33 | 1208,00 |
| override | 32 | 20 | helpers | 1 | 533,42 | 1208,00 |
| override | 32 | 20 | array | 1 | 566,92 | 1384,01 |
