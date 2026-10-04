#!/usr/bin/env python3
"""Verify a numeric alert expires after real process death without reopening Glance."""
import argparse,datetime,json,os,re,subprocess,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('serial',nargs='?',default='emulator-5556')
args=parser.parse_args()
adb=[str(Path(os.environ.get('ANDROID_HOME',Path.home()/'Android/Sdk'))/'platform-tools/adb'),'-s',args.serial]
def run(*commands,check=True):
 return subprocess.run(adb+list(commands),check=check,capture_output=True,text=True,timeout=30).stdout
assert args.serial.startswith('emulator-') and run('shell','getprop','ro.kernel.qemu').strip()=='1'
run('shell','run-as','com.khaleel.glance','pwd')
head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
dirty=bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip())
def fixture(mode):
 run('shell','am','start','-n','com.khaleel.glance/.debug.DebugNotificationActivity','--es','mode',mode)
def posted():return '|com.khaleel.glance|5202|' in run('shell','cmd','notification','list')
try:
 run('shell','input','keyevent','KEYCODE_HOME')
 run('shell','run-as','com.khaleel.glance','rm','-f','files/alert-expiry-fixture.json')
 # Initialize only the dedicated fixture task; do not resume MainActivity's Stop recovery.
 # Force-stop here is setup only. The tested death below is a plain kill -9.
 run('shell','am','start','-S','-f','0x10008000','-n','com.khaleel.glance/.debug.DebugNotificationActivity','--es','mode','alert-expiry-demo')
 receiptDeadline=time.monotonic()+3
 while True:
  raw=run('shell','run-as','com.khaleel.glance','cat','files/alert-expiry-fixture.json',check=False)
  if raw.strip():
   sample=json.loads(raw);break
  assert time.monotonic()<receiptDeadline,'Expiry fixture did not report successful posting'
  time.sleep(.02)
 assert sample['synthetic']
 deadline=datetime.datetime.fromisoformat(sample['freshnessDeadline'].replace('Z','+00:00'))
 initialDeadline=time.monotonic()+3
 while not posted():
  assert time.monotonic()<initialDeadline,'No initial synthetic alert'
  time.sleep(.02)
 run('shell','input','keyevent','KEYCODE_HOME')
 pid=run('shell','pidof','com.khaleel.glance').strip()
 assert re.fullmatch(r'[0-9]+',pid),pid
 run('shell','run-as','com.khaleel.glance','kill','-9',pid)
 killDeadline=time.monotonic()+2
 while run('shell','pidof','com.khaleel.glance',check=False).strip():
  assert time.monotonic()<killDeadline,'App did not die'
  time.sleep(.02)
 assert posted(),'Alert did not survive normal process death for the timeout test'
 # This bounded timing assertion observes the notification system; it never starts the app.
 until=time.monotonic()+18
 while posted():
  assert time.monotonic()<until,'System-owned alert timeout did not expire'
  time.sleep(.1)
 expiredAt=datetime.datetime.now(datetime.timezone.utc)
 assert expiredAt <= deadline+datetime.timedelta(seconds=2),'Expiry observed too late'
 checks={'numericAlertRetainedImmediatelyAfterProcessDeath':True,
         'systemRemovesAlertAcrossActualFiveMinuteSourceBoundaryWithoutManualReopen':True,
         'expiredNumericAlertDoesNotRemainInSystemNotificationList':True}
 apk=run('shell','pm','path','com.khaleel.glance').strip().removeprefix('package:')
 assert re.fullmatch(r'/data/app/[A-Za-z0-9/_.=+~\-]+/base\.apk',apk)
 result={'synthetic':True,'device':'API 36 emulator','checks':checks,'allPassed':True,
  'sourceCommitAtRun':head,'sourceDirtyAtRun':dirty,'fixtureApkSha256':run('shell','sha256sum',apk).split()[0],
  'sourceTime':sample['sourceTime'],'freshnessDeadline':sample['freshnessDeadline'],'expiryObservedAt':expiredAt.isoformat(),
  'scope':'Actual kill -9 of the background app; ordinary alarm retained, then system Notification timeout removes it near the real five-minute measurement boundary. No manual reopen/render. System delete callback may start a new process; its empty in-memory ledger cannot remove the alarm by itself.',
  'SamsungOneUI':'not tested','liveAccount':'not tested','naturalDoze':'not tested','audibleAlerts':'not tested'}
 (ROOT/'docs/evidence/api36-alert-expiry-0.27.0.json').write_text(json.dumps(result,indent=2)+'\n')
 print(json.dumps(result,indent=2))
finally:fixture('demo-clear')
