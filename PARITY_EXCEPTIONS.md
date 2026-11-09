# Parity exceptions

## Known Python issues (P5)

- SEM200-001 parses the destination as `to Boston`. Preserve this behavior until P5.

## Resolved during P1a

- Alias `Boſton`: Python casefold now resolves it to `Boston`.
- `PyStr.lower` handles Python 3.11 context-sensitive final sigma using the same Unicode case properties.
- Snapshot and mock candidate projections include source metadata, defaults and timestamps; verified against Python for the 48 city/category combinations in the frozen dataset.

## Accepted signed-zero exception (D7)

- Input: `-0.0` hotel budget. Python `max_price` is `-0.0` (`8000000000000000`), Kotlin `BigDecimal` yields `+0.0` (`0000000000000000`). IEEE 754 numeric equality holds; `TravelEngineTest.signedZeroBudgetAndDivisionByZero` fixes the expected behavior. UI money formatting normalizes displayed `-0.00` to `0.00`.

## Accepted numeric range exceptions

Accepted under D2 (B-class input values retain BigDecimal exactly; Python converts them to float):

- More than 15 significant digits: `1.2345678901234568`; Kotlin keeps `1.2345678901234568`, Python float becomes `1.2345678901234567`.
- Underflow/subnormal range: `4e-324`; Kotlin keeps `4E-324`, Python float becomes `5e-324`.
- Overflow range: `1e309`; Kotlin keeps `1E+309`, Python conversion becomes infinity and finite-only models reject it.

These accepted exceptions are fixed by `TravelEngineTest.declaredDecimalExceptionsRemainExact`.

## Pending confirmation exceptions

None identified. Golden round trips and tested provider inputs do not prove parity for every possible input.
