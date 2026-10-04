#!/usr/bin/env python3
"""Exercise Keystore-backed alarm storage with isolated synthetic emulator data."""

import argparse
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PKG = "com.khaleel.glance"
EXPECTED = {
    "allFourExactTypesPersisted",
    "recreatedStorePreservesRecords",
    "sourceAndPostingTimesDistinct",
    "onDiskPatientNameNotPlaintext",
    "clearPersistsAcrossRecreation",
    "isolatedSyntheticDirectory",
    "damagedCiphertextFailsClosed",
    "explicitClearRepairsUnreadableHistory",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial", nargs="?", default="emulator-5556")
    args = parser.parse_args()
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk"))
    command = [str(sdk / "platform-tools/adb"), "-s", args.serial]

    def adb(*words):
        return subprocess.run(command + list(words), check=True, capture_output=True,
                              text=True, timeout=60).stdout

    if not args.serial.startswith("emulator-") or adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("A dedicated Android emulator is required")
    adb("shell", "run-as", PKG, "pwd")
    target = "files/alarm-log-storage-check.json"
    # Never accept a leftover success receipt from a previous APK.
    adb("shell", "run-as", PKG, "rm", "-f", target)
    output = adb("shell", "am", "start", "-W", "-S", "-n",
                 PKG + "/.debug.DebugUiActivity", "--es", "mode", "alarmStorage")
    if "Error:" in output:
        raise RuntimeError(output)
    checks = json.loads(adb("shell", "run-as", PKG, "cat", target))
    if set(checks) != EXPECTED or any(value is not True for value in checks.values()):
        raise RuntimeError("Incomplete alarm-storage probe: " + str(checks))
    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, check=True,
                          capture_output=True, text=True).stdout.strip()
    dirty = bool(subprocess.run(["git", "status", "--porcelain"], cwd=ROOT,
                                check=True, capture_output=True, text=True).stdout.strip())
    report = {
        "synthetic": True,
        "sourceCommitAtRun": head,
        "sourceDirtyAtRun": dirty,
        "apiLevel": adb("shell", "getprop", "ro.build.version.sdk").strip(),
        "checks": checks,
        "allPassed": True,
        "scope": "Real SecureFileStore and Android Keystore; isolated synthetic no-backup directory",
        "GalaxyS25Ultra": "not tested",
        "liveAccount": "not tested",
    }
    evidence = ROOT / "docs/evidence"
    evidence.mkdir(exist_ok=True)
    (evidence / "api36-alarm-storage-0.23.0.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(checks))


if __name__ == "__main__":
    main()
