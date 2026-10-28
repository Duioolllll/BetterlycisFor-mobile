#!/usr/bin/env python3
"""Exercise actual document grants, local playback and OAuth primitives on a disposable emulator."""
import argparse, json, re, subprocess
from pathlib import Path
from widget_harness import build_host

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--serial',required=True);parser.add_argument('--sdk',required=True);parser.add_argument('--apk',required=True);parser.add_argument('--report',required=True)
args=parser.parse_args()
if not re.fullmatch(r'emulator-\d+',args.serial):raise SystemExit('Use a dedicated disposable emulator, never a physical device.')
target='io.betterlyrics.mobile';host='io.betterlyrics.widgettest';installed=[]
report={'passed':False,'serial':args.serial,'api':36,'physicalDeviceTested':False,'spotifyAccountTested':False,'checks':[]}
def run(*cmd,required=True,timeout=150):
    result=subprocess.run(list(map(str,cmd)),capture_output=True,text=True,timeout=timeout)
    if required and result.returncode:raise RuntimeError('Command failed ('+str(result.returncode)+'): '+(result.stderr or result.stdout)[-1500:])
    return result.stdout
def adb(*cmd,**kwargs):return run('adb','-s',args.serial,*cmd,**kwargs)
try:
    assert adb('shell','getprop','ro.kernel.qemu').strip()=='1' and adb('shell','getprop','ro.build.version.sdk').strip()=='36','A dedicated API36 emulator is required'
    for package in (target,host):assert not adb('shell','pm','path',package,required=False).strip().startswith('package:'),'Existing application data must not be overwritten'
    harness=build_host(root,Path(args.sdk).resolve())
    for package,apk in ((target,Path(args.apk).resolve()),(host,harness)):
        adb('install','--no-streaming',apk);installed.append(package)
    adb('shell','cmd','notification','disallow_listener',target+'/'+target+'.LyricsListener',required=False)
    output=adb('shell','am','instrument','-w',host+'/.PlaylistInstrumentation',timeout=180)
    for name in ('local','spotifyStorage','spotifyLoopback'):
        assert 'INSTRUMENTATION_RESULT: '+name+'=ok' in output,output
    assert 'INSTRUMENTATION_CODE: -1' in output,output
    report['checks']=[
        'persisted document-tree grants, M3U import, sidecar lyrics, append and removal',
        'local foreground audio playback, position, pause, seek, next, stop and media notification',
        'Spotify tokens encrypted by Android Keystore and reloaded',
        'Spotify loopback callback rejects wrong state and accepts the expected state',
    ]
    report['passed']=True
except Exception as error:
    report['error']=str(error)
    try:(Path(args.report).parent/'PLAYLIST_LOGCAT.txt').write_text(adb('logcat','-d','-t','800','PlaylistInstrumentation:E','AndroidRuntime:E','*:S'))
    except Exception:pass
    raise
finally:
    for package in reversed(installed):
        try:adb('uninstall',package)
        except Exception:pass
    output=Path(args.report);output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
    print(json.dumps(report,indent=2,ensure_ascii=False))
