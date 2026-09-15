# Scope-extension call shape

Protocol: `screen`. Completed 24/24 fresh JVMs. Time and bytes are per map update.

| Workload | Parent entries | Bindings | Model | Fork | ns/update | B/update |
| --- | ---: | ---: | --- | ---: | ---: | ---: |
| add | 8 | 11 | array | 1 | 686,51 | 1488,01 |
| add | 8 | 11 | helpers | 1 | 635,64 | 1384,01 |
| add | 8 | 11 | inline | 1 | 620,33 | 1384,01 |
| override | 32 | 11 | inline | 1 | 373,55 | 832,01 |
| override | 32 | 11 | helpers | 1 | 371,16 | 832,01 |
| override | 32 | 11 | array | 1 | 397,05 | 936,01 |
| add | 8 | 20 | array | 1 | 1230,73 | 3280,01 |
| add | 8 | 20 | helpers | 1 | 1079,25 | 3104,01 |
| add | 8 | 20 | inline | 1 | 1089,92 | 3104,01 |
| override | 32 | 20 | inline | 1 | 562,75 | 1208,01 |
| override | 32 | 20 | helpers | 1 | 568,06 | 1208,01 |
| override | 32 | 20 | array | 1 | 598,84 | 1384,01 |
| add | 8 | 11 | inline | 2 | 615,79 | 1384,01 |
| add | 8 | 11 | helpers | 2 | 695,76 | 1424,01 |
| add | 8 | 11 | array | 2 | 710,75 | 1488,01 |
| override | 32 | 11 | array | 2 | 388,45 | 936,01 |
| override | 32 | 11 | helpers | 2 | 373,90 | 832,00 |
| override | 32 | 11 | inline | 2 | 363,40 | 832,00 |
| add | 8 | 20 | inline | 2 | 1159,61 | 3104,01 |
| add | 8 | 20 | helpers | 2 | 1189,03 | 3104,01 |
| add | 8 | 20 | array | 2 | 1364,36 | 3280,01 |
| override | 32 | 20 | array | 2 | 617,84 | 1384,01 |
| override | 32 | 20 | helpers | 2 | 537,94 | 1208,01 |
| override | 32 | 20 | inline | 2 | 553,71 | 1208,01 |
