# Travel Agent KMP

Offline Android and Desktop migration of the deterministic Python travel planner.
Frozen fixtures describe Python behavior, not real-world feasibility: prices are estimates,
and opening hours and routes are not validated. OpenStreetMap attribution is in NOTICE.md.

The deterministic engine accepts natural-language queries through `TravelEngine.run` and structured requirements through `TravelEngine.runWithRequirements`. The full travel UI is not yet implemented.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :core:test -Pfixture=SEM200-001 --tests "travel.GoldenTest.fixtures"
.\gradlew.bat :composeApp:run
.\gradlew.bat :androidApp:installDebug
.\gradlew.bat :androidApp:connectedDebugAndroidTest
```

Synchronize fixtures from the pinned Python `golden-v1.1` tag with `python tools/sync_golden.py`.
The P2 Python differential corpora can be regenerated with the adjacent Python repository's `.venv` interpreter using `tools/generate_regex_parity.py` and `tools/generate_function_parity.py`. Both validate the pinned Python source and avoid writing Python bytecode. See `docs/P2_GOLD_COMPARISON.json` for the semantic dataset evaluation.


The Android manifest declares no `INTERNET` permission; planning and the bundled fonts work offline.
On iQOO devices that suppress Activity launches from instrumentation, run P3 UI tests with `python tools/run_android_ui_tests.py` after installing the debug and androidTest APKs; the helper issues an external foreground launch for each test.
Android min SDK is 26, required by the current java.time-based snapshot validator.
