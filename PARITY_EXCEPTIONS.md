# Parity exceptions

## Known Python issues (P5)

- SEM200-001 parses the destination as `to Boston`. Preserve this behavior until P5.

## Resolved during P1a

- Alias `Boſton`: Python casefold now resolves it to `Boston`.
- `PyStr.lower` handles Python 3.11 context-sensitive final sigma using the same Unicode case properties.
- Snapshot and mock candidate projections include source metadata, defaults and timestamps; verified against Python for the 48 city/category combinations in the frozen dataset.

## Unresolved parity exceptions

None currently identified. Golden round trips and the tested provider inputs do not prove parity for every possible input.
