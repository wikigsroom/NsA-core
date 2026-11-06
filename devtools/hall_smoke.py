"""Exercise the installed hall through real USB UI input and retain private evidence."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import struct
import time
import xml.etree.ElementTree as ET
from PIL import Image

from hall_device import ROOT, ADB, device

PACKAGE = 'org.nsa.hall.debug'
EVIDENCE = ROOT / '.gamedump/Hall/device-evidence'


class Phone:
    def __init__(self):
        self.serial = device()
        EVIDENCE.mkdir(parents=True, exist_ok=True)
        self.steps = []
        self.last_dimensions = None
        installed = self.adb('shell', 'pm', 'path', PACKAGE).strip().removeprefix('package:')
        self.apk_sha256 = self.adb('shell', 'sha256sum', installed).split()[0] if installed.startswith('/data/app/') else ''

    def adb(self, *args, binary=False, check=True):
        result = subprocess.run([str(ADB), '-s', self.serial, *args], capture_output=True, check=check)
        return result.stdout if binary else result.stdout.decode('utf-8', errors='replace')

    def foreground(self):
        # Rotation/Activity hand-off may briefly leave the focus null. Never
        # send input while it is null or after another application takes focus.
        for attempt in range(11):
            state = self.adb('shell', 'dumpsys', 'window')
            focused = next((line for line in state.splitlines() if 'mCurrentFocus=' in line), '')
            if PACKAGE in focused: return
            if focused and 'null' not in focused: break
            time.sleep(.3)
        raise RuntimeError('NsA is no longer focused; stopped before sending input to another app')

    def pid(self, game=False):
        # Android pidof returns 1 for the expected absence of a process.
        name = PACKAGE + (':game' if game else '')
        result = subprocess.run([str(ADB), '-s', self.serial, 'shell', 'pidof', name], capture_output=True)
        if result.returncode not in (0, 1) or result.stderr.strip():
            raise RuntimeError('Could not query the application process')
        value = result.stdout.decode('ascii').strip()
        if value and not all(part.isdigit() for part in value.split()):
            raise ValueError('Invalid process observation')
        return value

    def start_hall(self):
        if self.pid(game=True): raise RuntimeError('Exit the live game before resetting the test navigation')
        self.adb('shell', 'am', 'force-stop', PACKAGE)
        # Clear only children of our activity, including an orphaned system
        # document picker from a previously interrupted import scenario.
        self.adb('shell', 'am', 'start', '-f', '0x14000000', '-n', PACKAGE + '/org.nsa.hall.MainActivity')
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            focus = next((s for s in self.adb('shell', 'dumpsys', 'window').splitlines() if 'mCurrentFocus=' in s), '')
            if PACKAGE in focus: return
            time.sleep(.3)
        raise RuntimeError('The authorized hall did not acquire focus after startup; no UI input sent')

    def tree(self):
        self.foreground()
        for attempt in range(3):
            try:
                self.adb('shell', 'uiautomator', 'dump', '/data/local/tmp/nsa-hall-ui.xml')
                break
            except subprocess.CalledProcessError:
                if attempt == 2: raise
                time.sleep(.5)
        return ET.fromstring(self.adb('exec-out', 'cat', '/data/local/tmp/nsa-hall-ui.xml', binary=True))

    def wait(self, label, timeout=40, scroll=False):
        deadline = time.monotonic() + timeout
        attempts = 0
        wanted = (label,) if isinstance(label, str) else tuple(label)
        while time.monotonic() < deadline:
            root = self.tree()
            nodes = [node for node in root.iter('node') if node.get('text') in wanted or node.get('content-desc') in wanted]
            if nodes:
                return nodes[-1]
            if scroll and attempts < 7:
                areas = [n for n in root.iter('node') if n.get('scrollable') == 'true']
                if areas:
                    bounds = [int(n) for n in re.findall(r'\d+', areas[0].get('bounds', ''))]
                    if len(bounds) == 4:
                        left, top, right, bottom = bounds
                        self.foreground()
                        self.adb('shell', 'input', 'swipe', str((left + right) // 2), str(top + (bottom - top) * 4 // 5), str((left + right) // 2), str(top + (bottom - top) // 4), '350')
                        attempts += 1
            time.sleep(.6)
        labels = [node.get('text') or node.get('content-desc') for node in root.iter('node')]
        raise TimeoutError(f'UI did not show {label!r}: {labels[:35]}')

    def tap(self, label, timeout=40):
        node = self.wait(label, timeout, scroll=True)
        if node.get('enabled') == 'false':
            raise RuntimeError(f'UI action is disabled: {label}')
        values = [int(n) for n in re.findall(r'\d+', node.get('bounds', ''))]
        if len(values) != 4:
            raise ValueError('Invalid UI bounds')
        self.foreground()
        self.adb('shell', 'input', 'tap', str((values[0] + values[2]) // 2), str((values[1] + values[3]) // 2))
        self.steps.append({'action': 'tap', 'label': label, 'at': datetime.now(timezone.utc).isoformat()})
        time.sleep(.6)

    def back(self):
        self.foreground(); self.adb('shell', 'input', 'keyevent', '4'); time.sleep(.6)

    def capture(self, name):
        self.foreground()
        path = EVIDENCE / (name + '.png')
        data = self.adb('exec-out', 'screencap', '-p', binary=True)
        self.last_dimensions = struct.unpack('>II', data[16:24])
        path.write_bytes(data)
        self.provenance(name)
        return path

    def provenance(self, name):
        (EVIDENCE / (name + '-provenance.json')).write_text(json.dumps({
            'source': 'USB physical Android device', 'application_id': PACKAGE,
            'captured_at': datetime.now(timezone.utc).isoformat(), 'synthetic': False,
            'installed_apk_sha256': self.apk_sha256,
        }, indent=2), encoding='utf-8')

    def save_2048(self):
        data = self.adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/players/local/saves/nsa-2048/committed/state.dat', binary=True)
        if len(data) < 80:
            raise ValueError('The native game has not committed a complete save')
        return data

    def record_start(self, name, seconds=45):
        self.foreground()
        remote = '/data/local/tmp/nsa-' + name + '.mp4'
        process = subprocess.Popen([str(ADB), '-s', self.serial, 'shell', 'screenrecord', '--time-limit', str(seconds), '--bit-rate', '4000000', remote], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        return process, remote, name

    def record_finish(self, recording):
        process, remote, name = recording
        if process.poll() is None:
            # Stop only this recording, including when focus moved away.
            # Never leave a failed UI scenario recording another application.
            for candidate in self.adb('shell', 'pidof', 'screenrecord', check=False).split():
                if not candidate.isdigit(): continue
                command = self.adb('exec-out', 'cat', '/proc/' + candidate + '/cmdline', binary=True, check=False)
                if remote.encode() in command.split(b'\0'):
                    self.adb('shell', 'kill', '-2', candidate)
        process.wait(timeout=190)
        if process.returncode:
            raise RuntimeError('Device recording failed')
        self.adb('pull', remote, str(EVIDENCE / (name + '.mp4')))
        self.adb('shell', 'rm', remote)
        self.provenance(name)


def hall(phone):
    phone.adb('reverse', 'tcp:8443', 'tcp:8443')
    if phone.pid(game=True):
        raise RuntimeError('Exit the live game before starting a hall walkthrough')
    phone.start_hall()
    time.sleep(2)
    recording = phone.record_start('hall-walkthrough', 180)
    try:
        hall_pages(phone)
    finally:
        phone.record_finish(recording)


def hall_pages(phone):
    tree = phone.tree()
    if any(node.get('text') == '进入游戏大厅' for node in tree.iter('node')):
        phone.capture('welcome'); phone.tap('进入游戏大厅')
    phone.wait('发现游戏'); time.sleep(2); phone.capture('discovery')
    if any(node.get('text') == '实验游戏' for node in phone.tree().iter('node')):
        phone.tap('实验游戏'); phone.capture('discovery')
    phone.tap('2048 · 数字花园'); phone.wait(('实验适配', '下载游戏', '继续游戏'), scroll=True); phone.capture('game-detail')
    action = phone.wait(('下载游戏', '继续游戏'), scroll=True)
    retained_save = None
    if action.get('text') == '继续游戏':
        retained_save = phone.adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/players/local/saves/nsa-2048/committed/state.dat', binary=True, check=False)
        phone.tap('管理游戏'); phone.tap('删除游戏资源')
        checkboxes = [node for node in phone.tree().iter('node') if node.get('checkable') == 'true']
        if len(checkboxes) != 1 or checkboxes[0].get('checked') != 'true':
            raise AssertionError('Resource removal did not default to retaining saves')
        phone.tap('确认删除'); phone.tap('发现游戏'); phone.tap('2048 · 数字花园'); phone.wait(('实验适配', '下载游戏', '继续游戏'), scroll=True); phone.capture('game-detail')
        if retained_save and phone.save_2048() != retained_save:
            raise AssertionError('Removing demo resources changed its retained save')
    phone.tap('下载游戏'); phone.wait('确认下载'); phone.capture('download-confirmation')
    phone.tap('我了解实验适配的限制，继续内部测试')
    phone.tap('确认下载'); phone.wait('下载中心'); phone.capture('downloads')
    phone.wait('游戏已准备好', timeout=120); phone.capture('installed')
    if retained_save and phone.save_2048() != retained_save:
        raise AssertionError('Reinstalling demo resources changed its retained save')
    phone.tap('我的游戏'); phone.wait('继续游戏'); phone.capture('library')
    phone.tap('设置'); phone.capture('settings')
    phone.tap('设备支持'); phone.capture('device-support'); phone.back()
    phone.tap('存储空间'); phone.capture('storage'); phone.back()
    phone.tap('帮助与反馈'); phone.capture('feedback'); phone.back()
    phone.tap('我的游戏'); phone.tap('2048 · 数字花园'); phone.tap('管理游戏'); phone.capture('manage-game')
    phone.tap('存档与备份'); phone.capture('saves'); phone.back(); phone.back(); phone.back()
    phone.wait('我的游戏')


def native_2048(phone):
    phone.tap('我的游戏'); phone.tap('2048 · 数字花园'); phone.tap('继续游戏')
    deadline = time.monotonic() + 35
    while time.monotonic() < deadline:
        if phone.pid(game=True): break
        time.sleep(.5)
    time.sleep(4)
    phone.foreground(); phone.capture('2048-before-input')
    if phone.last_dimensions[0] <= phone.last_dimensions[1]: raise AssertionError('The game did not enter landscape')
    def board_hash(name):
        with Image.open(EVIDENCE / (name + '.png')) as frame:
            # System navigation insets can change the native buffer dimensions.
            # Compare all 16 rendered tile values, independent of screen scaling.
            palette = [0xff28211b,0xffb9d5d7,0xff9fcdd2,0xff70beca,0xff58a1c1,0xff527cae,0xff5364a1,0xff684da1,0xff8641a0,0xffa23991,0xffad4474,0xffb65f52]
            colors = {(c & 255, (c >> 8) & 255, (c >> 16) & 255): 0 if i == 0 else 2 ** i for i, c in enumerate(palette)}
            frame = frame.convert('RGB')
            mask = Image.new('L', frame.size)
            mask.putdata([255 if pixel in colors else 0 for pixel in frame.get_flattened_data()])
            bounds = mask.getbbox()
            if bounds is None: raise AssertionError('No native board tiles were rendered')
            left, top, right, bottom = bounds
            cells = [colors.get(frame.getpixel((int(left + (col + .23) * (right - left) / 4), int(top + (row + .23) * (bottom - top) / 4)))) for row in range(4) for col in range(4)]
            if any(value is None for value in cells): raise AssertionError('Could not read every rendered native tile')
            return cells
    initial_board = board_hash('2048-before-input')
    pids = {'hall': phone.pid(), 'game': phone.pid(game=True)}
    if not pids['hall'] or not pids['game'] or pids['hall'] == pids['game']:
        raise AssertionError('Independent live main/game processes were not observed')
    before = phone.save_2048()
    recording = phone.record_start('2048-play', 30)
    try:
        for key in (21, 19, 22, 20, 21, 19, 22, 20):
            phone.foreground(); phone.adb('shell', 'input', 'keyevent', str(key)); time.sleep(.4)
        phone.capture('2048-play')
        played_board = board_hash('2048-play')
        if initial_board == played_board: raise AssertionError('The visible board did not respond to real input')
        (EVIDENCE / '2048-audio-pipeline.txt').write_text(phone.adb('shell', 'dumpsys', 'media.aaudio'), encoding='utf-8')
        (EVIDENCE / '2048-audio-flinger.txt').write_text(phone.adb('shell', 'dumpsys', 'media.audio_flinger'), encoding='utf-8')
        after = phone.save_2048()
        if played_board != list(struct.unpack('<40I', after)[2:18]):
            raise AssertionError('The visible board differs from the committed native save')
        if before == after:
            raise AssertionError('Real directional input did not change the native save')
        phone.back(); phone.wait('返回游戏大厅'); phone.capture('game-pause')
        phone.tap('返回游戏大厅'); phone.wait('退出游戏'); phone.capture('game-exit')
        # The detail page deliberately preserves its pre-launch scroll position.
        # Its action remains visible even when the game heading is above the viewport.
        phone.tap('退出游戏'); phone.wait('继续游戏'); time.sleep(1)
        if phone.pid(game=True):
            raise AssertionError('The old game process survived exit')
        phone.capture('returned-portrait')
        if phone.last_dimensions[0] >= phone.last_dimensions[1]: raise AssertionError('The hall did not restore portrait')
        phone.tap('继续游戏'); time.sleep(4); phone.foreground(); phone.capture('2048-reloaded')
        reloaded = phone.save_2048()
        reloaded_board = board_hash('2048-reloaded')
        if reloaded_board != played_board: raise AssertionError('The relaunched game did not render its committed board')
        if reloaded != after:
            raise AssertionError('Committed native state changed during exit/reload without new input')
        pids['reloaded_game'] = phone.pid(game=True)
        if pids['reloaded_game'] == pids['game']:
            raise AssertionError('The second session reused the old process')
        (EVIDENCE / '2048-session-observations.json').write_text(json.dumps({
            'installed_apk_sha256': phone.apk_sha256, 'processes': pids,
            'committed_sha256': hashlib.sha256(after).hexdigest(),
            'reloaded_sha256': hashlib.sha256(reloaded).hexdigest(),
            'played_board': played_board, 'reloaded_board': reloaded_board,
            'observed_at': datetime.now(timezone.utc).isoformat(),
            'scope': 'Actual input, first safe return, fresh process and matching reloaded save/board; final cleanup is a separate step',
        }, indent=2), encoding='utf-8')
        phone.back(); phone.tap('返回游戏大厅'); phone.tap('退出游戏'); phone.wait('继续游戏'); phone.back(); phone.wait('我的游戏')
    finally:
        phone.record_finish(recording)
    return {'processes': pids, 'before_sha256': hashlib.sha256(before).hexdigest(), 'committed_sha256': hashlib.sha256(after).hexdigest(), 'reloaded_sha256': hashlib.sha256(reloaded).hexdigest(), 'played_board': played_board, 'reloaded_board': reloaded_board, 'audio': 'Private AAudio pipeline dump retained; audible output requires separate observation'}


def botw_download(phone):
    """Opt-in large private-content check; never downloads from a third party."""
    if phone.pid(game=True): raise RuntimeError('Exit the live game before starting the private-content check')
    phone.adb('reverse', 'tcp:8443', 'tcp:8443')
    phone.start_hall()
    time.sleep(2)
    phone.tap('发现游戏')
    if any(node.get('text') == '实验游戏' for node in phone.tree().iter('node')): phone.tap('实验游戏')
    phone.tap('塞尔达传说 旷野之息')
    phone.capture('botw-detail')
    action = phone.wait(('下载游戏', '查看下载任务', '继续游戏'), scroll=True)
    if action.get('text') == '继续游戏':
        return {'installed': True, 'already_installed': True}
    if action.get('text') == '下载游戏':
        phone.tap('下载游戏'); phone.wait('确认下载'); phone.capture('botw-download-confirmation')
        phone.tap('我了解实验适配的限制，继续内部测试'); phone.tap('确认下载')
    else:
        phone.tap('查看下载任务')
    phone.wait('下载中心'); phone.capture('botw-downloads')
    statuses = {'排队等待', '正在下载', '等待恢复条件', '已主动暂停', '正在校验文件', '正在准备游戏', '游戏已准备好', '任务失败，可重试', '已取消'}
    deadline = time.monotonic() + 7200
    last = None
    while time.monotonic() < deadline:
        root = phone.tree()
        candidates = []
        for node in root.iter('node'):
            labels = [child.get('text') for child in node.iter('node')]
            states = statuses.intersection(labels)
            if ('botw' in labels or '塞尔达传说 旷野之息' in labels) and states: candidates.append((node, states, labels))
        if not candidates: raise AssertionError('The BotW transfer card is no longer visible')
        _, states, labels = candidates[-1]
        if len(states) != 1: raise AssertionError('Could not isolate the BotW transfer state')
        state = next(iter(states))
        if state != last:
            print('Private BotW transfer: ' + state, flush=True)
            phone.capture('botw-transfer-' + str(len(phone.steps)))
            phone.steps.append({'action': 'transfer_state', 'state': state, 'at': datetime.now(timezone.utc).isoformat()})
            last = state
        if state == '游戏已准备好':
            phone.capture('botw-installed')
            return {'installed': True, 'already_installed': False}
        if state in {'任务失败，可重试', '已取消', '已主动暂停', '等待恢复条件'}:
            raise RuntimeError('Private BotW transfer requires attention: ' + state + '; ' + ' / '.join(label for label in labels if label))
        time.sleep(10)
    raise TimeoutError('Private BotW installation did not complete within two hours')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('scenario', choices=['hall', '2048', 'all', 'botw'])
    args = parser.parse_args(); phone = Phone(); result = {'scenario': args.scenario, 'passed': False}
    try:
        if args.scenario == '2048':
            if phone.pid(game=True): raise RuntimeError('Exit the live game before starting a fresh session check')
            phone.start_hall()
            time.sleep(2)
        if args.scenario in ('hall', 'all'):
            hall(phone); result['hall'] = {'passed': True}
        if args.scenario in ('2048', 'all'): result['2048'] = native_2048(phone)
        if args.scenario == 'botw': result['botw_download'] = botw_download(phone)
        result['passed'] = True
    finally:
        result['steps'] = phone.steps
        result['finished_at'] = datetime.now(timezone.utc).isoformat()
        (EVIDENCE / ('smoke-' + args.scenario + '.json')).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Physical device scenario passed; private evidence is under .gamedump/Hall/device-evidence.')


if __name__ == '__main__': main()
