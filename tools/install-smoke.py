#!/usr/bin/env python3
"""Install regression on a dedicated disposable Android emulator, never a phone."""
import argparse
import json
import os
import re
import subprocess
import time
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
p.add_argument('--report', required=True)
a = p.parse_args()
if not re.fullmatch(r'emulator-\d+', a.serial):
    raise SystemExit('Use a dedicated disposable emulator serial, not a physical device.')
root = Path(__file__).resolve().parents[1]
meta = json.loads((root/'downloads/release.json').read_text())
package = 'io.betterlyrics.mobile'
report = {'passed': False, 'serial': a.serial, 'version': meta['version'], 'sha256': meta['sha256'], 'checks': [], 'physicalDeviceTested': False}


def adb(*args, required=True):
    result = subprocess.run(['adb', '-s', a.serial, *map(str, args)], capture_output=True,
                            text=True, timeout=120)
    output = (result.stdout + result.stderr).strip()
    if required and result.returncode:
        raise RuntimeError('adb ' + str(args[0]) + ': ' + output)
    return result.returncode, output


def installed_version():
    _, output = adb('shell', 'dumpsys', 'package', package)
    assert 'versionName=' + meta['version'] in output, 'Installed version name differs'
    assert re.search(r'\bversionCode=' + str(meta['versionCode']) + r'\b', output), 'Installed version code differs'


def launch_and_check():
    _, launch = adb('shell', 'am', 'start', '-W', '-n', package+'/.MainActivity')
    assert 'Status: ok' in launch, launch
    time.sleep(3)
    _, pid = adb('shell', 'pidof', package)
    assert pid.strip(), 'App process exited after launch'


try:
    _, is_emulator = adb('shell', 'getprop', 'ro.kernel.qemu')
    assert is_emulator == '1', 'Refusing to change a non-emulator device'
    _, sdk = adb('shell', 'getprop', 'ro.build.version.sdk')
    report['api'] = int(sdk)
    assert report['api'] == 36, 'This release gate requires Android 16/API36'
    code, old_error = adb('install', '--no-streaming', root/'downloads/BetterLyrics-Mobile-0.4.0.apk', required=False)
    assert code != 0 and 'resources.arsc' in old_error, '0.4.0 resource-table install failure was not reproduced: ' + old_error
    report['oldApkError'] = old_error
    report['checks'].append('0.4.0 rejected by Android PackageManager for resources.arsc packaging')
    adb('install', '--no-streaming', root/'downloads'/meta['apk'])
    installed_version()
    report['checks'].append('fixed APK fresh install and installed version')
    adb('shell', 'logcat', '-c')
    launch_and_check()
    report['checks'].append('MainActivity launch and process still alive after 3 seconds')
    for previous in ['0.3.0', '0.4.1', '0.4.3', '0.4.4', '0.5.0', '0.6.0', '0.7.0']:
        adb('uninstall', package)
        adb('install', '--no-streaming', root/('downloads/BetterLyrics-Mobile-'+previous+'.apk'))
        adb('install', '--no-streaming', '-r', root/'downloads'/meta['apk'])
        installed_version()
        launch_and_check()
        report['checks'].append(previous+' to '+meta['version']+' in-place update and launch with original signing identity')
    report['passed'] = True
except Exception as error:
    report['error'] = str(error)
    raise
finally:
    Path(a.report).write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
