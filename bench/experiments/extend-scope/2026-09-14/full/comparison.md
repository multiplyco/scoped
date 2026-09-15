# Scope-extension comparison

Time and allocation per complete map update. Forks are shown separately; percentages compare each candidate with its matching baseline fork.

| Workload | Bindings | Fork | Model | Mean ns | Mean CI ns | B | Time reduction | Bytes saved |
| --- | ---: | ---: | --- | ---: | --- | ---: | ---: | ---: |
| add | 11 | 1 | array | 738.68 | 730.36–748.61 | 1448.01 | 0.0% | 0.00 |
| add | 11 | 1 | helpers | 609.48 | 607.44–613.80 | 1384.01 | 17.5% | 64.00 |
| add | 11 | 1 | inline | 610.40 | 608.43–615.10 | 1344.01 | 17.4% | 104.00 |
| override | 11 | 1 | inline | 366.57 | 365.31–368.73 | 832.01 | -6.7% | 104.00 |
| override | 11 | 1 | helpers | 382.02 | 380.78–384.33 | 832.00 | -11.2% | 104.00 |
| override | 11 | 1 | array | 343.49 | 342.04–346.72 | 936.01 | 0.0% | 0.00 |
| add | 20 | 1 | array | 1148.15 | 1143.94–1155.29 | 3280.01 | 0.0% | 0.00 |
| add | 20 | 1 | helpers | 1044.53 | 1041.24–1050.40 | 3104.01 | 9.0% | 176.00 |
| add | 20 | 1 | inline | 1102.70 | 1096.58–1111.71 | 3144.01 | 4.0% | 136.00 |
| override | 20 | 1 | inline | 570.33 | 567.80–574.86 | 1208.00 | -0.6% | 176.00 |
| override | 20 | 1 | helpers | 533.42 | 531.46–537.59 | 1208.00 | 5.9% | 176.00 |
| override | 20 | 1 | array | 566.92 | 564.53–572.53 | 1384.01 | 0.0% | 0.00 |
