# P3 verification

- Python source and golden fixtures remained read-only; no parity exception was added.
- `:core:test`: 3,553 tests passed. `:composeApp:desktopTest`: 8 narrow-layout fixture flows, 8 wide-layout fixture flows, 9 display and typography checks, 3 focused UI checks, and 1 contrast test passed.
- The direct Android `PlanUiTest` was attempted after the device's background pop-up permission was enabled. It remained at the first ActivityScenario launch for over 60 seconds; even an external manual launch did not resume it. No specific system denial or root cause was established. The reproducible `AssistedPlanUiTest` and `tools/run_android_ui_tests.py` use an external adb foreground launch for each case instead.
- The merged Android manifest has no `INTERNET` permission.
- The phone and Desktop screenshots in `docs/screenshots/` were refreshed on the final build. The user manually accepted the 1.3× font check.

## P3.6 input guidance and preview

- Desktop Compose regression: 40/40 tests. The preview parity test additionally compares requirements and coverage for 30 semantic fixtures against `TravelEngine.run`.
- iQOO assisted instrumentation: `python tools/run_android_ui_tests.py travel.android.AssistedGuidanceUiTest 2` passed 2/2 (city insertion/replacement and live preview). The script foregrounds the activity because direct instrumentation activity startup hangs on this device; the underlying system cause has not been established.
- Final `:core:test`: 3,591/3,591 passed. No new parity exception. The only P3 core change exposes the existing errors list, separately committed and covered by Python differential tests.
- P3.6 screenshots: `09-unknown-city.png`, `10-no-options.png`, `11-input-guidance.png`, and `12-input-preview.png`.
- The failure catalog documents internal invariant and hotel-budget errors for which a natural-language trigger was not established; those UI cases were tested with constructed engine results.
