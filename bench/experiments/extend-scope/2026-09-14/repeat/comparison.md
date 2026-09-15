# Scope-extension comparison

Time and allocation per complete map update. Forks are shown separately; percentages compare each candidate with its matching baseline fork.

| Workload | Bindings | Fork | Model | Mean ns | Mean CI ns | B | Time reduction | Bytes saved |
| --- | ---: | ---: | --- | ---: | --- | ---: | ---: | ---: |
| override | 11 | 1 | array | 338.42 | 336.78–340.89 | 936.01 | 0.0% | 0.00 |
| override | 11 | 1 | helpers | 367.41 | 365.50–370.68 | 832.01 | -8.6% | 104.00 |
| override | 11 | 1 | inline | 358.66 | 357.02–361.97 | 832.01 | -6.0% | 104.00 |
