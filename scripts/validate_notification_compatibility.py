#!/usr/bin/env python3
"""Run real framework grouping/Live Update shape checks on a dedicated emulator."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("serial", nargs="?", default="emulator-5556")
parser.add_argument("--suffix", required=True, help="Unique receipt suffix; preserve earlier runs")
parser.add_argument("--version", default="0.32.0", help="Version for the receipt filename")
args = parser.parse_args()
assert re.fullmatch(r"[A-Za-z0-9-]+", args.suffix)
assert re.fullmatch(r"[0-9.]+", args.version)
receipt_path = ROOT / ("docs/evidence/api36-notification-compatibility-" + args.version + "-" + args.suffix + ".json")
assert not receipt_path.exists(), "Choose a new suffix rather than overwrite evidence"
adb = [str(Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk")) / "platform-tools/adb"), "-s", args.serial]


def run(*commands):
    return subprocess.check_output(adb + list(commands), text=True, timeout=60)


assert args.serial.startswith("emulator-") and run("shell", "getprop", "ro.kernel.qemu").strip() == "1"
run("shell", "run-as", "com.khaleel.glance", "pwd")
head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
dirty = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip())
run("shell", "run-as", "com.khaleel.glance", "rm", "-f", "files/notification-compatibility-probe.json")
output = run("shell", "am", "instrument", "-w", "com.khaleel.glance/.debug.NotificationCompatibilityInstrumentation")
assert "INSTRUMENTATION_CODE: -1" in output, output
result = json.loads(run("shell", "run-as", "com.khaleel.glance", "cat", "files/notification-compatibility-probe.json"))
assert result["allPassed"] and len(result["checks"]) == (22 if result["framework"]["sdkFull"] >= 3700000 else 20) and all(result["checks"].values())
result.update(
    device="API36 emulator", sourceCommitAtRun=head, sourceDirtyAtRun=dirty,
    fixtureApkSha256=hashlib.sha256((ROOT / "app/build/outputs/apk/debug/app-debug.apk").read_bytes()).hexdigest(),
    SamsungOneUI="not tested", actualLiveUpdatePromotion="not tested", liveAccount="not tested",
)
receipt_path.write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps(result["checks"]))
