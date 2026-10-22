#!/usr/bin/env python3
"""Heuristic publication checks; reports paths only, never matching secret values."""
import re, subprocess
from pathlib import Path
r=Path(__file__).resolve().parents[1]
files=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=r).decode().split('\0')
patterns=[rb'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----',rb'gh[pousr]_[A-Za-z0-9]{30,}',rb'github_pat_[A-Za-z0-9_]{30,}',rb'AKIA[0-9A-Z]{16}',rb'AIza[0-9A-Za-z_-]{30,}',rb'(?i)Bearer\s+[A-Za-z0-9_.-]{24,}',rb'[A-Za-z0-9._%+-]+@(?:gmail|outlook|hotmail)\.com']
failures=[];count=0
for name in sorted(set(files)):
 if not name or not (r/name).is_file(): continue
 count+=1;p=Path(name)
 if any(part in ['.signing','node_modules','__pycache__'] for part in p.parts) or p.suffix.lower() in ['.p12','.jks','.keystore','.pem','.pfx','.p8','.key'] or p.name=='.env': failures.append((name,'private filename'))
 data=(r/name).read_bytes()
 for pattern in patterns:
  if re.search(pattern,data): failures.append((name,'credential/PII pattern'))
print(f'Inspected {count} publication files; {len(failures)} findings.')
for name,kind in failures: print(kind+': '+name)
raise SystemExit(bool(failures))
