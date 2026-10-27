#!/usr/bin/env python3
"""Offline Android build. Release signing requires an existing private keystore."""
import argparse, hashlib, json, os, shutil, subprocess, zipfile, xml.etree.ElementTree as ET
from pathlib import Path
from apk_packaging import check_resource_table
ROOT = Path(__file__).resolve().parents[1]
arg = argparse.ArgumentParser()
arg.add_argument('--sdk', default=os.environ.get('ANDROID_SDK_ROOT', os.environ.get('ANDROID_HOME', '')))
arg.add_argument('--variant', choices=['debug', 'release'], default='debug')
arg.add_argument('--keystore', default=os.environ.get('BL_KEYSTORE', ''))
arg.add_argument('--alias', default=os.environ.get('BL_KEY_ALIAS', 'mobile'))
opt = arg.parse_args()
if not opt.sdk: raise SystemExit('Set ANDROID_SDK_ROOT or --sdk (Android platform 36, build-tools 36.0.0).')
sdk = Path(opt.sdk).expanduser().resolve()
platforms = sorted(p for p in sdk.rglob('android.jar') if p.parent.name == 'android-36')
if not platforms: raise SystemExit('Android platform 36 is required; another API is not substituted.')
android = platforms[0]
exe = '.exe' if os.name == 'nt' else ''
aapt_candidates = sorted(sdk.rglob('aapt2'+exe))
bt = None
for candidate in aapt_candidates:
    props = candidate.parent/'source.properties'
    if props.exists() and 'Pkg.Revision=36.0.0' in props.read_text().replace(' ',''):
        bt = candidate.parent; break
if bt is None: raise SystemExit('Install Android SDK Build Tools 36.0.0.')
java = shutil.which('java')
if not java: raise SystemExit('JDK 17 is required on PATH.')
def run(args): subprocess.run([str(a) for a in args], cwd=ROOT, check=True)
def javatool(*args): run([java, *args])
# Validate release inputs before compiling; do not silently create a replacement identity.
if opt.variant == 'release':
    if not opt.keystore: raise SystemExit('Release requires --keystore or BL_KEYSTORE.')
    keystore = Path(opt.keystore).expanduser().resolve()
    if not keystore.is_file(): raise SystemExit('Release keystore does not exist.')
    if not os.environ.get('BL_STORE_PASSWORD'): raise SystemExit('Set BL_STORE_PASSWORD privately.')
    os.environ.setdefault('BL_KEY_PASSWORD', os.environ['BL_STORE_PASSWORD'])
else:
    keystore=ROOT/'.signing/debug.p12'
    keystore.parent.mkdir(exist_ok=True)
    os.environ['BL_STORE_PASSWORD']=os.environ['BL_KEY_PASSWORD']='android'
    opt.alias='androiddebugkey'
    if not keystore.exists():
        run([shutil.which('keytool') or 'keytool','-genkeypair','-keystore',keystore,
             '-storepass:env','BL_STORE_PASSWORD','-keypass:env','BL_KEY_PASSWORD',
             '-alias',opt.alias,'-keyalg','RSA','-keysize','3072','-validity','10000',
             '-dname','CN=Android Debug,O=Android,C=US','-storetype','PKCS12'])
work=ROOT/'build'/('manual-'+opt.variant)
if work.exists(): shutil.rmtree(work)
classes,dex=work/'classes',work/'dex';classes.mkdir(parents=True);dex.mkdir()
# Link resources first so native Java can compile against the generated R IDs.
run([bt/('aapt2'+exe),'compile','--dir',ROOT/'app/src/main/res','-o',work/'resources.zip'])
manifest=work/'AndroidManifest.xml'
source=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
source=source.replace('<manifest ','<manifest package="io.betterlyrics.mobile" ',1)
source=source.replace('<application ', '<application android:debuggable="'+str(opt.variant=='debug').lower()+'" ',1)
manifest.write_text(source,encoding='utf-8')
attrs=ET.fromstring(source).attrib;ns='{http://schemas.android.com/apk/res/android}'
version,code=attrs[ns+'versionName'],attrs[ns+'versionCode']
linked=work/'linked.apk';unsigned=work/'unsigned.apk';generated=work/'generated';generated.mkdir()
run([bt/('aapt2'+exe),'link','-I',android,'--manifest',manifest,'--min-sdk-version','26',
     '--target-sdk-version','36','--version-code',code,'--version-name',version,'--java',generated,'-o',linked,work/'resources.zip'])
sources=sorted((ROOT/'app/src/main/java').rglob('*.java'))+sorted(generated.rglob('*.java'))
if not any(p.name=='R.java' for p in sources): raise SystemExit('AAPT2 did not generate resource IDs.')
javatool('-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-source','8','-target','8',
         '-Xlint:deprecation','-classpath',android,'-d',classes,*sources)
# Fixed entry dates and sorted paths eliminate wall-clock ZIP differences.
def write_entry(z,name,data,compression=zipfile.ZIP_DEFLATED):
    info=zipfile.ZipInfo(name,(2000,1,1,0,0,0));info.compress_type=compression
    info.external_attr=0o100644<<16;z.writestr(info,data)
jar=work/'classes.jar'
with zipfile.ZipFile(jar,'w') as z:
    for p in sorted(classes.rglob('*.class')): write_entry(z,p.relative_to(classes).as_posix(),p.read_bytes())
javatool('-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',android,'--output',dex,jar)
with zipfile.ZipFile(linked) as src, zipfile.ZipFile(unsigned,'w') as z:
    for name in sorted(src.namelist()):
        compression = zipfile.ZIP_STORED if name == 'resources.arsc' else src.getinfo(name).compress_type
        write_entry(z,name,src.read(name),compression)
    for p in sorted(dex.glob('*.dex')): write_entry(z,p.name,p.read_bytes())
    for p in sorted((ROOT/'app/src/main/assets').rglob('*')):
        if p.is_file(): write_entry(z,'assets/'+p.relative_to(ROOT/'app/src/main/assets').as_posix(),p.read_bytes())
aligned=work/'aligned.apk';run([bt/('zipalign'+exe),'-f','-p','4',unsigned,aligned])
check_resource_table(aligned)
dist=ROOT/'dist';dist.mkdir(exist_ok=True)
apk=dist/('BetterLyrics-Mobile-'+version+('-debug' if opt.variant=='debug' else '')+'.apk')
javatool('-jar',bt/'lib/apksigner.jar','sign','--ks',keystore,'--ks-key-alias',opt.alias,
         '--ks-pass','env:BL_STORE_PASSWORD','--key-pass','env:BL_KEY_PASSWORD','--out',apk,aligned)
resource_table=check_resource_table(apk)
javatool('-jar',bt/'lib/apksigner.jar','verify','--verbose','--print-certs',apk)
run([bt/('zipalign'+exe),'-c','4',apk]);run([bt/('aapt2'+exe),'dump','badging',apk])
result={'apk':apk.name,'version':version,'versionCode':int(code),'variant':opt.variant,
        'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
        'resourceTable':resource_table,'minSdk':26,'targetSdk':36,'compileSdk':36,'buildTools':'36.0.0',
        'signing':'externally supplied private key' if opt.variant=='release' else 'local debug key',
        'abi':'universal (no native .so)'}
(dist/('build-info-'+opt.variant+'.json')).write_text(json.dumps(result,indent=2)+'\n')
(dist/(apk.name+'.sha256')).write_text(result['sha256']+'  '+apk.name+'\n')
print(json.dumps(result,indent=2))
