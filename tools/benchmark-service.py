#!/usr/bin/env python3
"""Local JVM comparison against source from a real Git revision; no live API calls."""
import argparse,json,platform,subprocess
from pathlib import Path

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--baseline',required=True,help='Retained Git revision, e.g. 8a12f2a')
parser.add_argument('--report',default='build/service-benchmark.json')
parser.add_argument('--check-only',action='store_true',help='Compare output only; omit timing/allocation measurements')
args=parser.parse_args()
baseline=subprocess.check_output(['git','rev-parse','--verify',args.baseline+'^{commit}'],cwd=root,text=True).strip()
source=subprocess.check_output(['git','show',baseline+':app/src/main/java/io/betterlyrics/mobile/AudioSignal.java'],cwd=root,text=True)
work=root/'build/service-benchmark';work.mkdir(parents=True,exist_ok=True)
old=work/'BaselineAudioSignal.java';old.write_text(source.replace('AudioSignal','BaselineAudioSignal'),encoding='utf-8')
src=root/'app/src/main/java/io/betterlyrics/mobile'
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-d',str(work),str(old),str(src/'AudioSignal.java'),str(src/'IoSupport.java'),str(root/'tests/ServiceProcessingBenchmark.java')],check=True)
result=subprocess.run(['java','-Xms128m','-Xmx256m','-cp',str(work),'ServiceProcessingBenchmark',*(['--check-only'] if args.check_only else [])],check=True,capture_output=True,text=True)
report=json.loads(result.stdout)
report.update(baselineCommit=baseline,host=platform.platform(),java=subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True).splitlines()[0],scope='Linux JVM microbenchmark, not Android device latency or battery measurement')
if args.check_only:report['scope']='JVM bit-for-bit output comparison; no performance timings measured'
destination=root/args.report;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
