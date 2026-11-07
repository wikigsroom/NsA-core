"""Reject stale or mismatched APK contents before installing the hall."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'nxnative/dm/android'))
from slot_identity import verify_compiled_identity
REQUIRED_CLASSES = (
    b'Lorg/nsa/hall/MainActivity;',
    b'Lorg/nsa/hall/data/DeviceCapabilities;',
    b'Lorg/nsa/hall/runtime/GameActivity;',
    b'Lorg/nsa/hall/runtime/GameProcessService;',
    b'Lorg/nsa/hall/transfer/TransferWorker;',
    b'Lorg/nsa/hall/transfer/Installation;',
)


def verify(path: Path):
    with zipfile.ZipFile(path) as apk:
        dex = [apk.read(name) for name in apk.namelist() if name.endswith('.dex')]
        missing = [name.decode() for name in REQUIRED_CLASSES if not any(name in part for part in dex)]
        if missing:
            raise ValueError('APK contains stale or missing classes; run a clean build: ' + ', '.join(missing))
        raw = apk.read('assets/runtime-registry.json')
        generated = ROOT / 'android/app/src/main/assets/runtime-registry.json'
        if raw != generated.read_bytes():
            raise ValueError('APK runtime registry differs from the generated registry')
        registry = json.loads(raw)
        for slot in registry['slots']:
            library = apk.read('lib/arm64-v8a/' + slot['library_name'])
            if hashlib.sha256(library).hexdigest() != slot['library_sha256']:
                raise ValueError('APK runtime library does not match its registered digest')
            verify_compiled_identity(library, slot)
        if 'lib/arm64-v8a/libnsa_bootstrap.so' not in apk.namelist():
            raise ValueError('APK is missing the native activity bootstrap')
    with path.open('rb') as incoming:
        digest = hashlib.file_digest(incoming, 'sha256').hexdigest()
    print(f'APK contents verified: {len(registry["slots"])} runtime slots; SHA-256 {digest}')
    return digest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path, nargs='?', default=ROOT / 'android/app/build/outputs/apk/debug/app-debug.apk')
    verify(parser.parse_args().apk)
