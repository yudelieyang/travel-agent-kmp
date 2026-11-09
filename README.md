# Travel Agent KMP

Offline Android and Desktop migration of the deterministic Python travel planner.
Frozen fixtures describe Python behavior, not real-world feasibility: prices are estimates,
and opening hours and routes are not validated. OpenStreetMap attribution is in NOTICE.md.

The deterministic engine accepts natural-language queries through `TravelEngine.run` and structured requirements through `TravelEngine.runWithRequirements`. The Compose interface presents stage playback, validation and repair, an offline coordinate sketch, and the resulting itinerary.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :core:test -Pfixture=SEM200-001 --tests "travel.GoldenTest.fixtures"
.\gradlew.bat :composeApp:run
.\gradlew.bat :androidApp:installDebug
.\gradlew.bat :androidApp:assembleDebugAndroidTest
python tools/run_android_ui_tests.py
```

Synchronize fixtures from the pinned Python `golden-v1.1` tag with `python tools/sync_golden.py`.
The P2 Python differential corpora can be regenerated with the adjacent Python repository's `.venv` interpreter using `tools/generate_regex_parity.py` and `tools/generate_function_parity.py`. Both validate the pinned Python source and avoid writing Python bytecode. See `docs/P2_GOLD_COMPARISON.json` for the semantic dataset evaluation.


The Android manifest declares no `INTERNET` permission; planning and the bundled fonts work offline.
On the tested iQOO 15, direct `PlanUiTest` execution remained at the first ActivityScenario launch after the background pop-up permission was enabled. An exact cause was not established. After installing the debug and androidTest APKs, `python tools/run_android_ui_tests.py` runs `AssistedPlanUiTest` with an external adb foreground launch for each case. Other Android instrumented suites can be selected explicitly with `adb shell am instrument -w -e class travel.android.AndroidParityTest travel.android.test/androidx.test.runner.AndroidJUnitRunner`.
Android min SDK is 26, required by the current java.time-based snapshot validator.
