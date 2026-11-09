"""Run P3 Compose tests with a foreground launch for each case on iQOO devices.

The device may suppress Activity starts originating in instrumentation itself. An
external adb launch after each test starts keeps the test in the foreground.
"""
import os
from pathlib import Path
import subprocess
import sys

sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
adb = str(Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")) if sdk else "adb"
command = [adb, "shell", "am", "instrument", "-w", "-r", "-e", "class", "travel.android.AssistedPlanUiTest", "travel.android.test/androidx.test.runner.AndroidJUnitRunner"]
proc = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace", bufsize=1)
assert proc.stdout is not None
passes = 0
failures = 0
for line in proc.stdout:
    print(line, end="", flush=True)
    if line.strip() == "INSTRUMENTATION_STATUS_CODE: 1":
        subprocess.run([adb, "shell", "am", "start", "-n", "travel.android/.MainActivity"], check=True, stdout=subprocess.DEVNULL)
    elif line.strip() == "INSTRUMENTATION_STATUS_CODE: 0":
        passes += 1
    elif line.strip() in {"INSTRUMENTATION_STATUS_CODE: -1", "INSTRUMENTATION_STATUS_CODE: -2"}:
        failures += 1
code = proc.wait()
print(f"P3 Compose: {passes}/8 passed; {failures} failures")
sys.exit(0 if code == 0 and passes == 8 and failures == 0 else 1)
