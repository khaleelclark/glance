#!/usr/bin/env python3
"""Reproduce disabled-default-alias launch and verify a data-preserving explicit launch.

Dedicated synthetic emulator only. This checks Studio's equivalent Android intent,
not an interactive Android Studio session or the owner's phone.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
serial = sys.argv[1] if len(sys.argv) > 1 else 'emulator-5556'
adb = [str(Path(os.environ.get('ANDROID_HOME', Path.home() / 'Android/Sdk')) / 'platform-tools/adb'), '-s', serial]

def run(*args):
    return subprocess.check_output(adb + list(args), text=True, stderr=subprocess.STDOUT, timeout=60)

assert serial.startswith('emulator-') and run('shell', 'getprop', 'ro.kernel.qemu').strip() == '1'
config = ET.parse(ROOT / '.run/Glance_phone.run.xml').getroot().find('configuration')
options = {o.get('name'): o.get('value') for o in config.findall('option')}
assert options['MODE'] == 'specific_activity'
activity = options['ACTIVITY_CLASS']
assert activity == 'com.khaleel.glance.MainActivity'
apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
assert 'Success' in run('install', '-r', str(apk))
probe = run('shell', 'am', 'instrument', '-w', '-e', 'keepAccent', 'Teal', 'com.khaleel.glance/.debug.LauncherAccentInstrumentation')
assert 'INSTRUMENTATION_CODE: -1' in probe, probe

def launchers():
    return run('shell', 'cmd', 'package', 'query-activities', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER', '-p', 'com.khaleel.glance')

def settings():
    return run('shell', 'run-as', 'com.khaleel.glance', 'cat', 'shared_prefs/settings.xml')

query = launchers()
assert 'LauncherTeal' in query and 'LauncherLilac' not in query, query
before = settings()
assert ET.fromstring(before).find("string[@name='accent_hex']").text == '#79D3C5'
marker = 'files/studio-upgrade-synthetic-marker.txt'
run('shell', 'run-as', 'com.khaleel.glance', 'touch', marker)
try:
    run('shell', 'am', 'force-stop', 'com.khaleel.glance')
    failed = run('shell', 'am', 'start', '-W', '-n', 'com.khaleel.glance/.LauncherLilac')
    assert 'Error type 3' in failed and 'does not exist' in failed, failed
    assert 'Success' in run('install', '-r', str(apk))
    assert settings() == before
    run('shell', 'run-as', 'com.khaleel.glance', 'test', '-f', marker)
    query = launchers()
    assert 'LauncherTeal' in query and 'LauncherLilac' not in query, query
    started = run('shell', 'am', 'start', '-W', '-n', 'com.khaleel.glance/' + activity)
    assert 'Status: ok' in started and 'Error' not in started, started
    assert run('shell', 'pidof', 'com.khaleel.glance').strip()
    query = launchers()
    assert 'LauncherTeal' in query and 'LauncherLilac' not in query, query
    assert ET.fromstring(settings()).find("string[@name='accent_hex']").text == '#79D3C5'
    installed = run('shell', 'pm', 'path', 'com.khaleel.glance').strip().removeprefix('package:')
    assert run('shell', 'sha256sum', installed).split()[0] == digest
    report = dict(synthetic=True, api=int(run('shell', 'getprop', 'ro.build.version.sdk').strip()),
        sourceCommitAtRun=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        sourceDirtyAtRun=bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip()),
        fixtureApkSha256=digest, checks=dict(disabledLilacReproducesOwnerError=True,
        replaceInstallRetainsSettingsAndPrivateMarker=True, explicitConfiguredMainActivityStarts=True,
        persistedTealRemainsSoleLauncherAfterColdLaunch=True),
        scope='Android intent equivalent to Specified Activity; no Android Studio UI or physical Samsung qualification')
    (ROOT / 'docs/evidence/api36-studio-accent-launch.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
finally:
    run('shell', 'run-as', 'com.khaleel.glance', 'rm', '-f', marker)
