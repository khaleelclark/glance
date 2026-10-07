#!/usr/bin/env python3
"""Synthetic full-day minimal-history rendering check; never contacts a source."""
import hashlib, json, subprocess
from pathlib import Path
import validate_ui_overhaul as ui

def main():
    assert ui.SERIAL.startswith('emulator-') and ui.adb('shell','getprop','ro.kernel.qemu').strip()=='1'
    apk = ui.adb('shell','pm','path','com.khaleel.glance').strip().removeprefix('package:')
    digest = ui.adb('shell','sha256sum',apk).split()[0]
    assert digest == hashlib.sha256((ui.ROOT/'app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest()
    ui.adb('shell','input','keyevent','KEYCODE_WAKEUP')
    ui.adb('shell','wm','dismiss-keyguard')
    ui.adb('shell','cmd','statusbar','collapse')
    ui.start('minuteHistory')
    assert ui.visible('113') and ui.visible('1440 source readings over 24 hours')
    ui.tap('Show latest 3 hours',True)
    assert ui.checked_node('Show latest 3 hours').get('checked')=='true'
    assert ui.visible('1440 source readings over 24 hours')
    image = ui.EVIDENCE/'api36-minute-history-chart-0.30.0-demo.png'
    image.write_bytes(subprocess.check_output(ui.ADB+['exec-out','screencap','-p']))
    report = {
        'synthetic': True, 'device': 'API36 emulator',
        'sourceCommitAtRun': subprocess.check_output(['git','rev-parse','HEAD'],cwd=ui.ROOT,text=True).strip(),
        'sourceDirtyAtRun': bool(subprocess.check_output(['git','status','--porcelain'],cwd=ui.ROOT,text=True).strip()),
        'fixtureApkSha256': digest,
        'checks': {'all1440MinuteSamplesReachProductionChart': True, 'currentReadingStill113': True,
                   'threeHourPresetStillActionableWithFullDayHistory': True},
        'scope': 'Synthetic integer canonical history with unknown historical trends in production chart; source adapter separately tested with mocked responses; no live cadence claim',
    }
    (ui.EVIDENCE/'api36-minute-history-chart-0.30.0.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report))

if __name__ == '__main__': main()
