#!/usr/bin/env python3
import argparse, hashlib, json, zipfile
p=argparse.ArgumentParser();p.add_argument('first');p.add_argument('second');a=p.parse_args()
def inspect(path):
 with open(path,'rb') as f: sha=hashlib.sha256(f.read()).hexdigest()
 with zipfile.ZipFile(path) as z:
  entries={n:hashlib.sha256(z.read(n)).hexdigest() for n in sorted(z.namelist()) if not n.upper().startswith('META-INF/')}
 return sha,entries
x,y=inspect(a.first),inspect(a.second)
r={'identicalApk':x[0]==y[0],'identicalPayload':x[1]==y[1],'firstSha256':x[0],'secondSha256':y[0]}
print(json.dumps(r,indent=2));raise SystemExit(0 if r['identicalPayload'] else 1)
