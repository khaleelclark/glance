#!/usr/bin/env python3
"""Run synthetic framework alarm-icon checks on a dedicated debug emulator."""
import argparse,hashlib,json,os,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('serial',nargs='?',default='emulator-5556')
parser.add_argument('--suffix',default='')
args=parser.parse_args()
assert not args.suffix or re.fullmatch(r'[A-Za-z0-9-]+',args.suffix)
adb=[str(Path(os.environ.get('ANDROID_HOME',Path.home()/'Android/Sdk'))/'platform-tools/adb'),'-s',args.serial]
def run(*commands):return subprocess.check_output(adb+list(commands),text=True,timeout=60)
assert args.serial.startswith('emulator-') and run('shell','getprop','ro.kernel.qemu').strip()=='1'
run('shell','run-as','com.khaleel.glance','pwd')
head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
dirty=bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip())
run('shell','run-as','com.khaleel.glance','rm','-f','files/alert-icon-probe.json')
output=run('shell','am','instrument','-w','com.khaleel.glance/.debug.AlertIconInstrumentation')
assert 'INSTRUMENTATION_CODE: -1' in output,output
result=json.loads(run('shell','run-as','com.khaleel.glance','cat','files/alert-icon-probe.json'))
assert result['allPassed'] and len(result['checks'])==19 and all(result['checks'].values())
result.update(device='API 36 emulator',fixtureApkSha256=hashlib.sha256((ROOT/'app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest(),sourceCommitAtRun=head,sourceDirtyAtRun=dirty,liveAccount='not tested',SamsungOneUI='not tested',audibleAlerts='not tested')
(ROOT/('docs/evidence/api36-alert-icons-0.27.0'+('-'+args.suffix if args.suffix else '')+'.json')).write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result['checks']))
