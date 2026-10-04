#!/usr/bin/env python3
"""Exercise the 0.23 production UI with debug-only synthetic fixtures."""
import json
from pathlib import Path
import re
import subprocess
import sys
sys.dont_write_bytecode = True
import validate_ui_overhaul as ui

CHECKS = {}


def scroll_to(text, upward=False):
    for _ in range(12):
        current = ui.nodes()
        if any(text in ui.label(n) for n in current):
            return
        scrollers = [n for n in current if n.get('scrollable') == 'true']
        assert scrollers, 'No scrollable container for: ' + text
        for node in scrollers:
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
            if y2 - y1 < 150:
                continue
            x = x1 + 12  # Stay outside the chart/wheel's pointer-consuming area.
            top, bottom = int(y1 + (y2 - y1) * .2), int(y1 + (y2 - y1) * .8)
            ui.adb('shell', 'input', 'swipe', str(x), str(top if upward else bottom),
                   str(x), str(bottom if upward else top), '250')
            ui.settle()
    raise AssertionError('Scroll target missing: ' + text)


ui.scroll_to = scroll_to
ui.scroll_up_to = lambda text: scroll_to(text, upward=True)


def screenshot(name):
    path = ui.EVIDENCE / f"api36-ui-0.23.0-{name}-demo.png"
    path.write_bytes(subprocess.run(ui.ADB + ['exec-out', 'screencap', '-p'],
                                   check=True, capture_output=True, timeout=30).stdout)


def wheel():
    return next(n for n in ui.nodes() if n.get('class') == 'android.widget.NumberPicker')


def move_wheel():
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', wheel().get('bounds')))
    x, mid = (x1 + x2) // 2, (y1 + y2) // 2
    ui.adb('shell', 'input', 'swipe', str(x), str(mid + 90), str(x), str(mid - 90), '300')
    ui.settle()


def main():
    assert ui.SERIAL.startswith('emulator-')
    assert ui.adb('shell', 'getprop', 'ro.kernel.qemu').strip() == '1'
    ui.adb('shell', 'run-as', 'com.khaleel.glance', 'pwd')
    assert ui.adb('shell', 'getprop', 'sys.boot_completed').strip() == '1'
    ui.EVIDENCE.mkdir(exist_ok=True)
    originals = {key: ui.adb('shell', 'settings', 'get', 'system', key).strip()
                 for key in ('font_scale', 'user_rotation', 'accelerometer_rotation')}
    night = ui.adb('shell', 'cmd', 'uimode', 'night').strip()
    error = None
    try:
        ui.adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
        ui.adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
        ui.adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
        ui.adb('shell', 'cmd', 'uimode', 'night', 'no')
        ui.adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        ui.adb('shell', 'wm', 'dismiss-keyguard')
        ui.start()
        assert ui.visible('113') and ui.visible('Reading at') and ui.visible('Glucose trend')
        assert ui.visible('Now') and ui.visible('Logbook') and ui.visible('Settings')
        assert not ui.visible('People')
        CHECKS['readingTimestampGraphAndThreeDestinations'] = True
        screenshot('main-light')
        ui.tap('Refresh reading', True)
        assert ui.events() == {'refresh': 1}
        CHECKS['compactRefreshDispatchesOnce'] = True
        for hours in (12, 24, 6):
            label = f'Show latest {hours} hours'
            ui.scroll_to(label)
            ui.tap(label, True)
            assert ui.checked_node(label).get('checked') == 'true'
        CHECKS['sixTwelveTwentyFourHourChoicesWork'] = True
        screenshot('graph-light')
        ui.tap('Logbook', True)
        for label in ('Low', 'Urgent Low', 'High', 'Urgent High'):
            ui.scroll_to(label)
            assert ui.visible(label)
        assert ui.visible('Source reading') and ui.visible('Posted')
        CHECKS['logHasAllFourTypesAndSourcePostingTimes'] = True
        screenshot('logbook')
        ui.scroll_up_to('Clear')
        before = ui.events()
        ui.tap('Clear', True)
        assert ui.visible('Clear alert log?') and ui.events() == before
        ui.tap('Cancel', True)
        assert ui.events() == before
        CHECKS['logClearConfirmationCancelHasNoSideEffects'] = True
        ui.tap('Clear', True)
        ui.tap('Clear log', True)
        assert ui.visible('No alerts yet') and ui.events().get('clearLog') == 1
        assert ui.events().get('signOut', 0) == 0 and ui.events().get('delete', 0) == 0
        CHECKS['logClearOnlyClearsHistoryAndShowsEmptyState'] = True
        screenshot('logbook-empty')
        ui.adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        ui.settle()
        assert ui.visible('113') and ui.visible('Glucose trend')
        CHECKS['systemBackReturnsToNow'] = True
        ui.tap('Settings', True)
        ui.tap('Units')
        assert ui.visible('Display units')
        ui.tap('mmol/L', True)
        assert ui.events().get('unit') == 1
        ui.tap('Refresh cadence')
        ui.tap('Every 5 minutes', True)
        assert ui.events().get('interval') == 1
        CHECKS['unitsAndCadenceQuickEditSheetsSaveOnce'] = True
        ui.scroll_to('Lock-screen values')
        assert ui.checked_node('Lock-screen values').get('checked') == 'false'
        ui.tap('Lock-screen values')
        assert ui.checked_node('Lock-screen values').get('checked') == 'true'
        assert ui.events().get('privacy') == 1
        CHECKS['privacyToggleHasCheckedSemanticsAndSingleDispatch'] = True
        ui.scroll_to('Presentation')
        ui.tap('Presentation')
        ui.tap('Live Update', True)
        assert ui.events().get('liveUpdate') == 1
        ui.tap('Presentation')
        ui.tap('Graph', True)
        assert ui.events().get('liveUpdate') == 2
        CHECKS['presentationSheetKeepsBothNotificationChoices'] = True
        ui.scroll_to('Show notification')
        ui.tap('Show notification', True)
        assert ui.events().get('restore') == 1
        before = ui.events()
        ui.tap('How it works', True)
        assert ui.visible('How notifications work') and ui.events() == before
        ui.tap('Done', True)
        CHECKS['notificationRestoreAndHelpRemainReachable'] = True
        screenshot('settings-notifications')
        ui.scroll_to('App color')
        ui.tap('App color')
        before = ui.events()
        ui.tap('Custom color', True)
        ui.scroll_to('Hex color')
        assert ui.visible('Hex color') and ui.events() == before
        screenshot('custom-color')
        ui.adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        ui.settle()
        CHECKS['customColorWheelAndHexRemainReachable'] = True
        for label in ('Show widget values', 'Share watch readings', 'Watch notification', 'Change person',
                      'Reconnect LibreLinkUp', 'Diagnostics', 'Delete local data', 'Sign out'):
            ui.scroll_to(label)
        CHECKS['widgetWatchAccountAndDiagnosticsActionsRemainReachable'] = True
        before = ui.events()
        ui.tap('Sign out', True)
        assert ui.visible('Monitoring will stop') and ui.events() == before
        ui.tap('Cancel', True)
        assert ui.events() == before
        CHECKS['signOutConfirmationCancelPreservesAccount'] = True
        ui.adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        ui.settle()
        assert ui.visible('6.3')
        CHECKS['unitChoiceRetainedAcrossNavigation'] = True
        ui.start('alertSaveFailure')
        ui.tap('Settings', True)
        ui.scroll_to('Enable Low alert')
        ui.tap('Enable Low alert', True)
        assert ui.events().get('alertRejected') == 1
        assert ui.checked_node('Enable Low alert').get('checked') == 'false'
        ui.tap('Low', True)
        move_wheel()
        assert ui.events().get('alertRejected', 0) > 1
        assert ui.visible('Alert at or below 70 mg/dL')
        screenshot('alert-rejected')
        ui.scroll_to('Done')
        ui.tap('Done', True)
        assert ui.visible('At or below 70 mg/dL')
        CHECKS['rejectedAlertToggleAndWheelRestoreAuthoritativeValues'] = True
        ui.start()
        ui.tap('Settings', True)
        ui.scroll_to('Enable Low alert')
        ui.tap('Enable Low alert', True)
        assert ui.checked_node('Enable Low alert').get('checked') == 'true'
        ui.tap('Low', True)
        move_wheel()
        ui.scroll_to('Done')
        ui.tap('Done', True)
        saved = json.loads(ui.adb('shell', 'run-as', 'com.khaleel.glance', 'cat', 'files/ui-alert.json'))
        assert saved['enabled'] and saved['thresholdMgDl'] != 70
        assert ui.visible('At or below ' + str(saved['thresholdMgDl']) + ' mg/dL')
        CHECKS['successfulAlertWheelSaveSurvivesSheetDismissal'] = True
        ui.start('stale')
        assert ui.visible('No recent reading') and ui.visible('Unavailable')
        screenshot('stale')
        ui.start('offline')
        assert ui.visible('Connection failed') and ui.visible('Showing the last reading')
        CHECKS['staleAndOfflineStatesRemainTruthful'] = True
        ui.start('signIn')
        assert ui.visible('LibreLinkUp') and ui.visible('Password') and ui.visible('encrypted')
        screenshot('sign-in')
        CHECKS['setupAndEncryptedCredentialNoticeRemainReachable'] = True
        ui.adb('shell', 'cmd', 'uimode', 'night', 'yes')
        ui.start()
        assert ui.visible('113') and ui.visible('Glucose trend')
        screenshot('main-dark')
        CHECKS['darkModeRendersReadingAndGraph'] = True
        ui.adb('shell', 'settings', 'put', 'system', 'font_scale', '1.5')
        ui.start()
        assert ui.visible('113') and ui.visible('mg/dL') and ui.visible('Logbook')
        screenshot('large-font')
        ui.tap('Settings', True)
        ui.tap('Units')
        assert ui.visible('Display units') and ui.visible('mmol/L')
        screenshot('sheet-large-font')
        CHECKS['largeFontReadingNavigationAndUnitSheetRemainReachable'] = True
        ui.adb('shell', 'settings', 'put', 'system', 'font_scale', '2.0')
        ui.start()
        assert ui.visible('113') and ui.visible('mg/dL') and ui.visible('Settings')
        screenshot('largest-font')
        ui.tap('Logbook', True)
        ui.scroll_to('Urgent High')
        screenshot('logbook-largest-font')
        CHECKS['twoHundredPercentFontKeepsReadingNavigationAndLogReachable'] = True
        ui.adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
        ui.adb('shell', 'settings', 'put', 'system', 'user_rotation', '1')
        ui.start()
        assert ui.visible('113') and ui.visible('Glucose trend')
        ui.scroll_to('Show latest 12 hours')
        screenshot('landscape')
        CHECKS['landscapeHasReadingGraphAndTimeChoices'] = True
        ui.tap('Settings', True)
        ui.scroll_to('App color')
        ui.tap('App color')
        ui.tap('Custom color', True)
        ui.scroll_to('Hex color')
        screenshot('color-landscape')
        ui.scroll_to('Done')
        ui.tap('Done', True)
        ui.scroll_up_to('Enable Low alert')
        ui.tap('Low', True)
        ui.scroll_to('Done')
        screenshot('alert-landscape')
        ui.tap('Done', True)
        CHECKS['landscapeColorAndAlertSheetsScrollToTheirControls'] = True
    except Exception as failure:
        error = str(failure)
        screenshot('failure')
        raise
    finally:
        for key, value in originals.items():
            ui.adb('shell', 'settings', 'put', 'system', key, value)
        ui.adb('shell', 'cmd', 'uimode', 'night', 'yes' if 'yes' in night else 'no')
        report = {'synthetic': True, 'device': 'API 36 emulator', 'checks': CHECKS,
                  'allPassed': error is None, 'failure': error,
                  'liveAccount': 'not tested', 'GalaxyS25Ultra': 'not tested',
                  'settingsPersistence': 'fixture callbacks; encrypted log is checked separately'}
        (ui.EVIDENCE / 'api36-ui-0.23.0.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(CHECKS))


if __name__ == '__main__':
    main()
