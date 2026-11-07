"""Validate a source tag and inspect only public, CI-built desktop bundles."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = 'wikigsroom/NsA-core'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def validate_tag(tag):
    if not re.fullmatch(r'v0\.\d+\.\d+', tag):
        raise ValueError('Expected an existing v0.MINOR.PATCH tag')
    version = re.search(r"^__version__ = '([^']+)'", (ROOT / 'porter/__init__.py').read_text('utf-8'), re.M).group(1)
    if tag != 'v' + version:
        raise ValueError('Tag and porter version differ')
    kind = subprocess.check_output(['git', 'cat-file', '-t', 'refs/tags/' + tag], cwd=ROOT, text=True).strip()
    tagged = subprocess.check_output(['git', 'rev-parse', tag + '^{commit}'], cwd=ROOT, text=True).strip()
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if kind != 'tag' or tagged != head:
        raise ValueError('Use the annotated tag checkout, without later source edits')
    if subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=no'], cwd=ROOT, text=True).strip():
        raise ValueError('Tracked source changes exist')
    notes = ROOT / 'docs/releases' / (tag + '.md')
    if not notes.is_file() or len(notes.read_text('utf-8')) < 1500:
        raise ValueError('Complete bilingual release notes are required')
    print(f'Validated {tag} at {head}')


def inspect_bundle(path, tag):
    if not re.fullmatch(r'nx2android-' + re.escape(tag) + r'-(windows-x64|macos-(arm64|x64))\.zip', path.name):
        raise ValueError('Unexpected release filename: ' + path.name)
    with zipfile.ZipFile(path) as source:
        names = set(source.namelist())
        required = {'nx2android/README.md', 'nx2android/README.zh-CN.md', 'nx2android/docs/README.md',
                    'nx2android/docs/README.zh-CN.md', 'nx2android/docs/media/README.md',
                    'nx2android/docs/media/botw-gameplay.png', 'nx2android/docs/media/botw-input.gif',
                    'nx2android/docs/releases/' + tag + '.md', 'nx2android/LICENSE', 'nx2android/VERSION',
                    'nx2android/INSTALL.md', 'nx2android/INSTALL.zh-CN.md', 'nx2android/nxnative/LICENSE'}
        required.add('nx2android/nx2android.exe' if '-windows-' in path.name else 'nx2android/nx2android')
        if not required <= names:
            raise ValueError('Missing documentation/media: ' + str(sorted(required - names)))
        for name in names:
            normalized = name.replace('\\', '/')
            parts = PurePosixPath(normalized).parts
            lower = normalized.lower()
            if not parts or parts[0] != 'nx2android' or '..' in parts or normalized.startswith('/') or '//' in normalized or '\\' in name:
                raise ValueError('Unexpected archive path: ' + name)
            # The tracked engine legitimately has nxnative/dm/*/tools. Only
            # project-root tools and services/local are local private material.
            private_root = len(parts) > 1 and parts[1].lower() in {'tools', 'local'}
            private_service = len(parts) > 2 and tuple(p.lower() for p in parts[1:3]) == ('services', 'local')
            if private_root or private_service or any(p.lower() in {'.gamedump', '.git', '.venv'} for p in parts):
                raise ValueError('Private/build directory in archive: ' + name)
            if lower.endswith(('.nsp', '.nsz', '.xci', '.xcz', '.apk', '.keystore')) or PurePosixPath(lower).name in {
                    'prod.keys', 'title.keys', 'internal-grant.txt', 'development-credentials.json',
                    'signing-key.pem', 'tls-key.pem', 'service-secret.bin', 'libnsa_botw.so', '.env'}:
                raise ValueError('Private content in archive: ' + name)
        if source.read('nx2android/VERSION').decode().strip() != tag:
            raise ValueError('Bundle VERSION must identify the exact clean release tag')
        if source.testzip() is not None:
            raise ValueError('Archive CRC validation failed')
    return {'name': path.name, 'bytes': path.stat().st_size, 'sha256': digest(path)}


def prepare(directory, tag):
    bundles = sorted(directory.glob('*.zip'))
    if len(bundles) != 2 or sum('-windows-' in p.name for p in bundles) != 1 or sum('-macos-' in p.name for p in bundles) != 1:
        raise ValueError('Exactly one CI-built Windows bundle and one macOS bundle are required')
    assets = [inspect_bundle(path, tag) for path in bundles]
    report = {'schema': 1, 'repository': REPOSITORY, 'tag': tag,
              'source_revision': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'scope': 'Public source and desktop authoring tools; no private Android game APK',
              'assets': assets}
    (directory / 'release-manifest.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    checksummed = bundles + [directory / 'release-manifest.json']
    (directory / 'SHA256SUMS.txt').write_text(''.join(digest(p) + '  ' + p.name + '\n' for p in checksummed), encoding='utf-8')
    print(json.dumps(report, indent=2))


def verify_download(directory):
    lines = (directory / 'SHA256SUMS.txt').read_text('utf-8').splitlines()
    seen = set()
    for line in lines:
        checksum, name = line.split('  ', 1)
        if name != Path(name).name or name in seen or not re.fullmatch('[a-f0-9]{64}', checksum):
            raise ValueError('Invalid or duplicate checksum entry')
        seen.add(name)
        if digest(directory / name) != checksum:
            raise ValueError('Uploaded/downloaded bytes differ: ' + name)
    expected = {p.name for p in directory.iterdir() if p.is_file()} - {'SHA256SUMS.txt'}
    if seen != expected:
        raise ValueError('Checksums must cover every downloadable asset')
    report = json.loads((directory / 'release-manifest.json').read_text('utf-8'))
    if report['repository'] != REPOSITORY or len(report['assets']) != 2:
        raise ValueError('Invalid release manifest')
    for entry in report['assets']:
        actual = inspect_bundle(directory / entry['name'], report['tag'])
        if actual != entry:
            raise ValueError('Release manifest differs from uploaded bytes')
    print('Both uploaded bundles and their documentation/media verified.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['validate-tag', 'prepare', 'verify-download'])
    parser.add_argument('tag', nargs='?')
    parser.add_argument('--directory', type=Path)
    args = parser.parse_args()
    if args.action == 'validate-tag': validate_tag(args.tag)
    elif args.action == 'prepare': prepare(args.directory, args.tag)
    else: verify_download(args.directory)


if __name__ == '__main__':
    main()
