#!/usr/bin/env python3
"""Synthetic production-screen checks for the fixed black presentation."""
import json,re,subprocess
from pathlib import Path
import validate_ui_overhaul as ui

def snapshot(name):
    (ui.EVIDENCE/f'api36-ui-0.26.0-{name}-demo.png').write_bytes(
        subprocess.run(ui.ADB+['exec-out','screencap','-p'],check=True,capture_output=True,timeout=30).stdout)

def bounds(node):return tuple(map(int,re.findall(r'\d+',node.get('bounds'))))

def scroll_to(text):
    for _ in range(12):
        if ui.visible(text):return
        root=ui.tree()
        scrolling=[n for n in root.iter('node') if n.get('scrollable')=='true']
        column=next((n for n in scrolling if any('source readings over 24 hours' in ui.label(c) for c in n.iter('node'))),scrolling[0])
        left,top,right,bottom=bounds(column)
        ui.adb('shell','input','swipe',str(left+8),str(top+(bottom-top)*3//4),str(left+8),str(top+(bottom-top)//4),'250');ui.settle()
    raise AssertionError('Scroll target missing: '+text)

def main():
    assert ui.SERIAL.startswith('emulator-') and ui.adb('shell','getprop','ro.kernel.qemu').strip()=='1'
    ui.adb('shell','run-as','com.khaleel.glance','pwd')
    original={k:ui.adb('shell','settings','get','system',k).strip() for k in ('font_scale','user_rotation','accelerometer_rotation')}
    original_night=ui.adb('shell','cmd','uimode','night').strip()
    checks={}
    try:
        ui.adb('shell','settings','put','system','font_scale','1.0')
        ui.adb('shell','settings','put','system','accelerometer_rotation','0')
        ui.adb('shell','settings','put','system','user_rotation','0')
        ui.adb('shell','cmd','uimode','night','no')
        ui.start('justNow')
        header='Reading at 11:59 AM · Just Now'
        label=ui.find(header,True)
        value=ui.find('113',True)
        assert bounds(label)[3] < bounds(value)[1]
        assert sum(n.get('text')==header for n in ui.nodes())==1
        assert not ui.visible('Current reading') and not ui.visible('In range')
        checks['sourceTimeAndActualAgeAppearOnceAboveGlucose']=True
        assert 'Selected source reading' not in ui.find('source readings over 24 hours').get('content-desc')
        snapshot('just-now-system-light')
        checks['idleMainGraphHasNoSelectedReadingHover']=True
        ui.start()
        assert ui.visible('Reading at 11:59 AM · 1 min ago') and not ui.visible('Just Now')
        checks['ageAdvancesInsteadOfHardcodedJustNow']=True
        for h in (3,6,12,24):
            scroll_to(f'Show latest {h} hours');ui.tap(f'Show latest {h} hours',True)
            assert ui.checked_node(f'Show latest {h} hours').get('checked')=='true'
            assert 'Selected source reading' not in ui.find('source readings over 24 hours').get('content-desc')
        checks['presetsDoNotReopenHover']=True
        ui.start('stale')
        assert ui.visible('No recent reading') and ui.visible('Reading at 11:40 AM · 20 min ago')
        snapshot('stale')
        checks['staleWarningAndSourceAgeRemainHonest']=True
        for mode,value in [('low','65'),('high','250')]:
            ui.start(mode);assert ui.visible(value) and ui.visible('Reading at 11:59 AM · 1 min ago')
            snapshot(mode)
        checks['lowHighSourceValuesStillRender']=True
        ui.start()
        ui.tap('Settings',True)
        scroll_to('Share watch readings')
        assert ui.visible('DEVICES')
        assert not ui.visible('App color') and not ui.visible('Custom color') and not ui.visible('Hex color')
        snapshot('settings-devices')
        checks['themePickerRemovedFromSettings']=True
        ui.start('justNow');ui.adb('shell','cmd','uimode','night','yes');ui.start('justNow')
        assert ui.visible(header) and ui.visible('113');snapshot('just-now-system-dark')
        checks['fixedPresentationWorksUnderBothSystemModes']=True
        ui.adb('shell','settings','put','system','font_scale','2.0')
        for rotation,name in [('0','portrait'),('1','landscape')]:
            ui.adb('shell','settings','put','system','user_rotation',rotation);ui.start('justNow')
            assert ui.visible('113') and ui.visible('Settings') and ui.visible(header)
            snapshot('large-text-'+name+'-header')
            scroll_to('Show latest 3 hours');ui.tap('Show latest 3 hours',True)
            assert ui.checked_node('Show latest 3 hours').get('checked')=='true'
            snapshot('large-text-'+name+'-presets')
            checks[name+'AtTwoHundredPercentKeepsHeaderAndPresetsReachable']=True
        apk=ui.adb('shell','pm','path','com.khaleel.glance').strip().removeprefix('package:')
        assert re.fullmatch(r'/data/app/[A-Za-z0-9/_.=+~\-]+/base\.apk',apk)
        report={'synthetic':True,'device':'API 36 emulator','checks':checks,'allPassed':True,'fixtureApkSha256':ui.adb('shell','sha256sum',apk).split()[0],
          'sourceCommitAtRun':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
          'sourceDirtyAtRun':bool(subprocess.check_output(['git','status','--porcelain'],text=True).strip()),
          'scope':'Production GlanceRoot with synthetic state and callbacks; screenshots need visual inspection for colors/clipping',
          'liveAccount':'not tested','SamsungOneUI':'not tested','TalkBack':'not tested'}
        (ui.EVIDENCE/'api36-ui-0.26.0.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(checks))
    finally:
        for k,v in original.items():ui.adb('shell','settings','put','system',k,v)
        ui.adb('shell','cmd','uimode','night','yes' if 'yes' in original_night else 'no')

if __name__=='__main__':main()
