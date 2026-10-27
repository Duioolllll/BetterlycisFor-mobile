#!/usr/bin/env bash
set -euo pipefail
mode=${1:?Expected widget or release}
case "$mode" in widget|release) ;; *) exit 2 ;; esac
cd "$(dirname "$0")/.."
export JAVA_HOME="$JAVA_HOME_17_X64"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
sudo apt-get update -qq
sudo apt-get install -y --no-install-recommends libpulse0 libasound2t64 libnss3 libx11-6 libx11-xcb1 libxcb1 libxcomposite1 libxcursor1 libxi6 libxtst6 libxrandr2 libgbm1 libglu1-mesa libvulkan1
if [ ! -x "$ANDROID_HOME/emulator/emulator" ]; then
  "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "emulator"
fi
test -c /dev/kvm
sudo chown "$(id -u)" /dev/kvm
"$ANDROID_HOME/emulator/emulator" -accel-check
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-36" "build-tools;36.0.0" "platform-tools" "system-images;android-36;google_apis;x86_64"
export ANDROID_AVD_HOME="$RUNNER_TEMP/betterlyrics-avd"
mkdir -p "$ANDROID_AVD_HOME"
"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -n betterlyrics_widget -k "system-images;android-36;google_apis;x86_64" --device pixel_6 --path "$ANDROID_AVD_HOME/betterlyrics_widget.avd" --force
test -f "$ANDROID_AVD_HOME/betterlyrics_widget.ini"
emulator_pid=''
cleanup(){
  result=$?
  if [ "$result" -ne 0 ] && [ -f "$RUNNER_TEMP/emulator.log" ]; then tail -100 "$RUNNER_TEMP/emulator.log" | sed '/Sending adb public key/d'; fi
  if [ -n "$emulator_pid" ]; then timeout 10 adb -s emulator-5554 emu kill >/dev/null 2>&1 || kill "$emulator_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT
"$ANDROID_HOME/emulator/emulator" -avd betterlyrics_widget -port 5554 -no-window -no-audio -no-boot-anim -no-snapshot -gpu software -memory 3072 -cores 2 > "$RUNNER_TEMP/emulator.log" 2>&1 &
emulator_pid=$!
connection_deadline=$((SECONDS + 180))
until [ "$(adb -s emulator-5554 get-state 2>/dev/null)" = device ]; do
  kill -0 "$emulator_pid"
  if [ "$SECONDS" -ge "$connection_deadline" ]; then exit 1; fi
  sleep 2
done
boot_deadline=$((SECONDS + 420))
until [ "$(adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
  kill -0 "$emulator_pid"
  if [ "$SECONDS" -ge "$boot_deadline" ]; then exit 1; fi
  sleep 2
done
python3 tools/build.py --sdk "$ANDROID_HOME" --variant debug
python3 - <<'PY'
import json, os, subprocess, sys
from pathlib import Path
apk=Path('dist')/json.loads(Path('dist/build-info-debug.json').read_text())['apk']
subprocess.run([sys.executable,'tools/widget-smoke.py','--serial','emulator-5554','--sdk',os.environ['ANDROID_HOME'],'--apk',str(apk),'--report',str(Path(os.environ['RUNNER_TEMP'])/'WIDGET_VALIDATION.json')],check=True)
PY
if [ "$mode" = release ]; then
  python3 tools/install-smoke.py --serial emulator-5554 --report "$RUNNER_TEMP/INSTALL_VALIDATION.json"
fi
