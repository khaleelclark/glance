#!/usr/bin/env python3
"""Exercise real Now/Settings restore controls on a dedicated synthetic emulator."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("serial", nargs="?", default="emulator-5556")
parser.add_argument("--suffix", required=True)
args = parser.parse_args()
assert re.fullmatch(r"[A-Za-z0-9-]+", args.suffix)
output = ROOT / ("docs/evidence/api36-live-update-recovery-0.32.0-" + args.suffix + ".json")
assert not output.exists(), "Preserve earlier receipts"
adb = [str(Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk")) / "platform-tools/adb"), "-s", args.serial]


def run(*commands):
    return subprocess.check_output(adb + list(commands), text=True, timeout=45)


def fixture(mode):
    run("shell", "am", "start", "-W", "-n", "com.khaleel.glance/.debug.DebugNotificationActivity", "--es", "mode", mode)


def nodes():
    run("shell", "uiautomator", "dump", "/sdcard/glance-live-recovery.xml")
    return list(ET.fromstring(run("shell", "cat", "/sdcard/glance-live-recovery.xml")).iter("node"))


def tap(label):
    matches = [node for node in nodes() if node.get("text") == label]
    assert matches, "Missing control: " + label
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", matches[-1].get("bounds")))
    run("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def main():
    run("shell", "cmd", "statusbar", "collapse")
    run("shell", "am", "start", "-W", "--activity-single-top", "-n", "com.khaleel.glance/.MainActivity")


def probe():
    fixture("notification-probe")
    return dict(line.split(": ", 1) for line in run("shell", "run-as", "com.khaleel.glance", "cat", "files/notification-probe.txt").splitlines())


def await_dismissal(value):
    deadline = time.monotonic() + 8
    while True:
        nodes()  # Wait for the Activity transition and delivered broadcast to settle.
        result = probe()
        if result["Regular card after dismissal"] == value:
            return result
        assert time.monotonic() < deadline, result


assert args.serial.startswith("emulator-") and run("shell", "getprop", "ro.kernel.qemu").strip() == "1"
assert "DEBUGGABLE" in run("shell", "dumpsys", "package", "com.khaleel.glance")
head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
dirty = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip())
checks = {}
try:
    run("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    run("shell", "wm", "dismiss-keyguard")
    fixture("demo-clear")
    # Finish prior fixture teardown before creating the new synthetic service session.
    run("shell", "am", "force-stop", "com.khaleel.glance")
    fixture("notification-retention")
    fixture("service-demo")
    main()
    tap("Settings")
    tap("Presentation")
    tap("Live Update")
    assert probe()["Regular card after dismissal"] == "false"
    main()
    tap("Now")
    assert not any(node.get("text") == "Restore Live Update" for node in nodes())
    checks["nowRestoreActionAbsentBeforeDismissal"] = True
    fixture("dismiss-active")
    dismissed = await_dismissal("true")
    assert dismissed["Regular card after dismissal"] == "true" and dismissed["Promotion requested"] == "false"
    checks["deliveredDismissalRestoresRegularWithoutAutomaticPromotion"] = True
    main()
    tap("Restore Live Update")
    # The actual button must refresh its own snapshot and disappear immediately.
    assert not any(node.get("text") == "Restore Live Update" for node in nodes())
    assert probe()["Regular card after dismissal"] == "false"
    checks["nowRestoreActionClearsDismissalAndDisappears"] = True
    fixture("dismiss-active")
    assert await_dismissal("true")["Regular card after dismissal"] == "true"
    main()
    tap("Settings")
    tap("Restore Live Update")
    assert probe()["Regular card after dismissal"] == "false"
    checks["settingsRestoreLiveUpdateClearsDismissal"] = True
    apk_hash = hashlib.sha256((ROOT / "app/build/outputs/apk/debug/app-debug.apk").read_bytes()).hexdigest()
    installed = run("shell", "pm", "path", "com.khaleel.glance").strip().removeprefix("package:")
    assert run("shell", "sha256sum", installed).split()[0] == apk_hash
    result = dict(synthetic=True, sdk=int(run("shell", "getprop", "ro.build.version.sdk").strip()),
        sourceCommitAtRun=head, sourceDirtyAtRun=dirty, fixtureApkSha256=apk_hash,
        installedApkHashVerified=True, checks=checks,
        scope="Actual Now/Settings UI after synthetic current-session delete callbacks; no Samsung/API37/promotion/real shade swipe qualification")
    output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result))
finally:
    fixture("demo-clear")
