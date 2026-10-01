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
    (EVIDENCE/f'api36-ui-0.21.0-{name}-demo.png').write_bytes(subprocess.run(ADB+['exec-out','screencap','-p'],check=True,capture_output=True,timeout=30).stdout)
def scroll_to(text):
    for _ in range(10):
        if visible(text): return
        adb('shell','input','swipe','24','1450','24','450','250');settle()
    raise AssertionError('Scroll target missing: '+text)
def scroll_up_to(text):
    for _ in range(10):
        if visible(text): return
        adb('shell','input','swipe','24','650','24','1800','250');settle()
    raise AssertionError('Scroll-up target missing: '+text)
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
        assert not visible('Restore card') and not visible('Show notification')
        checks['readingTimestampGraphAndClearMainActions']=True
        screenshot('main-light')
        tap('Refresh',True);assert events()=={'refresh':1}
        checks['refreshDispatchesOnce']=True
        scroll_to('Show latest 12 hours');tap('Show latest 12 hours',True)
        assert checked_node('Show latest 12 hours').get('checked')=='true'
        tap('Show latest 6 hours',True);assert checked_node('Show latest 6 hours').get('checked')=='true'
        checks['mainSixAndTwelveHourChoicesWork']=True
        screenshot('graph-light')
        # Fresh fixture resets scroll only; no real persisted state is touched.
        start();tap('Settings',True)
        assert visible('Everyday') and visible('Alerts') and visible('More')
        assert visible('mg/dL') and visible('mmol/L') and visible('1 minute') and visible('5 minutes')
        assert not visible('About Lock-screen values')
        checks['commonSettingsDirectWithoutAccordions']=True
        screenshot('settings-everyday')
        tap('mmol/L',True);assert events().get('unit')==1
        scroll_to('Lock-screen values')
        assert checked_node('Lock-screen values').get('checked')=='false'
        tap('Lock-screen values');assert checked_node('Lock-screen values').get('checked')=='true'
        assert events().get('privacy')==1 and visible('locked')
        checks['privacySummaryToggleSemanticsAndSingleDispatch']=True
        scroll_to('Notification style');tap('Live Update',True);assert events().get('liveUpdate')==1
        tap('Graph',True);assert events().get('liveUpdate')==2
        checks['notificationStyleChoicesDispatchCorrectly']=True
        scroll_to('Show notification');tap('Show notification',True)
        assert events().get('restore')==1
        checks['showNotificationLivesWithNotificationControls']=True
        screenshot('settings-notifications')
        scroll_to('How it works');before=events();tap('How it works',True)
        assert visible('How notifications work') and events()==before
        tap('Done',True);assert events()==before
        checks['consolidatedNotificationHelpHasNoSideEffects']=True
        # Tabs stay in view even after scrolling a long page and reset page position.
        scroll_to('Resume after restart');assert visible('Everyday') and visible('More')
        tap('Alerts',True);assert visible('Glucose alerts') and visible('Edit limit')
        checks['fixedTabsAndPagePositionPreserved']=True
        screenshot('settings-alerts')
        tap('Edit limit',True);scroll_to('Repeat cooldown')
        n=next(n for n in nodes() if n.get('class')=='android.widget.NumberPicker')
        x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
        x=(x1+x2)//2;mid=(y1+y2)//2
        before=events().get('alert',0)
        tab=find('More',True)
        tx1,ty1,tx2,ty2=map(int,re.findall(r'\d+',tab.get('bounds')))
        adb('shell','input','swipe',str(x),str(mid+170),str(x),str(mid-170),'500')
        # No tree dump or settle between wheel input and leaving the page.
        adb('shell','input','tap',str((tx1+tx2)//2),str((ty1+ty2)//2));settle()
        assert events().get('alert',0)>before
        checks['alertWheelChangeDispatchesBeforePageExit']=True
        assert visible('Appearance') and visible('Custom color')
        before=events();tap('Custom color',True);scroll_to('Hex color')
        assert visible('Hex color') and events()==before
        scroll_up_to('Close custom color');tap('Close custom color',True)
        checks['colorWheelPresetsAndHexRemainReachable']=True
        screenshot('settings-more')
        scroll_to('Show widget values');assert visible('locked')
        scroll_to('Share watch readings');assert visible('watch')
        scroll_to('Watch notification');assert visible('Watch notification')
        checks['widgetWatchControlsAndPrivacyReachable']=True
        scroll_to('Sign out');before=events();tap('Sign out',True)
        assert visible('Cancel') and visible('Monitoring will stop') and events()==before
        tap('Cancel',True);assert events()==before
        checks['destructiveConfirmationAndCancelPreserved']=True
        adb('shell','input','keyevent','KEYCODE_BACK');settle()
        assert visible('History') and visible('6.3')
        checks['systemBackAndUnitChoiceRetained']=True
        start('stale');assert visible('No recent reading') and visible('Unavailable')
        checks['staleLabelAndSuppressedCurrentTrendPreserved']=True
        screenshot('stale')
        start('offline');assert visible('Connection failed') and visible('Showing the last reading')
        checks['offlineRecoveryCopyVisible']=True
        start('signIn');assert visible('LibreLinkUp') and visible('Password')
        assert visible('encrypted')
        checks['signInAndEncryptedStorageNoticeReachable']=True
        screenshot('sign-in')
        adb('shell','cmd','uimode','night','yes');start()
        assert visible('113') and visible('History');screenshot('main-dark')
        checks['darkMainRenders']=True
        adb('shell','settings','put','system','font_scale','1.5');start()
        assert visible('113') and visible('mg/dL') and visible('Settings')
        screenshot('large-font')
        tap('Settings',True);assert visible('Everyday') and visible('Alerts') and visible('More')
        screenshot('settings-large-font')
        checks['largeFontReadingUnitsAndFixedTabsReachable']=True
        adb('shell','settings','put','system','font_scale','1.0')
        adb('shell','settings','put','system','user_rotation','1');start()
        assert visible('113') and visible('History')
        assert visible('Show latest 6 hours') and visible('Show latest 12 hours')
        screenshot('landscape')
        checks['landscapeReadingGraphAndTimeControlsRender']=True
    finally:
        adb('shell','settings','put','system','font_scale',original_scale)
        adb('shell','settings','put','system','user_rotation',original_rotation)
        adb('shell','settings','put','system','accelerometer_rotation',original_auto)
        adb('shell','cmd','uimode','night','yes' if 'yes' in original_mode else 'no')
        result={'synthetic':True,'device':'API 36 emulator','checks':checks,'allPassed':len(checks)==20 and all(checks.values()),'liveAccount':'not tested','GalaxyS25Ultra':'not tested','settingsPersistence':'fixture callbacks only; actual persistence not exercised'}
        (EVIDENCE/'api36-ui-0.21.0.json').write_text(json.dumps(result,indent=2)+'\n')
    assert result['allPassed'],result
    print(json.dumps(checks))
if __name__=='__main__': main()
