"""Capture actual native-2048 output twice across USB audio route changes.

Launch 2048 in the hall first. Requires the official local scrcpy binary and
ffmpeg. Keeps raw audio/diagnostics private; a signal check is not listening or
30-minute playability acceptance. Stops input if NsA loses focus.
"""
from __future__ import annotations
import argparse
import array
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import subprocess
import time

import hall_smoke


def tone_fit(samples, frequency=440, sample_rate=48000):
    """Fit the known game tone in the loudest 1024-sample window.

    Normalize by observed energy so the user's volume curve does not decide
    whether an actual tone is mistaken for silence. A least-squares sin/cos
    fit accounts for the finite window and unknown phase.
    """
    count = 1024
    if len(samples) < count: return 0.0
    offset = max(range(0, len(samples)-count+1, 512), key=lambda i: sum(int(x)*int(x) for x in samples[i:i+count]))
    window = samples[offset:offset+count]
    energy = sum(int(x)*int(x) for x in window)
    if not energy: return 0.0
    cosine = [math.cos(math.tau*frequency*i/sample_rate) for i in range(count)]
    sine = [math.sin(math.tau*frequency*i/sample_rate) for i in range(count)]
    cc = sum(c*c for c in cosine); ss = sum(s*s for s in sine); cs = sum(c*s for c,s in zip(cosine,sine))
    xc = sum(x*c for x,c in zip(window,cosine)); xs = sum(x*s for x,s in zip(window,sine))
    determinant = cc*ss-cs*cs
    a = (xc*ss-xs*cs)/determinant; b = (xs*cc-xc*cs)/determinant
    return max(0.0, min(1.0, (a*xc+b*xs)/energy))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--label', required=True, help='Unique private evidence folder name')
    parser.add_argument('--scrcpy', type=Path, default=hall_smoke.ROOT / 'tools/hall/scrcpy/scrcpy-win64-v5.0.1/scrcpy.exe')
    args = parser.parse_args()
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{1,80}', args.label):
        parser.error('Use a short lowercase evidence label')
    destination = hall_smoke.ROOT / '.gamedump/Hall/device-evidence' / args.label
    destination.mkdir(parents=True, exist_ok=False)
    hall_smoke.EVIDENCE = destination
    phone = hall_smoke.Phone()
    phone.foreground()
    game_pid = phone.pid(game=True)
    if not game_pid: raise RuntimeError('Launch native 2048 before running this check')
    maps = phone.adb('exec-out', 'run-as', hall_smoke.PACKAGE, 'cat', '/proc/' + game_pid + '/maps')
    if 'libnsa_2048.so' not in maps or 'libnsa_botw.so' in maps:
        raise RuntimeError('The foreground game process is not the native 2048 slot')
    # Reading a valid 2048 save is a prerequisite, not proof of a current move.
    phone.save_2048()
    environment = os.environ.copy(); environment['ADB'] = str(hall_smoke.ADB)
    observations = []
    for round_number in (1, 2):
        target = destination / f'output-{round_number}.opus'
        events = []
        with (destination / f'capture-{round_number}.log').open('w', encoding='utf-8') as log:
            process = subprocess.Popen([str(args.scrcpy), '-s', phone.serial, '--no-video', '--no-control', '--no-playback', '--require-audio', '--audio-source=output', '--time-limit=16', '--record=' + str(target)], env=environment, stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
            started = time.monotonic()
            try:
                for key in (21, 19, 22, 20, 21):
                    time.sleep(2)
                    phone.foreground()
                    if phone.pid(game=True) != game_pid: raise RuntimeError('Game process changed during capture')
                    before = phone.save_2048()
                    phone.adb('shell', 'input', 'keyevent', str(key)); time.sleep(.15)
                    events.append({'at_seconds': time.monotonic()-started, 'key': key, 'save_changed': before != phone.save_2048()})
                process.wait(timeout=25)
                if process.returncode: raise RuntimeError('Audio capture failed; inspect retained log')
            finally:
                if process.poll() is None: process.terminate(); process.wait(timeout=10)
        pcm = destination / f'output-{round_number}.s16le'
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(target), '-ac', '1', '-ar', '48000', '-f', 's16le', str(pcm)], check=True)
        samples = array.array('h', pcm.read_bytes())
        if not samples: raise RuntimeError('No decoded samples')
        peak = max(abs(n) for n in samples)
        observations.append({'round': round_number, 'seconds':len(samples)/48000, 'nonzero_samples':sum(n != 0 for n in samples), 'peak':peak, 'peak_dbfs':20*math.log10(peak/32768) if peak else None, 'tone_440_fit':tone_fit(samples), 'rms':math.sqrt(sum(int(n)*int(n) for n in samples)/len(samples)), 'sha256':hashlib.sha256(target.read_bytes()).hexdigest(), 'events':events})
        time.sleep(1)
    logs = phone.adb('logcat', '-d', '--pid=' + game_pid)
    (destination / 'game-logcat.txt').write_text(logs, encoding='utf-8')
    record = {'at':datetime.now(timezone.utc).isoformat(), 'installed_apk_sha256':phone.apk_sha256, 'game_pid':game_pid, 'source':'official scrcpy Android output, no video/control/playback', 'captures':observations, 'reopen_log_count':logs.count('2048 audio disconnected; reopening'), 'output_start_log_count':logs.count('2048 audio output started'), 'signal_check_passed':all(r['nonzero_samples'] > 1000 and r['peak'] >= 8 and r['tone_440_fit'] >= .8 and sum(e['save_changed'] for e in r['events']) >= 2 for r in observations), 'subjective_listening_accepted':False, 'full_playability_accepted':False}
    (destination / 'observation.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(record, indent=2))
    if not record['signal_check_passed']: raise RuntimeError('Audio output signal regression; raw evidence retained')


if __name__ == '__main__': main()
