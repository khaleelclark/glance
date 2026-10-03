#!/usr/bin/env python3
"""Verify safe response details using production UI and synthetic emulator state."""
import json
import subprocess
import sys

sys.dont_write_bytecode = True
import validate_ui_overhaul as ui


def screenshot(name):
    path = ui.EVIDENCE / name
    path.write_bytes(subprocess.run(ui.ADB + ['exec-out', 'screencap', '-p'],
                                   check=True, capture_output=True, timeout=30).stdout)
    return str(path.relative_to(ui.ROOT))


def main():
    assert ui.SERIAL.startswith('emulator-')
    assert ui.adb('shell', 'getprop', 'ro.kernel.qemu').strip() == '1'
    assert ui.adb('shell', 'getprop', 'sys.boot_completed').strip() == '1'
    ui.adb('shell', 'run-as', 'com.khaleel.glance', 'pwd')
    ui.adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    ui.adb('shell', 'wm', 'dismiss-keyguard')
    ui.start('sourceDiagnostics')
    checks = json.loads(ui.adb('shell', 'run-as', 'com.khaleel.glance',
                               'cat', 'files/vpn-diagnostics-check.json'))
    ui.start('blockedConnections')
    assert ui.visible('HTTP 403') and ui.visible('web page response')
    checks['connectionErrorDetailsVisible'] = True
    screenshots = [screenshot('api36-vpn-connections-demo.png')]
    ui.tap('Try again', True)
    assert ui.events().get('retryPeople') == 1
    checks['retryDispatchesExactlyOnce'] = True
    ui.start('blockedSignIn')
    assert ui.visible('HTTP 403') and ui.visible('web page response')
    checks['signInErrorDetailsVisible'] = True
    screenshots.append(screenshot('api36-vpn-sign-in-demo.png'))
    result = {
        'device': 'API 36 emulator', 'version': '0.22.0',
        'source': 'working tree before release commit; final production cookie-deletion fix included',
        'synthetic': True, 'checks': checks, 'allPassed': all(checks.values()),
        'screenshots': screenshots, 'liveVPN': 'not tested',
        'realAccount': 'not tested', 'GalaxyS25Ultra': 'not tested',
    }
    (ui.EVIDENCE / 'api36-vpn-diagnostics.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(checks))


if __name__ == '__main__':
    main()
