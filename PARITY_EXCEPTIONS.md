# Parity exceptions

## Known Python issues (P5)

- SEM200-001 parses the destination as `to Boston`. Preserve this behavior until P5.

## Unresolved parity exceptions

P1a verification is incomplete.

- Alias lookup: input `Boſton` is normalized by Python casefold to `Boston`; current JVM loader uses lowercase and does not match it. Fixture: none; field: destination alias. Python `Boston`; Kotlin `null`. Full Unicode casefold remains unresolved.
