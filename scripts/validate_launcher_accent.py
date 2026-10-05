#!/usr/bin/env python3
"""Native launcher components and cold-start repair on a dedicated synthetic emulator."""
import hashlib,json,os,subprocess,sys
from pathlib import Path
from validate_refinement_028 import PngPixels,pixels
ROOT=Path(__file__).resolve().parents[1]
serial=sys.argv[1] if len(sys.argv)>1 else 'emulator-5556'
adb=[str(Path(os.environ.get('ANDROID_HOME',Path.home()/'Android/Sdk'))/'platform-tools/adb'),'-s',serial]
def run(*args):return subprocess.check_output(adb+list(args),text=True,timeout=60)
assert serial.startswith('emulator-') and run('shell','getprop','ro.kernel.qemu').strip()=='1'
head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
dirty=bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip())
run('shell','input','keyevent','KEYCODE_WAKEUP');run('shell','wm','dismiss-keyguard')
run('shell','run-as','com.khaleel.glance','rm','-f','files/launcher-accent-probe.json')
output=run('shell','am','instrument','-w','-e','keepAccent','Teal','com.khaleel.glance/.debug.LauncherAccentInstrumentation')
assert 'INSTRUMENTATION_CODE: -1' in output,output
report=json.loads(run('shell','run-as','com.khaleel.glance','cat','files/launcher-accent-probe.json'))
assert report['allPassed'] and len(report['checks'])==13 and all(report['checks'].values())
def query():return run('shell','cmd','package','query-activities','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-p','com.khaleel.glance')
run('shell','am','force-stop','com.khaleel.glance')
run('shell','run-as','com.khaleel.glance','pm','enable','com.khaleel.glance/.LauncherLilac')
run('shell','run-as','com.khaleel.glance','pm','disable','com.khaleel.glance/.LauncherTeal')
assert 'LauncherLilac' in query() and 'LauncherTeal' not in query()
run('shell','am','start','-W','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-n','com.khaleel.glance/.LauncherLilac')
assert 'LauncherTeal' in query() and 'LauncherLilac' not in query()
report['checks']['coldStartupRepairsAliasFromPersistedTeal']=True
run('shell','am','force-stop','com.khaleel.glance')
launcher=subprocess.Popen(adb+['shell','am','start','-W','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-n','com.khaleel.glance/.LauncherTeal'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
frames=[]
for index in range(8):
    frames.append(subprocess.check_output(adb+['exec-out','screencap','-p'],timeout=30))
stdout,stderr=launcher.communicate(timeout=60)
assert launcher.returncode==0,(stdout,stderr)
assert 'LauncherTeal' in query()
report['checks']['selectedAliasColdLaunchesExistingMainActivity']=True
splash=False
for data in frames:
    im=PngPixels(data);center=(im.width//4,im.height//3,3*im.width//4,2*im.height//3)
    if pixels(im,center,(121,211,197))>500 and pixels(im,center,(185,174,245))==0:
        (ROOT/'docs/evidence/api36-launcher-0.28.0-teal-splash.png').write_bytes(data);splash=True;break
report['selectedAliasSplashPixelsObserved']=splash
apk=run('shell','pm','path','com.khaleel.glance').strip().removeprefix('package:')
report.update(device='API 36 emulator',sourceCommitAtRun=head,sourceDirtyAtRun=dirty,fixtureApkSha256=run('shell','sha256sum',apk).split()[0],
    scope='Actual PackageManager aliases/icons/process and persisted startup repair; legacy helper on API36, no API30 runtime claim',SamsungOneUI='not tested',themedMonochromeIcon='system controls tint')
(ROOT/'docs/evidence/api36-launcher-0.28.0.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
