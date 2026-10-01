#!/usr/bin/env python3
"""Synthetic refresh regression checks on a dedicated debug emulator."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk"))
SERIAL = sys.argv[1] if len(sys.argv) == 2 else "emulator-5556"
ADB = [str(SDK / "platform-tools/adb"), "-s", SERIAL]
PKG = "com.khaleel.glance"


def adb(*args):
    return subprocess.run(ADB + list(args), check=True, capture_output=True, text=True, timeout=30).stdout


def fixture(mode, *extra):
    adb("shell", "am", "start", "-W", "-n", PKG + "/.debug.DebugNotificationActivity", "--es", "mode", mode, *extra)


def probe():
    fixture("retry-probe")
    return json.loads(adb("shell", "run-as", PKG, "cat", "files/retry-probe.json"))


def screen():
    adb("shell", "am", "start", "-W", "-n", PKG + "/.MainActivity")
    adb("shell", "uiautomator", "dump", "/sdcard/glance-refresh.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/glance-refresh.xml"))


def wait_debounce():
    # Respect the deliberate local debounce while testing the longer automatic backoff.
    time.sleep(3.5)


def tap_refresh():
    wait_debounce()
    for node in screen().iter("node"):
        if node.get("text") == "Refresh now":
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            return
    raise RuntimeError("Refresh button missing")


def settled(min_attempts=0):
    deadline = time.monotonic() + 15
    while True:
        state = probe()
        if state["connection"] != "FETCHING" and state["attempts"] >= min_attempts:
            return state
        if time.monotonic() >= deadline:
            raise RuntimeError("Request did not settle")


def main():
    if not SERIAL.startswith("emulator-") or adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("Dedicated Android emulator required")
    adb("shell", "run-as", PKG, "pwd")  # Reject non-debuggable APKs.
    adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
    checks = {}
    fixture("service-demo")
    assert settled()["connection"] == "READY"

    # The production controller receives a typed DNS/network failure from a debug source.
    fixture("retry-source", "--es", "failure", "NETWORK")
    tap_refresh()
    failed = settled()
    assert failed["connection"] == "OFFLINE" and failed["retryReason"] == "TRANSIENT"
    assert failed["deadlineInFuture"] and failed["hasReading"]
    checks["networkFailureRetainsHistoricalReadingAndAutomaticBackoff"] = True
    fixture("retry-source", "--es", "failure", "NONE")
    tap_refresh()  # The real Compose callback retries before the automatic deadline.
    recovered = settled(failed["attempts"] + 1)
    assert recovered["connection"] == "READY" and recovered["retryReason"] == "NONE"
    assert recovered["attempts"] == failed["attempts"] + 1
    checks["mainRefreshRecoversBeforeNetworkBackoffExpires"] = True

    fixture("retry-source", "--es", "failure", "SERVER")
    tap_refresh()
    failed = settled()
    assert failed["connection"] == "BACKING_OFF" and failed["retryReason"] == "TRANSIENT"
    fixture("retry-source", "--es", "failure", "NONE")
    wait_debounce()
    fixture("retry-service-refresh")  # Exercise the generation-checked service path.
    recovered = settled(failed["attempts"] + 1)
    assert recovered["connection"] == "READY" and recovered["attempts"] == failed["attempts"] + 1
    checks["serviceRefreshRecoversBeforeServerBackoffExpires"] = True

    fixture("retry-source", "--es", "failure", "RATE_LIMITED")
    prior = probe()["attempts"]
    tap_refresh()
    limited = settled(prior + 1)
    assert limited["retryReason"] == "PROVIDER" and limited["deadlineInFuture"]
    fixture("retry-source", "--es", "failure", "NONE")
    tap_refresh()
    blocked = settled()
    assert blocked["attempts"] == limited["attempts"] and blocked["retryReason"] == "PROVIDER"
    checks["providerCooldownBlocksMainRefresh"] = True
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-W", "-n", PKG + "/.MainActivity")
    # Force-stop is an explicit system stop; resume deliberately without signing in again.
    fixture("service-start")
    tap_refresh()
    blocked = settled()
    assert blocked["attempts"] == limited["attempts"] and blocked["retryReason"] == "PROVIDER"
    assert blocked["connection"] == "BACKING_OFF"
    checks["providerCooldownSurvivesProcessRecreation"] = True

    fixture("retry-legacy-seed")
    tap_refresh()
    blocked = settled()
    assert blocked["retryReason"] == "PROVIDER" and blocked["attempts"] == limited["attempts"]
    checks["legacyDeadlineWithoutReasonIsConservativelyPreserved"] = True
    texts = [node.get("text", "") for node in screen().iter("node")]
    assert any("LibreLinkUp is limiting requests. Try again after" in text for text in texts)
    checks["providerWaitExplainsReasonAndTime"] = True
    output = ROOT / "docs/evidence/api36-refresh-0.19.1.json"
    output.write_text(json.dumps({"device": "Android 16 API 36 emulator", "synthetic": True,
        "checks": checks, "allPassed": True, "actualDnsResolver": "not manipulated; typed NETWORK simulated",
        "GalaxyS25Ultra": "not tested", "liveAccount": "not tested"}, indent=2) + "\n")
    fixture("demo-clear")
    print(json.dumps(checks))


if __name__ == "__main__":
    main()
