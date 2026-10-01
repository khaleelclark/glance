#!/usr/bin/env python3
"""Check rejected alert saves against production UI with synthetic debug callbacks."""
import json
import re
import sys
sys.dont_write_bytecode = True
import validate_ui_overhaul as ui


def main():
    assert ui.SERIAL.startswith('emulator-')
    assert ui.adb('shell', 'getprop', 'ro.kernel.qemu').strip() == '1'
    ui.adb('shell', 'run-as', 'com.khaleel.glance', 'pwd')
    ui.adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    ui.adb('shell', 'wm', 'dismiss-keyguard')
    ui.start('alertSaveFailure')
    ui.tap('Settings', True)
    ui.tap('Alerts', True)
    ui.tap('Enable Low alert', True)
    assert ui.events().get('alertRejected') == 1
    assert ui.checked_node('Enable Low alert').get('checked') == 'false', \
        'Rejected toggle remained enabled in the editor'
    checks = {'rejectedToggleRestoresAuthoritativeRule': True}
    ui.tap('Edit alert', True)
    ui.scroll_to('Repeat cooldown')
    picker = next(n for n in ui.nodes() if n.get('class') == 'android.widget.NumberPicker')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', picker.get('bounds')))
    x, mid = (x1 + x2) // 2, (y1 + y2) // 2
    before = ui.events().get('alertRejected', 0)
    ui.adb('shell', 'input', 'swipe', str(x), str(mid + 100), str(x), str(mid - 100), '300')
    ui.settle()
    assert ui.events().get('alertRejected', 0) > before, 'Wheel did not dispatch a save'
    assert ui.visible('Alert at or below 70 mg/dL'), 'Rejected threshold remained displayed'
    checks['rejectedWheelSaveRestoresAuthoritativeThreshold'] = True
    ui.tap('Done', True)
    assert ui.visible('At or below 70 mg/dL'), 'Collapsed row retained an unsaved threshold'
    checks['collapsedRowMatchesAuthoritativeRule'] = True
    ui.start()
    ui.tap('Settings', True)
    ui.tap('Alerts', True)
    ui.tap('Enable Low alert', True)
    assert ui.events().get('alert') == 1
    assert ui.checked_node('Enable Low alert').get('checked') == 'true'
    checks['successfulToggleRetainsSavedRule'] = True
    ui.tap('Edit alert', True)
    ui.scroll_to('Repeat cooldown')
    picker = next(n for n in ui.nodes() if n.get('class') == 'android.widget.NumberPicker')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', picker.get('bounds')))
    x, mid = (x1 + x2) // 2, (y1 + y2) // 2
    tab = ui.find('More', True)
    tx1, ty1, tx2, ty2 = map(int, re.findall(r'\d+', tab.get('bounds')))
    before = ui.events().get('alert', 0)
    ui.adb('shell', 'input', 'swipe', str(x), str(mid + 170), str(x), str(mid - 170), '500')
    ui.adb('shell', 'input', 'tap', str((tx1 + tx2) // 2), str((ty1 + ty2) // 2))
    ui.settle()
    count = ui.events().get('alert', 0) - before
    assert 0 < count <= 2, 'Wheel/page-exit saves were missing or not coalesced: ' + str(count)
    saved = json.loads(ui.adb('shell', 'run-as', 'com.khaleel.glance', 'cat', 'files/ui-alert.json'))
    ui.tap('Alerts', True)
    assert saved['enabled']
    assert ui.visible('At or below ' + str(saved['thresholdMgDl']) + ' mg/dL')
    checks['successfulWheelSaveCoalescesAndSurvivesPageExit'] = True
    result = {'synthetic': True, 'checks': checks, 'allPassed': all(checks.values()),
              'scope': 'production Compose UI; debug callback rejects saves; no real disk failure injected'}
    ui.EVIDENCE.mkdir(exist_ok=True)
    (ui.EVIDENCE / 'alert-save-failure-0.21.0.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(checks))


if __name__ == '__main__':
    main()
