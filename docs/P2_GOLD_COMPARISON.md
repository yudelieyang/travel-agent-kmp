# P2 semantic gold comparison

The `hybrid_semantics_200_v1` annotations were evaluated with the original Python evaluator against the deterministic Python parser and the Kotlin parser. Both use Python source commit `0f498003c365c8a6d1bb1f066dc3afbd3bdb23fd` as the reference.

| Result | Python | Kotlin |
| --- | ---: | ---: |
| Cases passing the dataset gold checks | 76 / 200 (38%) | 76 / 200 (38%) |
| Cases with different failure categories | 0 | 0 |

The gold score measures agreement with dataset annotations; it is distinct from the 200/200 Kotlin-versus-Python fixture parity result. Machine-readable results are in `P2_GOLD_COMPARISON.json`.

To reproduce, run `./gradlew :core:test --tests travel.P2GoldExportTest`, followed by the Python repository's virtual-environment interpreter with `-B tools/evaluate_p2_gold.py` from this repository.
