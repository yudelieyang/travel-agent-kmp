# Travel Agent KMP

Offline Android and Desktop migration of the deterministic Python travel planner.
Frozen fixtures describe Python behavior, not real-world feasibility: prices are estimates,
and opening hours and routes are not validated. OpenStreetMap attribution is in NOTICE.md.

The project is under construction; no planner implementation is included in P1a.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :core:test --tests "travel.GoldenTest.fixtureName"
.\gradlew.bat :composeApp:run
.\gradlew.bat :composeApp:installDebug
.\gradlew.bat :composeApp:connectedAndroidTest
```

Synchronize fixtures from the pinned Python tag with `python tools/sync_golden.py`.

## P1a status

The current `composeApp` is a multiplatform Android library, not an Android application.
Consequently `installDebug` and Android device-test commands above are not yet available.
Desktop compilation and JVM fixture round-trip tests have been exercised; the Android app
packaging and complete snapshot validation remain to be finished.
