#!/usr/bin/env python3
import argparse
import os
import subprocess
import sys
from pathlib import Path

root=Path(__file__).resolve().parents[1]
out=root/'build/jvm-tests';out.mkdir(parents=True,exist_ok=True)
src=root/'app/src/main/java/io/betterlyrics/mobile'
tests=root/'tests'

parser=argparse.ArgumentParser()
parser.add_argument('--sdk',default=os.environ.get('ANDROID_SDK_ROOT',''))
parser.add_argument('--include-korean-check',action='store_true')
parser.add_argument('--include-network-check',action='store_true')
args=parser.parse_args()

if not args.sdk:
    raise SystemExit('ANDROID_SDK_ROOT 또는 --sdk 경로가 필요합니다.')
android=Path(args.sdk).resolve()/'platforms/android-36/android.jar'
if not android.is_file():
    raise SystemExit('android.jar를 찾지 못했습니다: '+str(android))

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
compile_command=['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-cp',str(android),'-d',str(out),*[str(src/name) for name in sources],*[str(tests/(name+'.java')) for name in selected]]
subprocess.run(compile_command,check=True)
classpath=os.pathsep.join([str(out),str(android)])
for name in selected:subprocess.run(['java','-ea','-cp',classpath,name],check=True)
if not args.include_korean_check:print('Skipped live-network KoreanProviderCheck (use --include-korean-check).',file=sys.stderr)
if not args.include_network_check:print('Skipped live-network RepositoryNetworkCheck (use --include-network-check).',file=sys.stderr)
