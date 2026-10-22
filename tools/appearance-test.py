#!/usr/bin/env python3
"""Native preference schema regression using the same JSON version as network tests."""
import argparse, os, subprocess
from pathlib import Path
r=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--json-jar',required=True);a=p.parse_args()
out=r/'build/appearance-tests';out.mkdir(parents=True,exist_ok=True)
jar=str(Path(a.json_jar).resolve())
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-cp',jar,'-d',str(out),str(r/'app/src/main/java/io/betterlyrics/mobile/UiAppearance.java'),str(r/'tests/AppearanceCheck.java')],check=True)
subprocess.run(['java','-ea','-cp',str(out)+os.pathsep+jar,'AppearanceCheck'],check=True)
