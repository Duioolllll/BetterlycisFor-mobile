#!/usr/bin/env python3
"""Build with a local Android SDK + JDK 17, without downloading Gradle dependencies.

Set ANDROID_SDK_ROOT or --sdk. The tool never downloads anything automatically.
The output is signed with a generated local development key. Back up .signing/
privately for install-compatible later builds. Never commit the private signing key.
"""
import argparse, hashlib, json, os, shutil, subprocess, zipfile, xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
arg = argparse.ArgumentParser()
arg.add_argument('--sdk', default=os.environ.get('ANDROID_SDK_ROOT', os.environ.get('ANDROID_HOME', '')))
opt = arg.parse_args()
sdk = Path(opt.sdk).expanduser().resolve()
platforms = sorted(sdk.rglob('android.jar'))
if not platforms:
    raise SystemExit('Android API 36 platform is required. Set ANDROID_SDK_ROOT or pass --sdk.')
android = next((p for p in platforms if '36' in str(p.parent)), platforms[-1])
aapt_candidates = list(sdk.rglob('aapt2'))
if not aapt_candidates:
    raise SystemExit('Android SDK Build Tools 36 are required.')
bt = aapt_candidates[-1].parent
java = shutil.which('java')
if not java:
    raise SystemExit('JDK 17+ is required.')
def run(args):
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True)
def javatool(*args):
    run([java, *args])

work = ROOT / 'build/manual'
if work.exists(): shutil.rmtree(work)
classes, dex = work/'classes', work/'dex'
classes.mkdir(parents=True); dex.mkdir()
sources = sorted((ROOT/'app/src/main/java').rglob('*.java'))
javatool('-m', 'jdk.compiler/com.sun.tools.javac.Main', '-encoding', 'UTF-8',
         '-source', '8', '-target', '8', '-Xlint:deprecation', '-classpath', android,
         '-d', classes, *sources)
jar = work/'classes.jar'
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in classes.rglob('*.class'): z.write(p, p.relative_to(classes))
javatool('-cp', bt/'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '26',
         '--lib', android, '--output', dex, jar)
run([bt/'aapt2','compile','--dir',ROOT/'app/src/main/res','-o',work/'resources.zip'])
unsigned=work/'unsigned.apk'
manifest=work/'AndroidManifest.xml'
manifest.write_text((ROOT/'app/src/main/AndroidManifest.xml').read_text().replace('<manifest ', '<manifest package="io.betterlyrics.mobile" ', 1))
attrs=ET.fromstring(manifest.read_text()).attrib
version=attrs['{http://schemas.android.com/apk/res/android}versionName']
version_code=attrs['{http://schemas.android.com/apk/res/android}versionCode']
run([bt/'aapt2','link','-I',android,'--manifest',manifest,
     '--min-sdk-version','26','--target-sdk-version','36','--version-code',version_code,
     '--version-name',version,'-o',unsigned,work/'resources.zip'])
with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:
    for p in dex.glob('*.dex'):z.write(p,p.name)
    for p in (ROOT/'app/src/main/assets').rglob('*'):
        if p.is_file():z.write(p,'assets/'+str(p.relative_to(ROOT/'app/src/main/assets')))
aligned=work/'aligned.apk'
run([bt/'zipalign','-f','-p','4',unsigned,aligned])
signing=ROOT/'.signing';signing.mkdir(exist_ok=True)
keystore=signing/'development.p12'
if not keystore.exists():
    run([shutil.which('keytool') or 'keytool','-genkeypair','-keystore',keystore,'-storepass','android',
         '-keypass','android','-alias','mobile','-keyalg','RSA','-keysize','3072','-validity','10000',
         '-dname','CN=BetterLyrics Mobile Development,O=Unofficial Android Port,C=KR','-storetype','PKCS12'])
dist=ROOT/'dist';dist.mkdir(exist_ok=True)
apk=dist/('BetterLyrics-Mobile-'+version+'.apk')
javatool('-jar',bt/'lib/apksigner.jar','sign','--ks',keystore,'--ks-key-alias','mobile',
         '--ks-pass','pass:android','--key-pass','pass:android','--out',apk,aligned)
javatool('-jar',bt/'lib/apksigner.jar','verify','--verbose','--print-certs',apk)
run([bt/'aapt2','dump','badging',apk])
result={'apk':apk.name,'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
        'minSdk':26,'targetSdk':36,'signing':'local-development-key','abi':'universal (no native .so)'}
(dist/'build-info.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
