"""Restore only this task's emulator app data after instrumented tests."""
from pathlib import Path
import json
import subprocess
import sys

adb = r"C:\Users\hanan\AppData\Local\Android\Sdk\platform-tools\adb.exe"
package = "com.hananel.workout.rebuilt"
source = Path(sys.argv[1]).resolve()
expected = json.loads(source.read_bytes())

def run(*args):
    return subprocess.run([adb, "-s", "emulator-5554", *args], check=True, capture_output=True).stdout

run("shell", "am", "force-stop", package)
run("push", str(source), "/data/local/tmp/workout-35-restore.json")
run("shell", "run-as", package, "cp", "/data/local/tmp/workout-35-restore.json", "files/datastore/workout-v3.json")
actual = run("exec-out", "run-as", package, "cat", "files/datastore/workout-v3.json")
assert json.loads(actual) == expected
print("Restored and verified:", source.name)
