#!/usr/bin/env python3
"""Inject one/two-finger events into the production chart on a debug emulator."""
import json
import argparse
import os
import re
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk"))
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("serial", nargs="?", default="emulator-5556")
parser.add_argument("--version", default="0.21.0", help="Version label for the evidence file")
args = parser.parse_args()
if not all(c.isalnum() or c in ".-" for c in args.version):
    parser.error("version must contain only letters, digits, dots or hyphens")
SERIAL = args.serial
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
    apk = adb("shell", "pm", "path", PKG).strip().removeprefix("package:")
    if not re.fullmatch(r"/data/app/[A-Za-z0-9/_.=+~\-]+/base\.apk", apk):
        raise RuntimeError("Unexpected fixture path")
    result["fixtureApkSha256"] = adb("shell", "sha256sum", apk).split()[0]
    result["sourceCommitAtRun"] = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    result["sourceDirtyAtRun"] = bool(subprocess.check_output(["git", "status", "--porcelain"], text=True).strip())
    result["fixtureType"] = "Debug-only synthetic APK; not a release deliverable"
    result["GalaxyS25Ultra"] = "not tested"
    result["liveAccount"] = "not tested"
    evidence = ROOT / "docs/evidence"
    evidence.mkdir(exist_ok=True)
    (evidence / f"api36-chart-{args.version}.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result["checks"]))


if __name__ == "__main__":
    main()
