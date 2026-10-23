#!/usr/bin/env python3
"""Validate the manifest-selected release APK and run offline service regressions."""
import argparse, hashlib, json, os, re, subprocess, sys
from pathlib import Path

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--sdk',required=True)
parser.add_argument('--report',required=True)
args=parser.parse_args()
sdk=Path(args.sdk).resolve()
meta=json.loads((root/'downloads/release.json').read_text())
assert re.fullmatch(r'\d+\.\d+\.\d+',meta['version'])
assert meta['apk']=='BetterLyrics-Mobile-'+meta['version']+'.apk'
apk=root/'downloads'/meta['apk']
tools=None
for candidate in sdk.rglob('source.properties'):
    if 'Pkg.Revision=36.0.0' in candidate.read_text(errors='replace').replace(' ',''):
        tools=candidate.parent;break
assert tools is not None,'Build Tools 36.0.0 required'
exe='.exe' if os.name=='nt' else ''
report={'passed':False,'version':meta['version'],'versionCode':meta['versionCode'],
        'apk':meta['apk'],'sha256':meta['sha256'],'physicalDeviceTested':False,'checks':[]}

def run(command):
    result=subprocess.run(list(map(str,command)),cwd=root,text=True,capture_output=True,timeout=180)
    if result.returncode:
        raise RuntimeError(result.stdout+'\n'+result.stderr)
    return result.stdout

try:
    stdout=run([sys.executable,'tools/test.py','--sdk',sdk]);print(stdout)
    count=sum(map(int,re.findall(r': (\d+) (?:checks|assertions) passed',stdout)))
    assert count>0,'No JVM checks recorded'
    report['jvmChecks']=count;report['checks'].append('offline service, parser, policy, appearance and signal regressions')
    parity=root/'build/service-output-parity.json'
    run([sys.executable,'tools/benchmark-service.py','--baseline','8a12f2a31f4c1d32870bc7b3f281092e560b8379','--check-only','--report',parity])
    report['audioOutputParity']=json.loads(parity.read_text())
    assert report['audioOutputParity']['retainedFrameUnchanged']
    assert report['audioOutputParity']['audioFramesBitIdentical']==2073
    report['checks'].append('2073 audio frames bit-identical to retained baseline')
    print(run([sys.executable,'tools/verify-release.py']))
    assert apk.stat().st_size==meta['bytes'] and hashlib.sha256(apk.read_bytes()).hexdigest()==meta['sha256']
    report['checks'].append('release manifest, checksum, packaged web assets and private-file exclusions')
    packaging=json.loads(run([sys.executable,'tools/apk-packaging-test.py','--fixed-apk',apk]))
    assert packaging['passed'];report['apkPackaging']=packaging
    signature=run(['java','-jar',tools/'lib/apksigner.jar','verify','--verbose','--print-certs',apk])
    assert meta['certificateSha256'] in signature,'Unexpected release signing certificate'
    for scheme in [2,3]:assert re.search(r'Verified using v'+str(scheme)+r' scheme[^\n]*: true',signature)
    previous=run(['java','-jar',tools/'lib/apksigner.jar','verify','--print-certs',root/'downloads/BetterLyrics-Mobile-0.4.1.apk'])
    assert meta['certificateSha256'] in previous,'Latest public APK uses a different certificate'
    run([tools/('zipalign'+exe),'-c','4',apk])
    report['certificateSha256']=meta['certificateSha256'];report['checks'].append('v2/v3 signatures, previous release certificate and ZIP alignment')
    badging=run([tools/('aapt2'+exe),'dump','badging',apk])
    for value in ["name='io.betterlyrics.mobile'","versionName='"+meta['version']+"'","versionCode='"+str(meta['versionCode'])+"'"]:assert value in badging,value
    assert "minSdkVersion:'26'" in badging.splitlines() and "targetSdkVersion:'36'" in badging.splitlines()
    assert 'application-debuggable' not in badging
    report['checks'].append('release package/version, minSdk26, targetSdk36 and debuggable=false')
    print(run([sys.executable,'tools/audit-public.py']))
    report['checks'].append('heuristic public-file credential/private-file scan')
    report['sourceCommit']=run(['git','rev-parse','HEAD']).strip()
    report['workingTreeDirty']=bool(run(['git','status','--porcelain']).strip())
    report['passed']=True
except Exception as error:
    report['error']=str(error)
    raise
finally:
    output=Path(args.report);output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))
