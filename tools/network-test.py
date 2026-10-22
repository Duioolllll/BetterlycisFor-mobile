#!/usr/bin/env python3
"""Optional live check with a separately supplied org.json test JAR and Android SDK.
No API keys or music accounts are used; song text is never printed.
"""
import argparse,os,subprocess,urllib.parse
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--sdk',required=True);p.add_argument('--json-jar',required=True);p.add_argument('--offline',action='store_true');p.add_argument('--korean',action='store_true');a=p.parse_args()
android=next(Path(a.sdk).resolve().rglob('android.jar'));jsonjar=Path(a.json_jar).resolve()
out=root/'build/network-tests';out.mkdir(parents=True,exist_ok=True)
src=root/'app/src/main/java/io/betterlyrics/mobile'
cp=os.pathsep.join([str(jsonjar),str(android)])
test='RepositoryRegressionCheck' if a.offline else 'KoreanProviderCheck' if a.korean else 'RepositoryNetworkCheck'
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-cp',cp,'-d',str(out),*[str(src/n) for n in ['LyricParser.java','MatchScore.java','LyricTextPolicy.java','LyricsRepository.java']],str(root/('tests/'+test+'.java'))],check=True)
cmd=['java'];proxy=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY',os.environ.get('https_proxy','')))
if proxy.hostname:cmd+=['-Dhttps.proxyHost='+proxy.hostname,'-Dhttps.proxyPort='+str(proxy.port or 80),'-Dhttp.proxyHost='+proxy.hostname,'-Dhttp.proxyPort='+str(proxy.port or 80)]
cmd+=['-cp',str(out)+os.pathsep+cp,test];subprocess.run(cmd,check=True)
