#!/usr/bin/env python3
import argparse
import hashlib
import os
import subprocess
import sys
import urllib.request
from pathlib import Path

root=Path(__file__).resolve().parents[1]
out=root/'build/jvm-tests';out.mkdir(parents=True,exist_ok=True)
src=root/'app/src/main/java/io/betterlyrics/mobile'
tests=root/'tests'

parser=argparse.ArgumentParser()
parser.add_argument('--sdk',default=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or '')
parser.add_argument('--include-korean-check',action='store_true')
parser.add_argument('--include-network-check',action='store_true')
args=parser.parse_args()

if not args.sdk:
    raise SystemExit('ANDROID_SDK_ROOT/ANDROID_HOME 또는 --sdk 경로가 필요합니다.')
android=Path(args.sdk).resolve()/'platforms/android-36/android.jar'
if not android.is_file():
    raise SystemExit('android.jar를 찾지 못했습니다: '+str(android))

# android.jar's org.json is a compile-only stub (its methods throw at runtime and
# JSONObject.similar does not exist), so the JVM checks run against the real
# org.json build, pinned and checksum-verified, ahead of android.jar.
JSON_VERSION='20260814'
JSON_SHA256='7dd2de1533e766e2b6c07d8194da85145ad313a167a4760050f5c9b6839604fe'
env_json=os.environ.get('BL_JSON_JAR','')
json_jar=Path(env_json) if env_json else out/'lib'/('json-'+JSON_VERSION+'.jar')
if not json_jar.is_file():
    json_jar.parent.mkdir(parents=True,exist_ok=True)
    urllib.request.urlretrieve('https://repo1.maven.org/maven2/org/json/json/'+JSON_VERSION+'/json-'+JSON_VERSION+'.jar',json_jar)
if hashlib.sha256(json_jar.read_bytes()).hexdigest()!=JSON_SHA256:
    raise SystemExit('org.json jar 체크섬이 일치하지 않습니다: '+str(json_jar))

selected=[
    'RepositoryRegressionCheck',
    'AppearanceCheck',
    'TextPolicyTests',
    'ParserTests',
    'MathTests',
    'AudioSignalTests',
]
if args.include_korean_check:selected.append('KoreanProviderCheck')
if args.include_network_check:selected.append('RepositoryNetworkCheck')

sources=[
    'LyricParser.java',
    'MatchScore.java',
    'LyricTextPolicy.java',
    'AudioSignal.java',
    'UiAppearance.java',
    'LyricsRepository.java',
]
compile_command=['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-cp',os.pathsep.join([str(json_jar),str(android)]),'-d',str(out),*[str(src/name) for name in sources],*[str(tests/(name+'.java')) for name in selected]]
subprocess.run(compile_command,check=True)
classpath=os.pathsep.join([str(out),str(json_jar),str(android)])
for name in selected:subprocess.run(['java','-ea','-cp',classpath,name],check=True)
if not args.include_korean_check:print('Skipped live-network KoreanProviderCheck (use --include-korean-check).',file=sys.stderr)
if not args.include_network_check:print('Skipped live-network RepositoryNetworkCheck (use --include-network-check).',file=sys.stderr)
