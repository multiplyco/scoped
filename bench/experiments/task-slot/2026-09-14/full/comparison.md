# Task-slot comparison

Protocol: `full`. Each cell is mean ns / allocated bytes per complete sequence; bracketed values are individual JVM means.

| Mode | App entries | Depth | Map | Split | Split cached |
| --- | ---: | ---: | --- | --- | --- |
| handoff | 8 | 10 | 532.46 / 2480.01 [532.46] | 744.23 / 886.31 [744.23] | 494.47 / 793.04 [494.47] |
| nested | 8 | 10 | 538.44 / 2240.01 [538.44] | 835.26 / 1000.01 [835.26] | 781.07 / 680.01 [781.07] |
