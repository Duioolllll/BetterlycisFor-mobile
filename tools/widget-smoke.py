#!/usr/bin/env python3
"""Exercise the real launcher widget on a dedicated, disposable emulator."""
import argparse, hashlib, json, os, re, subprocess, sys, tempfile, time, xml.etree.ElementTree as ET, zipfile
from pathlib import Path

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--serial',required=True);parser.add_argument('--sdk',required=True);parser.add_argument('--apk',required=True);parser.add_argument('--report',required=True)
args=parser.parse_args()
if not re.fullmatch(r'emulator-\d+',args.serial):raise SystemExit('Use a dedicated disposable emulator, never a physical device.')
sdk=Path(args.sdk).resolve();bt=sdk/'build-tools/36.0.0';android=sdk/'platforms/android-36/android.jar';work=root/'build/widget-harness'
target='io.betterlyrics.mobile';host='io.betterlyrics.widgettest';component=host+'/.WidgetHostActivity'
report={'passed':False,'serial':args.serial,'checks':[],'physicalDeviceTested':False}
installed_target=installed_host=bound=False

def run(cmd,**kw):
    result=subprocess.run(list(map(str,cmd)),text=True,capture_output=True,timeout=120,**kw)
    if result.returncode:raise RuntimeError('Command failed with status '+str(result.returncode)+': '+(result.stderr or result.stdout)[-1200:])
    return result.stdout
def adb(*cmd):return run(['adb','-s',args.serial,*cmd])
def package_present(name):
    result=subprocess.run(['adb','-s',args.serial,'shell','pm','path',name],capture_output=True,text=True,timeout=120)
    if result.returncode not in (0,1):raise RuntimeError('Unable to check installed packages')
    return result.stdout.strip().startswith('package:')
def build_host():
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

def tree():
    adb('shell','uiautomator','dump','/sdcard/betterlyrics-widget-test.xml')
    xml=adb('shell','cat','/sdcard/betterlyrics-widget-test.xml')
    return ET.fromstring(xml[xml.index('<?xml'):])
def find(tree_root,name):
    return next((node for node in tree_root.iter('node') if node.attrib.get('resource-id','').endswith(':id/'+name)),None)
def wait_for(predicate,description,attempts=25):
    latest=''
    for _ in range(attempts):
        try:
            current=tree();latest=' | '.join(node.attrib.get('text','') for node in current.iter('node') if node.attrib.get('text'))
            if predicate(current):return current
        except (RuntimeError,ET.ParseError,ValueError):pass
        time.sleep(1)
    raise AssertionError(description+'; visible text: '+latest[:500])
def text_is(name,value):return lambda t: (find(t,name) is not None and find(t,name).attrib.get('text')==value)
def click(node):
    assert node is not None,'widget control missing'
    coords=list(map(int,re.findall(r'\d+',node.attrib['bounds'])));assert len(coords)==4
    assert coords[2]>coords[0] and coords[3]>coords[1]
    adb('shell','input','tap',str((coords[0]+coords[2])//2),str((coords[1]+coords[3])//2))

try:
    assert adb('shell','getprop','ro.kernel.qemu').strip()=='1','Refusing a non-emulator device'
    assert int(adb('shell','getprop','ro.build.version.sdk').strip())==36,'API36 required'
    assert not package_present(target) and not package_present(host),'Emulator already has target data; refusing to overwrite it'
    harness=build_host()
    adb('install','--no-streaming',Path(args.apk).resolve());installed_target=True
    adb('install','--no-streaming',harness);installed_host=True
    try:adb('shell','appwidget','grantbind','--package',host,'--user','0')
    except RuntimeError as error:
        if 'status 137' not in str(error):raise
        # Low-memory-killer during first boot: free background processes and retry once.
        adb('shell','am','kill-all');time.sleep(3)
        adb('shell','appwidget','grantbind','--package',host,'--user','0')
    bound=True
    adb('shell','pm','grant',host,'android.permission.POST_NOTIFICATIONS')
    adb('shell','am','start','-W','-n',component)
    wait_for(text_is('widget_lyric','음악 감지 연결'),'widget without permission')
    report['checks'].append('widget is hosted and requests music detection when permission is missing')
    fixture={'title':'Widget Test','artist':'Artist','album':'Album','provider':'Local','duration':60000,'raw':'[00:00.00]Widget line one\n[00:05.00]Widget line two\n[00:30.00]Widget line three','score':100}
    filename=hashlib.sha256(b'Widget Test|Artist|Album|60').hexdigest()+'.json'
    with tempfile.NamedTemporaryFile(mode='w',encoding='utf-8',suffix='.json',delete=False) as temp:
        json.dump(fixture,temp);temp_path=Path(temp.name)
    try:
        adb('push',temp_path,'/data/local/tmp/betterlyrics-widget-fixture.json')
        adb('shell','run-as',target,'mkdir','-p','files/lyrics')
        adb('shell','run-as',target,'cp','/data/local/tmp/betterlyrics-widget-fixture.json','files/lyrics/'+filename)
    finally:temp_path.unlink(missing_ok=True)
    adb('shell','cmd','notification','allow_listener',target+'/'+target+'.LyricsListener')
    adb('shell','am','start','-W','-n',component,'--es','command','start')
    current=wait_for(text_is('widget_lyric','Widget line one'),'cached lyric on paused session')
    assert find(current,'widget_title').attrib.get('text')=='Widget Test'
    screenshot=subprocess.run(['adb','-s',args.serial,'exec-out','screencap','-p'],capture_output=True,timeout=60,check=True).stdout
    assert screenshot.startswith(b'\x89PNG\r\n\x1a\n'),'invalid emulator screenshot'
    (Path(args.report).parent/'WIDGET_PREVIEW.png').write_bytes(screenshot)
    report['checks'].append('live media session and cached lyric shown')
    click(find(current,'widget_play'))
    current=wait_for(lambda t:find(t,'widget_play') is not None and find(t,'widget_play').attrib.get('content-desc')=='일시정지','play control')
    current=wait_for(text_is('widget_lyric','Widget line two'),'automatic next-line update')
    click(find(current,'widget_play'))
    current=wait_for(lambda t:find(t,'widget_play') is not None and find(t,'widget_play').attrib.get('content-desc')=='재생','pause control')
    time.sleep(2);assert find(tree(),'widget_lyric').attrib.get('text')=='Widget line two','paused lyric should remain stable'
    report['checks'].append('play/pause actions and timed lyric transition')
    click(find(current,'widget_next'));current=wait_for(text_is('widget_lyric','Widget line three'),'next control')
    click(find(current,'widget_previous'));current=wait_for(text_is('widget_lyric','Widget line one'),'previous control')
    report['checks'].append('previous/next control and seek refresh')
    adb('shell','am','start','-W','-n',component,'--es','command','resize','--ei','width','220','--ei','height','90')
    current=wait_for(lambda t:find(t,'widget_lyric') is not None and find(t,'widget_previous') is None and find(t,'widget_art') is None,'compact widget reflow')
    assert find(current,'widget_play') is not None and find(current,'widget_next') is not None
    report['checks'].append('compact resize keeps playback controls and removes optional artwork/previous')
    click(find(current,'widget_lyric'))
    for _ in range(15):
        top=adb('shell','dumpsys','activity','activities')
        if re.search(r'(mResumedActivity|topResumedActivity).*io\.betterlyrics\.mobile/\.MainActivity',top):break
        time.sleep(1)
    else:raise AssertionError('Widget did not open MainActivity')
    report['checks'].append('widget opens the app')
    instrumentation=adb('shell','am','instrument','-w',host+'/.FontInstrumentation')
    assert 'INSTRUMENTATION_RESULT: fonts=ok' in instrumentation and 'INSTRUMENTATION_CODE: -1' in instrumentation, instrumentation
    report['checks'].append('Android WebView loads all four Korean fonts and persists selection')
    adb('shell','am','start','-W','-f','0x00020000','-n',component,'--es','command','resize','--ei','width','320','--ei','height','140')
    for _ in range(15):
        adb('shell','am','start','-W','-f','0x00020000','-n',component,'--es','command','inspect')
        try:
            if any(node.attrib.get('text')=='WIDGET_STYLE_OK' for node in tree().iter('node')):break
        except (RuntimeError,ET.ParseError,ValueError):pass
        time.sleep(1)
    else:raise AssertionError('Widget did not apply saved background, font size and cover settings')
    wait_for(text_is('widget_lyric','Widget line one'),'widget reconnects after the app process restarts',attempts=35)
    screenshot=subprocess.run(['adb','-s',args.serial,'exec-out','screencap','-p'],capture_output=True,timeout=60,check=True).stdout
    assert screenshot.startswith(b'\x89PNG\r\n\x1a\n')
    (Path(args.report).parent/'WIDGET_CUSTOM.png').write_bytes(screenshot)
    report['checks'].append('widget applies saved background, text size and cover settings')
    report['checks'].append('widget resumes the active session after app process restart')
    report['passed']=True
except Exception as error:
    report['error']=str(error)
    try:(Path(args.report).parent/'WIDGET_LOGCAT.txt').write_text(adb('logcat','-d','-t','600','AndroidRuntime:E','AppWidgetHostView:W','*:S'))
    except Exception:pass
    raise
finally:
    if bound:
        try:adb('shell','appwidget','revokebind','--package',host,'--user','current')
        except Exception:pass
    for name,installed in [(host,installed_host),(target,installed_target)]:
        if installed:
            try:adb('uninstall',name)
            except Exception:pass
    output=Path(args.report);output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
    print(json.dumps(report,indent=2,ensure_ascii=False))
