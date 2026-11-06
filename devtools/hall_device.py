"""USB-only APK install and actual device capture. Never launches an emulator."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
ADB = ROOT / 'tools/hall/android-sdk/platform-tools/adb.exe'

def adb(*arguments, **kwargs):
    return subprocess.run([str(ADB), *arguments], check=True, **kwargs)

def device():
    output = subprocess.check_output([str(ADB), 'devices'], text=True)
    devices = [line.split()[0] for line in output.splitlines()[1:] if line.endswith('\tdevice') and not line.startswith('emulator-')]
    if len(devices) != 1: raise RuntimeError('Exactly one authorized USB physical device is required')
    return devices[0]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['install', 'capture', 'record'])
    parser.add_argument('--name', default='hall')
    parser.add_argument('--seconds', type=int, default=30)
    args = parser.parse_args(); serial = device()
    def call(*arguments, **kwargs): return adb('-s', serial, *arguments, **kwargs)
    if args.command == 'install':
        from hall_verify_apk import verify
        verify(ROOT / 'android/app/build/outputs/apk/debug/app-debug.apk')
        call('reverse', 'tcp:8443', 'tcp:8443', capture_output=True)
        call('install', '-r', str(ROOT / 'android/app/build/outputs/apk/debug/app-debug.apk'), capture_output=True)
        call('shell', 'am', 'start', '-f', '0x14000000', '-n', 'org.nsa.hall.debug/org.nsa.hall.MainActivity', capture_output=True)
        print('Separate NsA debug application installed. Existing game APKs and saves were retained.')
        return
    output = ROOT / '.gamedump/Hall/device-evidence'; output.mkdir(parents=True, exist_ok=True)
    safe_name = ''.join(char for char in args.name if char.isalnum() or char in '-_')
    if args.command == 'capture':
        with (output / (safe_name + '.png')).open('wb') as file: call('exec-out', 'screencap', '-p', stdout=file)
    else:
        seconds = min(max(args.seconds, 1), 180)
        remote = '/data/local/tmp/nsa-hall-capture.mp4'
        call('shell', 'screenrecord', '--time-limit', str(seconds), '--bit-rate', '4000000', remote, capture_output=True)
        call('pull', remote, str(output / (safe_name + '.mp4')), capture_output=True)
        call('shell', 'rm', remote, capture_output=True)
    (output / (safe_name + '-provenance.json')).write_text(json.dumps({'source': 'USB physical Android device', 'application_id': 'org.nsa.hall.debug', 'captured_at': datetime.now(timezone.utc).isoformat(), 'synthetic': False}, indent=2), encoding='utf-8')
    print('Actual device media saved in .gamedump/Hall/device-evidence; review before publishing.')

if __name__ == '__main__': main()
