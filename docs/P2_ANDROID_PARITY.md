# P2 Android instrumented parity

On the USB-connected iQOO 15 (device model V2505A), the `travel.android.AndroidParityTest` instrumentation suite passed:

| Test | Passed | Total |
| --- | ---: | ---: |
| Regex pattern / corpus pairs, each checking six operations and source replacement templates | 49,536 | 49,536 |
| Requirements and coverage fixtures | 200 | 200 |

Android's regex engine rejects the JVM-only `UNICODE_CHARACTER_CLASS` flag. `PyRegex` retries without that flag only when Android reports it unsupported; the complete differential corpus above passes on the device. The test assets and generator pin the Python `golden-v1` commit.

Run with `./gradlew :androidApp:connectedDebugAndroidTest` with a device attached. Results are also written to the target application's private files directory as `regex-parity.txt` and `requirements-parity.txt`.
