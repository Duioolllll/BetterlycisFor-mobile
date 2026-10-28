#!/usr/bin/env bash
set -euo pipefail
mode=${1:?Expected widget, playlist, install or release}
case "$mode" in widget|playlist|install|release) ;; *) exit 2 ;; esac
cd "$(dirname "$0")/.."
export JAVA_HOME="$JAVA_HOME_17_X64"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
sudo apt-get update -qq
sudo apt-get install -y --no-install-recommends libpulse0 libasound2t64 libnss3 libx11-6 libx11-xcb1 libxcb1 libxcomposite1 libxcursor1 libxi6 libxtst6 libxrandr2 libgbm1 libglu1-mesa libvulkan1
# Pinned stable emulator 36.4.10: the newer 37.2.12 host process has
# intermittently crashed under headless graphics while running WebView/audio.
emulator_archive="$RUNNER_TEMP/betterlyrics-emulator.zip"
emulator_root="$RUNNER_TEMP/betterlyrics-emulator"
curl --fail --location --silent --show-error --retry 3 --max-time 240 https://dl.google.com/android/repository/emulator-linux_x64-15004761.zip --output "$emulator_archive"
printf '%s  %s\n' '58bfdf0572f06ec007a7287154cdc6b2ebfea947d07a1e26a0f6b5e1a39e9191' "$emulator_archive" | sha256sum -c -
mkdir -p "$emulator_root"
unzip -q "$emulator_archive" -d "$emulator_root"
emulator_binary="$emulator_root/emulator/emulator"
test -c /dev/kvm
sudo chown "$(id -u)" /dev/kvm
"$emulator_binary" -accel-check
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
"$emulator_binary" -avd betterlyrics_widget -port 5554 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader -feature -Vulkan -memory 3072 -cores 2 > "$RUNNER_TEMP/emulator.log" 2>&1 &
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
if [ "$mode" != install ]; then
  python3 tools/build.py --sdk "$ANDROID_HOME" --variant debug
  export BETTERLYRICS_EMULATOR_MODE="$mode"
  python3 - <<'PY'
import json, os, subprocess, sys
from pathlib import Path
apk=Path('dist')/json.loads(Path('dist/build-info-debug.json').read_text())['apk']
if os.environ['BETTERLYRICS_EMULATOR_MODE']!='playlist':
    subprocess.run([sys.executable,'tools/widget-smoke.py','--serial','emulator-5554','--sdk',os.environ['ANDROID_HOME'],'--apk',str(apk),'--report',str(Path(os.environ['RUNNER_TEMP'])/'WIDGET_VALIDATION.json')],check=True)
if os.environ['BETTERLYRICS_EMULATOR_MODE']!='widget':
    subprocess.run([sys.executable,'tools/playlist-device-smoke.py','--serial','emulator-5554','--sdk',os.environ['ANDROID_HOME'],'--apk',str(apk),'--report',str(Path(os.environ['RUNNER_TEMP'])/'PLAYLIST_VALIDATION.json')],check=True)
PY
fi
if [ "$mode" = release ] || [ "$mode" = install ]; then
  python3 tools/install-smoke.py --serial emulator-5554 --report "$RUNNER_TEMP/INSTALL_VALIDATION.json"
fi
