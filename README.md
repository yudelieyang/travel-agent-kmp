# Travel Agent KMP

Offline Android and Desktop migration of the deterministic Python travel planner.
Frozen fixtures describe Python behavior, not real-world feasibility: prices are estimates,
and opening hours and routes are not validated. OpenStreetMap attribution is in NOTICE.md.

The project is under construction; no planner implementation is included in P1a.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :core:test -Pfixture=SEM200-001 --tests "travel.GoldenTest.fixtures"
.\gradlew.bat :composeApp:run
.\gradlew.bat :androidApp:installDebug
.\gradlew.bat :androidApp:connectedDebugAndroidTest
```

Synchronize fixtures from the pinned Python tag with `python tools/sync_golden.py`.


Android and Desktop startup show the snapshot and fixture candidate counts.
Android min SDK is 26, required by the current java.time-based snapshot validator.
