import json, subprocess, pathlib, time, sys
# Dedicated synthetic-data emulator only; never run against a physical watch.
SDK=pathlib.Path.home()/'Android/Sdk'
SERIAL=sys.argv[1] if len(sys.argv)>1 else 'emulator-5560'
assert SERIAL.startswith('emulator-')
ADB=[str(SDK/'platform-tools/adb'),'-s',SERIAL]
OUT=pathlib.Path(__file__).resolve().parents[1]/'docs/evidence/watch-0.19'; OUT.mkdir(exist_ok=True)
def adb(*args):
    return subprocess.run(ADB+list(args),check=True,capture_output=True,text=True).stdout
assert adb('shell','getprop','ro.kernel.qemu').strip()=='1'
assert 'DEBUGGABLE' in adb('shell','dumpsys','package','com.khaleel.glance')
revision=int(time.time()*1000)
rows={}
def fixture(label,status=None,**extras):
    args=['shell','am','start','-S','-W','-n','com.khaleel.glance/com.khaleel.glance.wear.debug.DemoFixtureActivity']
    if status: args+=['--es','demo_status',status]
    for key,value in extras.items():
        kind='--ez' if isinstance(value,bool) else '--el' if key=='demo_revision' else '--ei'
        args += [kind,key,str(value).lower() if isinstance(value,bool) else str(value)]
    adb(*args)
    p=json.loads(adb('shell','run-as','com.khaleel.glance','cat','files/watch-probe.json'))
    dump=adb('shell','dumpsys','notification','--noredact')
    active=dump.split('Enqueued Notification List:')[0].split('Snoozed notifications:')[0]
    p['notificationCountAtActivityStart']=p['notificationCount']
    p['notificationCount']=sum('NotificationRecord(' in line and 'pkg=com.khaleel.glance ' in line and 'id=2101 ' in line for line in active.splitlines())
    rows[label]=p
    print(label,json.dumps(p),flush=True)
    return p
adb('shell','pm','grant','com.khaleel.glance','android.permission.POST_NOTIFICATIONS')
p=fixture('fresh','READING',demo_opt_in=True,demo_revision=revision)
assert p['fresh'] and p['hasValue'] and p['hasArrow'] and p['notificationCount']==1 and p['safeDefault']
p=fixture('stable_card_update','READING',demo_revision=revision+1)
assert p['notificationCount']==1
p=fixture('delayed_historical','READING',demo_age_minutes=7,demo_revision=revision+2)
assert p['status']=='Delayed' and p['hasValue'] and not p['fresh'] and not p['hasArrow']
p=fixture('expired_transport','READING',demo_transport_minutes=5,demo_revision=revision+3)
assert not p['hasValue'] and p['notificationCount']==0 and p['timelineFrames']==0
p=fixture('future_measurement','READING',demo_age_minutes=-3,demo_revision=revision+4)
assert not p['hasValue'] and not p['fresh']
p=fixture('phone_notification_off','READING',demo_phone_notifications=False,demo_revision=revision+5)
assert p['fresh'] and p['notificationCount']==0
p=fixture('watch_opt_out','READING',demo_opt_in=False,demo_revision=revision+6)
assert p['notificationCount']==0
adb('shell','pm','revoke','com.khaleel.glance','android.permission.POST_NOTIFICATIONS')
p=fixture('permission_denied','READING',demo_opt_in=True,demo_revision=revision+7)
assert p['fresh'] and p['notificationCount']==0
adb('shell','pm','grant','com.khaleel.glance','android.permission.POST_NOTIFICATIONS')
p=fixture('hidden_tombstone','HIDDEN',demo_revision=revision+8)
assert p['revoked'] and not p['hasValue'] and p['notificationCount']==0
p=fixture('late_reading_rejected','READING',demo_revision=revision+7)
assert p['revoked'] and not p['hasValue'] and p['notificationCount']==0
p=fixture('process_recreation_keeps_revocation')
assert p['revoked'] and not p['hasValue']
p=fixture('explicit_newer_grant','READING',demo_revision=revision+9)
assert p['fresh'] and p['notificationCount']==1
p=fixture('stopped','STOPPED',demo_revision=revision+10)
assert p['revoked'] and not p['hasValue'] and p['notificationCount']==0
(OUT/'native-watch-scenarios.json').write_text(json.dumps({'device':'Wear OS 6 API36 x86_64 emulator','synthetic':True,'checks':rows,'allPassed':True,'pairedDataLayer':'not tested','GalaxyWatch7':'not tested'},indent=2)+'\n')
print('ALL WATCH SCENARIOS PASSED',flush=True)
