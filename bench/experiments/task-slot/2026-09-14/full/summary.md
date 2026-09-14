# Separate task slot

Protocol: `full`. Completed 6/6 fresh JVMs. Time and bytes are per complete sequence.

| Mode | App entries | Depth | Model | Fork | ns/sequence | B/sequence |
| --- | ---: | ---: | --- | ---: | ---: | ---: |
| nested | 8 | 10 | map | 1 | 538,44 | 2240,01 |
| nested | 8 | 10 | split | 1 | 835,26 | 1000,01 |
| nested | 8 | 10 | split-cached | 1 | 781,07 | 680,01 |
| handoff | 8 | 10 | split-cached | 1 | 494,47 | 793,04 |
| handoff | 8 | 10 | split | 1 | 744,23 | 886,31 |
| handoff | 8 | 10 | map | 1 | 532,46 | 2480,01 |
