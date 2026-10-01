#!/usr/bin/env python3
"""Exercise production GlanceRoot with debug-only synthetic callbacks on an emulator."""
import json, os, re, subprocess, sys, time
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
SERIAL = sys.argv[1] if len(sys.argv) > 1 else 'emulator-5556'
ADB = [str(Path(os.environ.get('ANDROID_HOME',Path.home()/'Android/Sdk'))/'platform-tools/adb'), '-s', SERIAL]
EVIDENCE = ROOT/'docs/evidence'
checks = {}
def adb(*args):
    return subprocess.run(ADB+list(args),check=True,capture_output=True,text=True,timeout=60).stdout

def settle(): time.sleep(0.6)
def tree():
    adb('shell','uiautomator','dump','/sdcard/glance-ui.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/glance-ui.xml'))
def nodes(): return list(tree().iter('node'))
def label(n): return n.get('text','')+' '+n.get('content-desc','')
def find(text,exact=False):
    for n in nodes():
        if (text in [n.get('text'),n.get('content-desc')] if exact else text in label(n)): return n
    raise AssertionError('Visible control missing: '+text)
def visible(text): return any(text in label(n) for n in nodes())
def tap(text,exact=False):
    n=find(text,exact)
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));settle()
def events(): return json.loads(adb('shell','run-as','com.khaleel.glance','cat','files/ui-events.json'))
def start(mode='fresh'):
    adb('shell','am','start','-W','-S','-n','com.khaleel.glance/.debug.DebugUiActivity','--es','mode',mode);time.sleep(2)
def screenshot(name):
    (EVIDENCE/f'api36-ui-0.20.0-{name}-demo.png').write_bytes(subprocess.run(ADB+['exec-out','screencap','-p'],check=True,capture_output=True,timeout=30).stdout)
def scroll_to(text):
    for _ in range(10):
        if visible(text): return
        adb('shell','input','swipe','400','1450','400','450','250');settle()
    raise AssertionError('Scroll target missing: '+text)
def checked_node(text):
    root=tree()
    for n in root.iter('node'):
        if n.get('checkable')=='true' and any(text in label(child) for child in n.iter('node')): return n
    raise AssertionError('Missing toggle semantics: '+text)

def main():
    assert SERIAL.startswith('emulator-') and adb('shell','getprop','ro.kernel.qemu').strip()=='1'
    adb('shell','run-as','com.khaleel.glance','pwd')
    assert adb('shell','getprop','sys.boot_completed').strip()=='1'
    EVIDENCE.mkdir(exist_ok=True)
    original_scale=adb('shell','settings','get','system','font_scale').strip()
    original_mode=adb('shell','cmd','uimode','night').strip()
    original_rotation=adb('shell','settings','get','system','user_rotation').strip()
    original_auto=adb('shell','settings','get','system','accelerometer_rotation').strip()
    try:
        adb('shell','settings','put','system','font_scale','1.0')
        adb('shell','settings','put','system','accelerometer_rotation','0')
        adb('shell','settings','put','system','user_rotation','0')
        adb('shell','cmd','uimode','night','no')
        adb('shell','input','keyevent','KEYCODE_WAKEUP');adb('shell','wm','dismiss-keyguard')
        start()
        assert visible('113') and visible('Reading at') and visible('History')
        checks['freshValueTimestampAndGraphVisible']=True
        screenshot('main-light')
        tap('Refresh',True);tap('Restore card',True)
        assert events()=={'refresh':1,'restore':1}
        checks['compactActionsDispatchOnce']=True
        tap('Settings',True)
        assert visible('Display') and visible('Notifications') and not visible('Custom hex color')
        screenshot('settings-light')
        before=events();tap('App color');assert visible('Custom hex color')
        assert events()==before
        checks['colorWheelAndHexRemainAvailableWithoutSideEffects']=True
        tap('App color');tap('mmol/L',True)
        assert events().get('unit')==1
        tap('Notifications',True)
        assert checked_node('Lock-screen values').get('checked')=='false'
        assert visible('Anyone can see the name and reading while locked.')
        before=events();tap('About Lock-screen values')
        assert visible("Show the person's name, glucose value, and reading time on the lock screen.")
        assert events()==before
        checks['privacySummaryAndExpandableHelpWithoutToggle']=True
        tap('About Lock-screen values');tap('Lock-screen values')
        assert checked_node('Lock-screen values').get('checked')=='true' and events().get('privacy')==1
        checks['settingToggleHasCheckedSemanticsAndDispatchesOnce']=True
        tap('Notifications',True)
        tap('Display',True)
        for title in ['Widgets','Galaxy Watch','Glucose alerts','Monitoring','Account']:
            scroll_to(title);tap(title,True)
            expected={'Widgets':'Widget values','Galaxy Watch':'Watch readings','Glucose alerts':'Value & repeat','Monitoring':'Resume after restart','Account':'Sign out'}[title]
            scroll_to(expected);assert visible(expected)
            # Collapse after returning toward each section's heading.
            for _ in range(8):
                if visible(title):break
                adb('shell','input','swipe','400','500','400','1400','250');settle()
            tap(title,True)
        checks['allSettingsSectionsAndRepresentativeControlsReachable']=True
        tap('Account',True);scroll_to('Sign out');before=events();tap('Sign out',True)
        assert visible('Cancel') and visible('Monitoring will stop') and events()==before
        tap('Cancel',True);assert events()==before
        checks['destructiveConfirmationAndCancelPreserved']=True
        adb('shell','input','keyevent','KEYCODE_BACK');settle()
        assert visible('History') and visible('6.3')
        checks['systemBackReturnsToMainAndUnitChoiceRetained']=True
        start('stale');assert visible('No recent reading') and visible('Trend · Unavailable')
        checks['staleLabelAndSuppressedCurrentTrendPreserved']=True
        screenshot('stale')
        start('offline');assert visible('Connection failed') and visible('Showing the last reading')
        checks['offlineMessageRemainsVisible']=True
        start('signIn');assert visible('Use your LibreLinkUp follower account.') and visible('Password')
        checks['signInFieldsAndEncryptedStorageNoticeReachable']=visible('Sign-in is stored encrypted on this phone.')
        screenshot('sign-in')
        adb('shell','cmd','uimode','night','yes');start()
        assert visible('113') and visible('History');screenshot('main-dark')
        checks['darkMainRenders']=True
        adb('shell','settings','put','system','font_scale','1.5');start()
        assert visible('113') and visible('mg/dL') and visible('Settings')
        screenshot('large-font')
        tap('Settings',True);assert visible('Display') and visible('Notifications')
        screenshot('settings-large-font')
        checks['largeFontReadingUnitsAndSettingsReachable']=True
        adb('shell','settings','put','system','font_scale','1.0')
        adb('shell','settings','put','system','user_rotation','1');start()
        assert visible('113') and visible('History');screenshot('landscape')
        checks['landscapeReadingAndGraphRender']=True
    finally:
        adb('shell','settings','put','system','font_scale',original_scale)
        adb('shell','settings','put','system','user_rotation',original_rotation)
        adb('shell','settings','put','system','accelerometer_rotation',original_auto)
        adb('shell','cmd','uimode','night','yes' if 'yes' in original_mode else 'no')
        result={'synthetic':True,'device':'API 36 emulator','checks':checks,'allPassed':len(checks)==14 and all(checks.values()),'liveAccount':'not tested','GalaxyS25Ultra':'not tested','settingsPersistence':'fixture callbacks only; actual persistence not exercised'}
        (EVIDENCE/'api36-ui-0.20.0.json').write_text(json.dumps(result,indent=2)+'\n')
    assert result['allPassed'],result
    print(json.dumps(checks))
if __name__=='__main__': main()
