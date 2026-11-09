# Parity exceptions

## Known Python issues (P5)

- SEM200-001 parses the destination as `to Boston`. Preserve this behavior until P5.

## Unresolved parity exceptions

- Alias lookup `Boſton`: closed; Python casefold mapping now resolves it to `Boston`.

- `PyStr.lower` context-sensitive Greek final sigma: fixture none; input `ΟΣ`; Python `ος` (U+03BF U+03C2), Kotlin `οσ` (U+03BF U+03C3). The handoff prescribes per-code-point lower mappings, which cannot express this contextual Python behavior. Pending user decision.

- Snapshot candidate projection: fixture `preset-snapshot`, field `candidates[*].provider`; Python provider emits `openstreetmap`, while the current `SnapshotLoader.candidates` returns raw snapshot rows without `provider` (missing). Other canonical metadata and defaults are likewise not projected. Pending full `TravelOption` projection before planner integration.
