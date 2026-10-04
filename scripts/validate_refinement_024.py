#!/usr/bin/env python3
"""Check the 0.24 production UI with synthetic debug data on a dedicated emulator."""
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
import validate_ui_overhaul as ui

checks = {}


def snapshot(name):
    (ui.EVIDENCE / f'api36-ui-0.24.0-{name}-demo.png').write_bytes(
        subprocess.run(ui.ADB + ['exec-out', 'screencap', '-p'], check=True,
                       capture_output=True, timeout=30).stdout)


def checked(label):
    assert ui.checked_node(label).get('checked') == 'true'


def no_boilerplate():
    labels = [ui.label(n) for n in ui.nodes()]
    assert not any(any(text.lower() in label.lower() for text in (
        'arranged around daily use', 'queues to paired watch', 'glucose alerts posted by',
        'source readings only', 'Pinch to zoom')) for label in labels)


def scroll_to(text):
    for _ in range(12):
        root = ui.tree()
        if any(text in ui.label(n) for n in root.iter('node')):
            return
        scrolling = [n for n in root.iter('node') if n.get('scrollable') == 'true']
        chart_parent = next((n for n in scrolling if any(
            'Glucose trend' in ui.label(child) or 'source readings over 24 hours' in ui.label(child)
            for child in n.iter('node'))), None)
        node = chart_parent if chart_parent is not None else scrolling[0]
        left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
        # The left rail is outside the chart's touch-consuming Canvas. Use the
        # chart's own scrolling column in landscape, rather than the summary.
        x = left + 8
        ui.adb('shell', 'input', 'swipe', str(x), str(top + (bottom-top)*3//4),
               str(x), str(top + (bottom-top)//4), '250')
        ui.settle()
    raise AssertionError('Scroll target missing: ' + text)


def tap_plot():
    chart = ui.find('source readings over 24 hours')
    left, top, right, bottom = map(int, re.findall(r'\d+', chart.get('bounds')))
    ui.adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
    ui.settle()


def main():
    assert ui.SERIAL.startswith('emulator-')
    assert ui.adb('shell', 'getprop', 'ro.kernel.qemu').strip() == '1'
    assert ui.adb('shell', 'getprop', 'sys.boot_completed').strip() == '1'
    ui.adb('shell', 'run-as', 'com.khaleel.glance', 'pwd')
    ui.EVIDENCE.mkdir(exist_ok=True)
    old = {key: ui.adb('shell', 'settings', 'get', 'system', key).strip()
           for key in ('font_scale', 'user_rotation', 'accelerometer_rotation')}
    old_night = ui.adb('shell', 'cmd', 'uimode', 'night').strip()
    try:
        ui.adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
        ui.adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
        ui.adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
        ui.adb('shell', 'cmd', 'uimode', 'night', 'no')
        ui.start()
        assert ui.visible('113') and ui.visible('In range') and ui.visible('Reading at')
        assert not any(n.get('text') == 'Pause' for n in ui.nodes())
        assert not any(n.get('text') == 'Monitoring is running' for n in ui.nodes())
        assert ui.visible('Monitoring is running')  # Small dot retains spoken status.
        checks['compactHeaderHasActivityDotAndNoPauseOrStatusStrip'] = True
        ui.tap('Refresh reading', True)
        assert ui.events() == {'refresh': 1}
        checks['refreshArrowDispatchesOnce'] = True
        no_boilerplate()
        snapshot('fresh-light')
        for mode, value, label in [('low', '65', 'Below Low limit'), ('high', '250', 'Above High limit')]:
            ui.start(mode)
            assert ui.visible(value) and ui.visible(label)
            snapshot(mode + '-light')
        checks['lowInRangeHighPresentDistinctSourceRangeLabels'] = True
        ui.start('stale')
        assert ui.visible('No recent reading') and not ui.visible('Current reading · In range')
        snapshot('stale')
        checks['staleReadingDoesNotPresentCurrentRange'] = True
        ui.start()
        for hours in (3, 6, 12, 24):
            scroll_to(f'Show latest {hours} hours')
            ui.tap(f'Show latest {hours} hours', True)
            checked(f'Show latest {hours} hours')
        checks['threeSixTwelveTwentyFourHourPresetsAreSelectable'] = True
        ui.tap('Show latest 3 hours', True)
        before = ui.find('source readings over 24 hours').get('content-desc')
        tap_plot()
        after = ui.find('source readings over 24 hours').get('content-desc')
        assert before != after and 'Selected source reading' in after and 'measured' in after
        no_boilerplate()
        snapshot('selected-three-hour')
        checks['graphScrubUsesOnGraphReadoutWithoutBottomCard'] = True
        ui.start()
        ui.tap('Logbook', True)
        assert ui.visible('Low') and ui.visible('65 mg/dL, trend Holding steady')
        no_boilerplate()
        snapshot('logbook')
        ui.tap('Settings', True)
        no_boilerplate()
        scroll_to('Share watch readings')
        no_boilerplate()
        checks['settingsAndLogbookBoilerplateRemoved'] = True
        ui.adb('shell', 'cmd', 'uimode', 'night', 'yes')
        ui.start()
        assert ui.visible('113') and ui.visible('In range')
        snapshot('fresh-dark')
        checks['darkReadingAndOpenGraphRender'] = True
        for mode, value, label in [('low', '65', 'Below Low limit'), ('high', '250', 'Above High limit')]:
            ui.start(mode)
            assert ui.visible(value) and ui.visible(label)
            snapshot(mode + '-dark')
        checks['darkLowAndHighReadingsRender'] = True
        ui.adb('shell', 'settings', 'put', 'system', 'font_scale', '2.0')
        ui.start()
        assert ui.visible('113') and ui.visible('Now') and ui.visible('Settings')
        scroll_to('Show latest 3 hours')
        ui.tap('Show latest 3 hours', True)
        checked('Show latest 3 hours')
        snapshot('largest-font')
        checks['twoHundredPercentFontKeepsReadingNavigationAndPresetsReachable'] = True
        ui.adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
        ui.adb('shell', 'settings', 'put', 'system', 'user_rotation', '1')
        ui.start()
        assert ui.visible('113') and ui.visible('Now')
        scroll_to('Show latest 3 hours')
        ui.tap('Show latest 3 hours', True)
        checked('Show latest 3 hours')
        snapshot('landscape')
        checks['landscapeKeepsReadingAndThreeHourPresetReachable'] = True
        apk = ui.adb('shell', 'pm', 'path', 'com.khaleel.glance').strip().removeprefix('package:')
        assert re.fullmatch(r'/data/app/[A-Za-z0-9/_.=+~\-]+/base\.apk', apk)
        assert len(checks) == 11 and all(checks.values())
        report = {'synthetic': True, 'device': 'API 36 emulator', 'checks': checks, 'allPassed': True,
                  'fixtureApkSha256': ui.adb('shell', 'sha256sum', apk).split()[0],
                  'sourceCommitAtRun': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
                  'sourceDirtyAtRun': bool(subprocess.check_output(['git', 'status', '--porcelain'], text=True).strip()),
                  'fixtureType': 'Debug-only synthetic APK; not a release deliverable',
                  'TalkBack': 'not tested', 'liveAccount': 'not tested', 'GalaxyS25Ultra': 'not tested'}
        (ui.EVIDENCE / 'api36-ui-0.24.0.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(checks))
    finally:
        for key, value in old.items():
            ui.adb('shell', 'settings', 'put', 'system', key, value)
        ui.adb('shell', 'cmd', 'uimode', 'night', 'yes' if 'yes' in old_night else 'no')


def font_fix():
    assert ui.SERIAL.startswith('emulator-')
    assert ui.adb('shell', 'getprop', 'ro.kernel.qemu').strip() == '1'
    ui.adb('shell', 'run-as', 'com.khaleel.glance', 'pwd')
    old = {key: ui.adb('shell', 'settings', 'get', 'system', key).strip()
           for key in ('font_scale', 'user_rotation', 'accelerometer_rotation')}
    old_night = ui.adb('shell', 'cmd', 'uimode', 'night').strip()
    focused = {}
    try:
        ui.adb('shell', 'settings', 'put', 'system', 'font_scale', '2.0')
        ui.adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
        ui.adb('shell', 'cmd', 'uimode', 'night', 'yes')
        for rotation, name in [('0', 'portrait'), ('1', 'landscape')]:
            ui.adb('shell', 'settings', 'put', 'system', 'user_rotation', rotation)
            ui.start()
            assert ui.visible('113') and ui.visible('Settings')
            scroll_to('Show latest 3 hours')
            ui.tap('Show latest 3 hours', True)
            checked('Show latest 3 hours')
            description = ui.find('source readings over 24 hours').get('content-desc')
            assert 'Selected source reading 113 mg/dL' in description and 'measured' in description
            snapshot('font-fix-' + name)
            focused[name + 'AtTwoHundredPercentKeepsReadingAndPresetAndSourceSemantics'] = True
        apk = ui.adb('shell', 'pm', 'path', 'com.khaleel.glance').strip().removeprefix('package:')
        assert re.fullmatch(r'/data/app/[A-Za-z0-9/_.=+~\-]+/base\.apk', apk)
        report = {'synthetic': True, 'device': 'API 36 emulator', 'checks': focused, 'allPassed': True,
                  'fixtureApkSha256': ui.adb('shell', 'sha256sum', apk).split()[0],
                  'sourceCommitAtRun': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
                  'sourceDirtyAtRun': bool(subprocess.check_output(['git', 'status', '--porcelain'], text=True).strip()),
                  'scope': 'Focused 200% font follow-up; inspect screenshots for actual visual clipping',
                  'TalkBack': 'not tested', 'liveAccount': 'not tested', 'GalaxyS25Ultra': 'not tested'}
        (ui.EVIDENCE / 'api36-ui-0.24.0-font-fix.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(focused))
    finally:
        for key, value in old.items():
            ui.adb('shell', 'settings', 'put', 'system', key, value)
        ui.adb('shell', 'cmd', 'uimode', 'night', 'yes' if 'yes' in old_night else 'no')


if __name__ == '__main__':
    font_fix() if '--font-fix' in sys.argv else main()
