"""Build the disposable Android widget and playlist instrumentation host."""
import os
import subprocess
import zipfile
from pathlib import Path

def build_host(root, sdk):
    root=Path(root);sdk=Path(sdk);bt=sdk/'build-tools/36.0.0';android=sdk/'platforms/android-36/android.jar';work=root/'build/widget-harness'
    def run(command,**kwargs):
        result=subprocess.run(list(map(str,command)),text=True,capture_output=True,timeout=120,**kwargs)
        if result.returncode:raise RuntimeError('Harness build failed: '+(result.stderr or result.stdout)[-2000:])
    work.mkdir(parents=True,exist_ok=True);classes=work/'classes';dex=work/'dex';classes.mkdir(exist_ok=True);dex.mkdir(exist_ok=True)
    manifest=root/'tools/widget-harness/AndroidManifest.xml';linked=work/'linked.apk';unsigned=work/'unsigned.apk';aligned=work/'aligned.apk';apk=work/'widget-host.apk'
    run([bt/'aapt2','link','-I',android,'--manifest',manifest,'--min-sdk-version','26','--target-sdk-version','28','-o',linked])
    run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-classpath',android,'-d',classes,*sorted((root/'tools/widget-harness').glob('*.java'))])
    run(['java','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',android,'--output',dex,*sorted(classes.rglob('*.class'))])
    with zipfile.ZipFile(linked) as source,zipfile.ZipFile(unsigned,'w') as dest:
        for name in source.namelist():dest.writestr(name,source.read(name),compress_type=zipfile.ZIP_STORED if name=='resources.arsc' else zipfile.ZIP_DEFLATED)
        dest.writestr('classes.dex',(dex/'classes.dex').read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
    run([bt/'zipalign','-f','4',unsigned,aligned])
    env=os.environ.copy();env['WIDGET_TEST_DEBUG_PASSWORD']='android'
    run(['java','-jar',bt/'lib/apksigner.jar','sign','--ks',root/'.signing/debug.p12','--ks-key-alias','androiddebugkey','--ks-pass','env:WIDGET_TEST_DEBUG_PASSWORD','--key-pass','env:WIDGET_TEST_DEBUG_PASSWORD','--out',apk,aligned],env=env)
    return apk
