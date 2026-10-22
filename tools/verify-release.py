#!/usr/bin/env python3
"""Offline release manifest, digest and APK asset checks (no private key required)."""
import hashlib,json,re,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
from apk_packaging import check_resource_table
r=Path(__file__).resolve().parents[1]
m=json.loads((r/'downloads/release.json').read_text());name=m['apk']
assert re.fullmatch(r'BetterLyrics-Mobile-\d+\.\d+\.\d+\.apk',name)
apk=r/'downloads'/name
assert hashlib.sha256(apk.read_bytes()).hexdigest()==m['sha256']
assert f"{m['sha256']}  {name}" in (r/'downloads/SHA256SUMS').read_text()
ns='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse(r/'app/src/main/AndroidManifest.xml').getroot()
assert manifest.attrib[ns+'versionName']==m['version']
assert int(manifest.attrib[ns+'versionCode'])==m['versionCode']
with zipfile.ZipFile(apk) as z:
 assert z.testzip() is None
 for path in (r/'app/src/main/assets').rglob('*'):
  if path.is_file(): assert z.read('assets/'+path.relative_to(r/'app/src/main/assets').as_posix())==path.read_bytes()
 assert not any(re.search(r'(\.p12|\.jks|\.keystore|\.pem|\.env|\.key)$',name,re.I) for name in z.namelist())
 assert 'classes.dex' in z.namelist()
resource_table=check_resource_table(apk)
assert m.get('resourceTable') == resource_table
print('Release checksum, version, web assets, private-file exclusions and Android resource-table packaging verified.')
