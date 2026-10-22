#!/usr/bin/env python3
import subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
out=root/'build/jvm-tests';out.mkdir(parents=True,exist_ok=True)
src=root/'app/src/main/java/io/betterlyrics/mobile'
tests=list((root/'tests').glob('*Tests.java'))
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-d',str(out),str(src/'LyricParser.java'),str(src/'MatchScore.java'),str(src/'LyricTextPolicy.java'),str(src/'AudioSignal.java'),*[str(p) for p in tests]],check=True)
for p in tests:subprocess.run(['java','-ea','-cp',str(out),p.stem],check=True)
