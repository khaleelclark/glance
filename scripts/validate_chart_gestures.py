#!/usr/bin/env python3
"""Inject one/two-finger events into the production chart on a debug emulator."""
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk"))
SERIAL = sys.argv[1] if len(sys.argv) == 2 else "emulator-5556"
ADB = [str(SDK / "platform-tools/adb"), "-s", SERIAL]
PKG = "com.khaleel.glance"


def adb(*args):
    return subprocess.run(ADB + list(args), check=True, capture_output=True,
                          text=True, timeout=60).stdout


def main():
    if not SERIAL.startswith("emulator-") or adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("Dedicated Android emulator required")
    adb("shell", "run-as", PKG, "pwd")  # Reject non-debuggable APKs.
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    adb("shell", "cmd", "statusbar", "collapse")
    # Remove stale success before the single test attempt.
    adb("shell", "run-as", PKG, "rm", "-f", "files/chart-gesture-probe.json")
    output = adb("shell", "am", "instrument", "-w", PKG + "/.debug.ChartGestureInstrumentation")
    if "INSTRUMENTATION_CODE: -1" not in output:
        raise RuntimeError(output)
    result = json.loads(adb("shell", "run-as", PKG, "cat", "files/chart-gesture-probe.json"))
    if not result["allPassed"] or not all(result["checks"].values()):
        raise RuntimeError("Incomplete gesture checks")
    result["GalaxyS25Ultra"] = "not tested"
    result["liveAccount"] = "not tested"
    (ROOT / "docs/evidence/api36-chart-0.19.2.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result["checks"]))


if __name__ == "__main__":
    main()
