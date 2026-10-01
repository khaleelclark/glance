#!/usr/bin/env python3
"""Synthetic 0.18 retention checks on a dedicated API 36 emulator, never a phone."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get("ANDROID_HOME", Path.home() / "Android" / "Sdk"))
SERIAL = sys.argv[1] if len(sys.argv) == 2 else "emulator-5556"
ADB = [str(SDK / "platform-tools" / "adb"), "-s", SERIAL]
EVIDENCE = ROOT / "docs" / "evidence"


def adb(*args):
    return subprocess.run(ADB + list(args), check=True, capture_output=True, text=True).stdout


def fixture(mode, *extra):
    adb("shell", "am", "start", "-W", "-n",
        "com.khaleel.glance/.debug.DebugNotificationActivity", "--es", "mode", mode, *extra)


def probe(label):
    fixture("notification-probe")
    result = adb("shell", "run-as", "com.khaleel.glance", "cat", "files/notification-probe.txt")
    (EVIDENCE / f"api36-notification-0.18-{label}.txt").write_text(result)
    return dict(line.split(": ", 1) for line in result.splitlines())


def shade():
    adb("shell", "cmd", "statusbar", "expand-notifications")
    adb("shell", "uiautomator", "dump", "/sdcard/glance-retention.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/glance-retention.xml"))


def posted_probe(label):
    # Android can defer foreground-service presentation. Observe it before dismissal checks.
    deadline = time.monotonic() + 25
    while True:
        shade()
        result = probe(label)
        if result["Monitoring card posted"] == "true":
            return result
        if time.monotonic() >= deadline:
            raise RuntimeError("Foreground card was not posted within 25 seconds: " + str(result))


def swipe_glance():
    deadline = time.monotonic() + 15
    while True:
        root = shade()
        for node in root.iter("node"):
            if node.get("resource-id") == "android:id/app_name_text" and node.get("text") == "Glance":
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
                adb("shell", "input", "swipe", "1100", str((y1 + y2) // 2 + 100),
                    "100", str((y1 + y2) // 2 + 100), "300")
                # UI dump waits for the transition to settle, including the delete-intent callback.
                shade()
                return
        if time.monotonic() >= deadline:
            raise RuntimeError("Glance card was not found in the emulator shade within 15 seconds")


def main():
    if not SERIAL.startswith("emulator-") or adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("This fixture is restricted to an Android emulator")
    if "DEBUGGABLE" not in adb("shell", "dumpsys", "package", "com.khaleel.glance"):
        raise RuntimeError("Install the debug APK on a dedicated synthetic-data emulator first")
    EVIDENCE.mkdir(exist_ok=True)
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    checks = {}
    try:
        fixture("demo-clear")
        fixture("notification-retention", "--ez", "keep", "true")
        fixture("service-demo")
        adb("shell", "am", "start", "-W", "-n", "com.khaleel.glance/.MainActivity")
        fixture("notification-restore")
        shade()
        shade()
        before = posted_probe("before")
        assert before["Monitoring card posted"] == "true", before
        assert before["Clear-all protection flag"] == "true", before
        assert before["Public clear-all protection flag"] == "true", before
        checks["original_and_public_clear_protection"] = True
        fixture("dismiss-active", "--el", "generation", "-1")
        old = probe("stale-delete-intent")
        assert old["Regular card after dismissal"] == "false", old
        checks["old_generation_ignored"] = True
        adb("shell", "cmd", "notification", "post", "-t", "DEMO retention check",
            "glance-retention-clear", "DEMO disposable notification")
        root = shade()
        clear = next((node for node in root.iter("node")
                      if node.get("text") == "Clear all" or node.get("content-desc") == "Clear all"), None)
        if clear is None:
            raise RuntimeError("Clear all control not found with the disposable DEMO card posted")
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", clear.get("bounds")))
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        shade()
        clear_state = probe("clear-all")
        assert clear_state["Monitoring card posted"] == "true", clear_state
        assert "glance-retention-clear" not in adb("shell", "cmd", "notification", "list")
        checks["clear_all_removes_disposable_but_retains_glance"] = True
        swipe_glance()
        kept = probe("retained-swipe")
        assert kept["Monitoring card posted"] == "true", kept
        assert kept["Regular card after dismissal"] == "true", kept
        assert kept["Card updates suppressed"] == "false", kept
        assert kept["Promotion requested"] == "false", kept
        checks["real_swipe_restores_regular_card"] = True
        shade()
        image = subprocess.run(ADB + ["exec-out", "screencap", "-p"], check=True, capture_output=True).stdout
        (EVIDENCE / "api36-notification-0.18-retained.png").write_bytes(image)
        fixture("notification-privacy", "--ez", "visible", "true")
        posted_probe("public-values")
        fixture("notification-privacy", "--ez", "visible", "false")
        posted_probe("private-values")
        privacy = json.loads(adb("shell", "run-as", "com.khaleel.glance", "cat", "files/notification-privacy-probe.json"))
        assert all(privacy.values()), privacy
        (EVIDENCE / "api36-notification-0.18-privacy.json").write_text(json.dumps(privacy, indent=2) + "\n")
        checks["privacy_off_posts_redacted_card_without_numeric_icon_graph_or_chip"] = True
        fixture("notification-restore")
        fixture("notification-retention", "--ez", "keep", "false")
        swipe_glance()
        hidden = probe("manual-dismissal")
        assert hidden["Monitoring card posted"] == "false", hidden
        assert hidden["Card updates suppressed"] == "true", hidden
        checks["retention_off_respects_real_swipe"] = True
        fixture("notification-retention", "--ez", "keep", "true")
        fixture("notification-restore")
        fixture("service-pause")
        fixture("dismiss-saved")
        stopped = probe("stopped")
        assert stopped["Monitoring card posted"] == "false", stopped
        checks["stop_removes_card_and_no_restoration"] = True
        for mode, label in (("service-stop-person", "person-change"), ("service-stop-account", "sign-out")):
            fixture("service-demo")
            adb("shell", "am", "start", "-W", "-n", "com.khaleel.glance/.MainActivity")
            fixture("notification-restore")
            shade()
            assert posted_probe(label + "-before")["Monitoring card posted"] == "true"
            fixture(mode)
            fixture("dismiss-saved")
            assert probe(label)["Monitoring card posted"] == "false"
            checks[label + "_rejects_old_delete_intent"] = True
        (EVIDENCE / "api36-notification-0.18-retention.json").write_text(
            json.dumps({"sdk": adb("shell", "getprop", "ro.build.version.sdk").strip(),
                        "checks": checks, "promotion_on_emulator": False,
                        "samsung_now_bar": "not tested"}, indent=2) + "\n")
        print(json.dumps(checks))
    finally:
        fixture("demo-clear")
        fixture("notification-retention", "--ez", "keep", "true")


if __name__ == "__main__":
    main()
